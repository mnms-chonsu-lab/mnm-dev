package com.mnm.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mnm.api.TestTokens;
import com.mnm.api.domain.audit.AuditLog;
import com.mnm.api.domain.delivery.Delivery;
import com.mnm.api.domain.delivery.ReceiverCheck;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.guide.BiosecurityRule;
import com.mnm.api.domain.ops.EtlJobRun;
import com.mnm.api.domain.ops.InferenceLog;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.report.HistoryReport;
import com.mnm.api.domain.report.ReportSummary;
import com.mnm.api.domain.symptom.SymptomReport;
import com.mnm.api.domain.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 어드민 API (ADM-01~09, 11, 12)를 실제 보안·검증·응답 설정 그대로 확인한다.
 * DB 없이 띄우고(nodb) 리포지토리·트랜잭션은 가짜로 바꾼다. 엔티티 변경은 메모리의 객체에 그대로 남는다.
 */
@SpringBootTest
@ActiveProfiles("nodb")
@AutoConfigureMockMvc
class AdminApiTest {

    private static final long ADMIN_ID = 1L;
    private static final String INBOXES = """
            [{"inbox_id":"hs-nh-bio","type":"biosecurity","name":"방역팀","phone":null},
             {"inbox_id":"hs-nh-history","type":"history","name":"이력 담당","phone":null}]""";

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtEncoder jwtEncoder;
    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    AdminUserRepository userRepository;
    @MockitoBean
    AdminFarmRepository farmRepository;
    @MockitoBean
    AdminOrgRepository orgRepository;
    @MockitoBean
    AdminRuleRepository ruleRepository;
    @MockitoBean
    AdminAuditRepository auditRepository;
    @MockitoBean
    AdminMetricsRepository metricsRepository;
    @MockitoBean
    AdminDeliveryRepository deliveryRepository;
    @MockitoBean
    AdminEtlRepository etlRepository;
    @MockitoBean
    PlatformTransactionManager transactionManager;

    private String admin;
    private ReceiverOrg hongseong;

    @BeforeEach
    void setUp() {
        admin = TestTokens.bearer(jwtEncoder, ADMIN_ID, "admin", false, Duration.ofHours(1));
        hongseong = entity(ReceiverOrg.class, Map.of("id", 1L, "name", "홍성축협", "type", "축협",
                "regionCodes", new String[] {"4480000000"}, "inboxes", INBOXES));
        given(orgRepository.findById(1L)).willReturn(Optional.of(hongseong));
        User me = entity(User.class, Map.of("id", ADMIN_ID, "role", "admin", "name", "운영팀", "status", "active"));
        given(userRepository.findById(ADMIN_ID)).willReturn(Optional.of(me));
    }

    // ---- 권한 ----

    @Test
    void 운영자가_아니면_어드민_API를_쓸_수_없다() throws Exception {
        for (String role : List.of("farmer", "receiver")) {
            mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtEncoder, role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH-403"));
        }
        mvc.perform(get("/api/admin/health")).andExpect(status().isUnauthorized());
        // 임시 비밀번호 상태의 운영자도 비밀번호를 바꾸기 전에는 못 쓴다
        String temp = TestTokens.bearer(jwtEncoder, ADMIN_ID, "admin", true, Duration.ofHours(1));
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, temp)).andExpect(status().isForbidden());
    }

    // ---- ADM-02 ~ 04 사용자 ----

    @Test
    void 사용자_목록은_연락처를_가리고_20개씩_다음_커서를_준다() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        for (long id = 30; id >= 10; id--) { // 21개 → 다음 페이지 있음
            User u = entity(User.class, Map.of("id", id, "role", "farmer", "name", "김한우", "phone", "010-0000-0937",
                    "status", "active"));
            rows.add(new Object[] {u, farm(u)});
        }
        given(userRepository.search(isNull(), isNull(), isNull(), eq("%김한우%"), eq(Long.MAX_VALUE), any()))
                .willReturn(rows);

        mvc.perform(get("/api/admin/users").param("q", " 김한우 ").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(20))
                .andExpect(jsonPath("$.data.items[0].phone_masked").value("010-****-0937"))
                .andExpect(jsonPath("$.data.items[0].org_name").value("홍성축협"))
                .andExpect(jsonPath("$.data.items[0].farm_no").value("448012"))
                .andExpect(jsonPath("$.data.has_next").value(true))
                .andExpect(jsonPath("$.data.next_cursor").value("11"));
    }

    @Test
    void 농가_계정을_만들면_농장도_함께_만들고_감사_기록을_남긴다() throws Exception {
        given(userRepository.save(any())).willAnswer(inv -> withId(inv.getArgument(0), 40L));

        createUser("""
                {"role":"farmer","name":"정순자","phone":"010-0000-3390","farm_no":"448027",
                 "address":"충남 홍성군","head_count":30,"org_id":1}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.users_id").value(40))
                .andExpect(jsonPath("$.data.role").value("farmer"))
                .andExpect(jsonPath("$.data.temp_password").value(nullValue()));

        ArgumentCaptor<FarmInfo> farm = ArgumentCaptor.forClass(FarmInfo.class);
        verify(farmRepository).save(farm.capture());
        assertThat(farm.getValue().getFarmNo()).isEqualTo("448027");
        assertThat(farm.getValue().getName()).isEqualTo("정순자 농장");
        assertThat(farm.getValue().getOrg()).isSameAs(hongseong);
        assertThat(farm.getValue().getOwner().getPasswordHash()).isNull();
        assertThat(auditOf(0).getAction()).isEqualTo("create");
        assertThat(auditOf(0).getTargetId()).isEqualTo(40L);
    }

    @Test
    void 농장식별번호_휴대폰_아이디가_겹치면_CONFLICT_빠진_칸은_VALID_422() throws Exception {
        given(farmRepository.existsByFarmNo("448012")).willReturn(true);
        createUser("{\"role\":\"farmer\",\"name\":\"김한우\",\"phone\":\"01000000937\",\"farm_no\":\"448012\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 등록된 농장식별번호예요."));

        given(userRepository.existsFarmerPhone("01000000937", -1L)).willReturn(true);
        createUser("{\"role\":\"farmer\",\"name\":\"김한우\",\"phone\":\"010-0000-0937\",\"farm_no\":\"448013\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 등록된 휴대폰 번호예요."));

        given(userRepository.existsByLoginId("hs-bio")).willReturn(true);
        createUser("{\"role\":\"receiver\",\"name\":\"박서연\",\"login_id\":\"hs-bio\",\"org_id\":1}")
                .andExpect(status().isConflict());

        createUser("{\"role\":\"farmer\",\"name\":\"김한우\",\"phone\":\"01000000937\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("농장식별번호 6자리를 넣어 주세요."));
        createUser("{\"role\":\"farmer\",\"name\":\"김한우\",\"phone\":\"041-000-0000\",\"farm_no\":\"448014\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("휴대폰 번호를 다시 확인해 주세요."));
        createUser("{\"role\":\"boss\",\"name\":\"\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItems("role", "name")));
        verify(farmRepository, never()).save(any());
    }

    @Test
    void 접수자_계정은_임시_비밀번호를_한_번_보여주고_첫_로그인_때_바꾸게_한다() throws Exception {
        given(userRepository.save(any())).willAnswer(inv -> withId(inv.getArgument(0), 50L));

        String body = createUser("""
                {"role":"receiver","name":"박서연","login_id":"seoyeon.park","org_id":1,"inbox_id":"hs-nh-bio"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.login_id").value("seoyeon.park"))
                .andExpect(jsonPath("$.data.temp_password").value(matchesPattern("[A-Za-z0-9]{4}-[A-Za-z0-9]{4}")))
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.isMustChangePw()).isTrue();
        assertThat(user.getInboxId()).isEqualTo("hs-nh-bio");
        assertThat(passwordEncoder.matches(JsonPath.read(body, "$.data.temp_password"), user.getPasswordHash())).isTrue();

        createUser("{\"role\":\"receiver\",\"name\":\"박서연\",\"login_id\":\"a.b\",\"org_id\":1,\"inbox_id\":\"nope\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("수신함을 다시 골라 주세요."));
        createUser("{\"role\":\"receiver\",\"name\":\"박서연\",\"login_id\":\"a.b\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("소속 기관을 골라 주세요."));
        createUser("{\"role\":\"receiver\",\"name\":\"박서연\",\"login_id\":\"a.b\",\"org_id\":99}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("소속 기관을 다시 골라 주세요."));
    }

    @Test
    void 비밀번호_초기화는_접수자_잠금을_풀고_새_임시_비밀번호를_준다_농가와_내_계정_정지는_막는다() throws Exception {
        User receiver = entity(User.class, new HashMap<>(Map.of("id", 20L, "role", "receiver", "name", "박서연",
                "loginId", "hs-bio", "passwordHash", "old", "status", "active", "org", hongseong,
                "failedCount", 3, "lockedUntil", Instant.now().plusSeconds(300))));
        given(userRepository.findById(20L)).willReturn(Optional.of(receiver));

        updateUser(20L, "{\"reset_password\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temp_password").isNotEmpty())
                .andExpect(jsonPath("$.data.user.must_change_pw").value(true))
                .andExpect(jsonPath("$.data.user.locked").value(false));
        assertThat(receiver.getFailedCount()).isZero();
        assertThat(auditOf(0).getAction()).isEqualTo("password_reset");

        // 다른 기관으로 옮기면 예전 수신함은 비운다
        ReceiverOrg gunchung = entity(ReceiverOrg.class, Map.of("id", 2L, "name", "홍성군청 축산과", "type", "시군청",
                "regionCodes", new String[] {"4480000000"}, "inboxes", "[]"));
        given(orgRepository.findById(2L)).willReturn(Optional.of(gunchung));
        receiver.assignOrg(hongseong, "hs-nh-bio");
        updateUser(20L, "{\"org_id\":2,\"status\":\"suspended\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.org_name").value("홍성군청 축산과"))
                .andExpect(jsonPath("$.data.user.inbox_id").value(nullValue()))
                .andExpect(jsonPath("$.data.user.status").value("suspended"))
                .andExpect(jsonPath("$.data.temp_password").value(nullValue()));

        User farmer = entity(User.class, Map.of("id", 10L, "role", "farmer", "name", "김한우", "status", "active"));
        given(userRepository.findById(10L)).willReturn(Optional.of(farmer));
        updateUser(10L, "{\"reset_password\":true}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(startsWith("농가 계정은 비밀번호가 없어요.")));

        updateUser(ADMIN_ID, "{\"status\":\"suspended\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("내 계정은 정지할 수 없어요."));
        updateUser(99L, "{\"status\":\"active\"}").andExpect(status().isNotFound());
    }

    @Test
    void 농가_상세는_농장_현황을_건수로만_보여준다() throws Exception {
        User farmer = entity(User.class, Map.of("id", 10L, "role", "farmer", "name", "김한우",
                "phone", "01000000937", "status", "active"));
        given(userRepository.findById(10L)).willReturn(Optional.of(farmer));
        given(farmRepository.findFirstByOwnerId(10L)).willReturn(Optional.of(farm(farmer)));
        given(metricsRepository.countActiveCows(1L)).willReturn(3L);
        given(metricsRepository.countDiary(eq(1L), any())).willReturn(10L);
        given(metricsRepository.countSymptomReports(eq(1L), any())).willReturn(2L);
        given(metricsRepository.countFarmDeliveries(1L)).willReturn(1L);

        mvc.perform(get("/api/admin/users/10").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone_masked").value("010-****-0937"))
                .andExpect(jsonPath("$.data.farm.cow_count").value(3))
                .andExpect(jsonPath("$.data.farm.records30d").value(12))
                .andExpect(jsonPath("$.data.farm.delivered_count").value(1))
                .andExpect(jsonPath("$.data.farm.org_name").value("홍성축협"));
    }

    // ---- ADM-05 접수 기관 ----

    @Test
    void 접수자가_있는_수신함은_뺄_수_없고_새_수신함은_id를_받는다() throws Exception {
        given(userRepository.countReceiversByInbox()).willReturn(List.<Object[]>of(new Object[] {1L, "hs-nh-bio", 2L}));

        updateOrg("""
                {"name":"홍성축협","type":"축협","region_codes":["4480000000"],
                 "inboxes":[{"inbox_id":"hs-nh-history","type":"history","name":"이력 담당"}]}""")
                .andExpect(status().isConflict());

        updateOrg("""
                {"name":"홍성축협","type":"축협","region_codes":["4480000000","4480000000"],
                 "inboxes":[{"inbox_id":"hs-nh-bio","type":"biosecurity","name":"방역팀"},
                            {"type":"biosecurity","name":"방역팀 2"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.region_codes.length()").value(1))
                .andExpect(jsonPath("$.data.inboxes[0].receiver_count").value(2))
                .andExpect(jsonPath("$.data.inboxes[1].inbox_id").value(startsWith("inbox-")))
                .andExpect(jsonPath("$.data.inboxes[1].receiver_count").value(0))
                .andExpect(jsonPath("$.data.receiver_count").value(2));
        assertThat(auditOf(0).getTargetType()).isEqualTo("receiver_org");

        updateOrg("""
                {"name":"홍성축협","type":"축협","region_codes":["4480000000"],
                 "inboxes":[{"inbox_id":"made-up","type":"history","name":"이력"}]}""")
                .andExpect(status().isUnprocessableContent());
        updateOrg("{\"name\":\"홍성축협\",\"type\":\"학교\",\"region_codes\":[\"448\"],\"inboxes\":[]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItems("type")));
    }

    // ---- ADM-07 · 08 안내 문구 ----

    @Test
    void 안내_문구는_검수를_거쳐_게시하고_게시된_문구를_고치면_새_버전이_생기고_게시하면_이전_버전을_내린다() throws Exception {
        BiosecurityRule v1 = entity(BiosecurityRule.class, Map.of("id", 3L, "kind", "question", "disease", "fmd",
                "stage", "suspect", "text", "사료를 안 먹거나 남기나요?", "sourceDoc", "구제역 SOP", "status", "published",
                "displayOrder", 3, "version", 1));
        given(ruleRepository.findById(3L)).willReturn(Optional.of(v1));
        Map<Long, BiosecurityRule> saved = new HashMap<>();
        given(ruleRepository.save(any())).willAnswer(inv -> {
            BiosecurityRule r = withId(inv.getArgument(0), 4L);
            saved.put(4L, r);
            given(ruleRepository.findById(4L)).willReturn(Optional.of(r));
            return r;
        });
        String content = """
                {"content":{"disease":"fmd","stage":"suspect","text":"사료를 남기나요?","source_doc":"구제역 SOP",
                 "display_order":3,"yes_required_parts":["body_side_skin"]}}""";

        updateGuide(3L, content)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.biosecurity_rule_id").value(4))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.yes_required_parts[0]").value("body_side_skin"));
        assertThat(v1.getStatus()).isEqualTo("published");
        assertThat(v1.getText()).isEqualTo("사료를 안 먹거나 남기나요?");

        updateGuide(4L, "{\"status\":\"published\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("검수를 거친 문구만 게시할 수 있어요."));

        updateGuide(4L, "{\"status\":\"review\"}").andExpect(jsonPath("$.data.status").value("review"));
        given(ruleRepository.findByKindAndStatus("question", "published")).willReturn(List.of(v1));
        updateGuide(4L, "{\"status\":\"published\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"))
                .andExpect(jsonPath("$.data.reviewer").value("운영팀"));
        assertThat(v1.getStatus()).isEqualTo("draft");
        assertThat(saved.get(4L).getStatus()).isEqualTo("published");
    }

    @Test
    void 안내_문구_등록은_초안으로_만들고_원문_문서명이_없으면_VALID_422() throws Exception {
        given(ruleRepository.save(any())).willAnswer(inv -> withId(inv.getArgument(0), 7L));
        mvc.perform(post("/api/admin/guides").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"action\",\"content\":{\"text\":\"이동을 멈춰 주세요.\",\"source_doc\":\"구제역 SOP\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(post("/api/admin/guides").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"action\",\"content\":{\"text\":\"이동을 멈춰 주세요.\"}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("content.source_doc"));
    }

    // ---- ADM-01 대시보드 ----

    @Test
    void DB가_안_되면_DB는_오류_나머지는_확인_안_됨으로_보인다() throws Exception {
        given(metricsRepository.ping()).willThrow(new IllegalStateException("connection refused"));
        mvc.perform(get("/api/admin/health").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components[0].status").value("normal"))
                .andExpect(jsonPath("$.data.components[1].key").value("db"))
                .andExpect(jsonPath("$.data.components[1].status").value("error"))
                .andExpect(jsonPath("$.data.components[2].status").value("unknown"));
    }

    @Test
    void AI_오류율이_기준을_넘거나_수집이_실패하면_오류로_보인다() throws Exception {
        given(metricsRepository.ping()).willReturn(1);
        given(metricsRepository.inferenceStats(any())).willReturn(List.<Object[]>of(new Object[] {10L, 1L, 800.0}));
        EtlJobRun failed = entity(EtlJobRun.class, Map.of("id", 1L, "job", "disinfection_facility",
                "status", "failed", "startedAt", Instant.parse("2026-11-14T21:00:00Z")));
        EtlJobRun ok = entity(EtlJobRun.class, Map.of("id", 2L, "job", "outbreak", "status", "success",
                "startedAt", Instant.parse("2026-11-14T21:00:00Z"), "finishedAt", Instant.parse("2026-11-14T21:01:00Z")));
        given(metricsRepository.latestEtlRuns()).willReturn(List.of(failed, ok));

        mvc.perform(get("/api/admin/health").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.data.components[2].key").value("ai"))
                .andExpect(jsonPath("$.data.components[2].status").value("error"))
                .andExpect(jsonPath("$.data.components[2].detail").value("800ms"))
                .andExpect(jsonPath("$.data.components[3].status").value("error"))
                .andExpect(jsonPath("$.data.components[3].detail").value("disinfection_facility"))
                .andExpect(jsonPath("$.data.components[3].last_at").value("2026-11-14T21:01:00Z"));
    }

    @Test
    void 오늘_수치는_한국_시간_0시부터_세고_AI_호출이_없으면_오류율은_null() throws Exception {
        given(metricsRepository.countDiary(isNull(), any())).willReturn(5L);
        given(metricsRepository.countSymptomReports(isNull(), any())).willReturn(2L);
        given(metricsRepository.countDeliveriesSince(any())).willReturn(3L);
        given(metricsRepository.countPendingReceipts()).willReturn(1L);
        given(metricsRepository.inferenceStats(any())).willReturn(List.<Object[]>of(new Object[] {0L, 0L, null}));

        String body = mvc.perform(get("/api/admin/metrics/today").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").value(7))
                .andExpect(jsonPath("$.data.deliveries").value(3))
                .andExpect(jsonPath("$.data.pending_receipts").value(1))
                .andExpect(jsonPath("$.data.ai_error_rate").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        // 한국 0시 = 전날 15:00 UTC
        assertThat((String) JsonPath.read(body, "$.data.since")).endsWith("T15:00:00Z");
    }

    // ---- ADM-06 전달 현황 ----

    @Test
    void 전달_현황은_종류와_접수자_처리_상태를_보여주고_처리_기록이_없으면_새로_도착() throws Exception {
        User owner = entity(User.class, Map.of("id", 2L, "role", "farmer", "name", "김한우"));
        FarmInfo farm = farm(owner);
        SymptomReport sr = entity(SymptomReport.class, Map.of("id", 3L, "farm", farm));
        ReportSummary rs = entity(ReportSummary.class, Map.of("id", 4L, "symptomReport", sr));
        Delivery call = entity(Delivery.class, Map.of("id", 11L, "reportSummary", rs, "route", "call",
                "ccOrg", hongseong, "status", "delivered", "approvedAt", Instant.parse("2026-11-14T00:42:00Z")));
        HistoryReport hr = entity(HistoryReport.class, Map.of("id", 5L, "farm", farm, "type", "birth"));
        Delivery coop = entity(Delivery.class, Map.of("id", 10L, "historyReport", hr, "route", "coop",
                "org", hongseong, "status", "delivered", "approvedAt", Instant.parse("2026-11-13T11:10:00Z")));
        given(deliveryRepository.search(isNull(), isNull(), isNull(), eq(Instant.EPOCH),
                eq(Instant.parse("9999-12-31T00:00:00Z")), eq(Long.MAX_VALUE), any()))
                .willReturn(List.of(call, coop));
        ReceiverCheck done = entity(ReceiverCheck.class, Map.of("id", 1L, "delivery", coop, "status", "done"));
        given(deliveryRepository.latestChecks(List.of(11L, 10L))).willReturn(List.of(done));

        mvc.perform(get("/api/admin/deliveries").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].kind").value("symptom"))
                .andExpect(jsonPath("$.data.items[0].farmer_name").value("김한우"))
                .andExpect(jsonPath("$.data.items[0].org_name").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].cc_org_name").value("홍성축협"))
                .andExpect(jsonPath("$.data.items[0].receiver_status").value("new"))
                .andExpect(jsonPath("$.data.items[1].kind").value("birth"))
                .andExpect(jsonPath("$.data.items[1].receiver_status").value("done"))
                .andExpect(jsonPath("$.data.has_next").value(false));
    }

    @Test
    void 다시_보내기는_실패_건만_도착으로_바꾸고_감사_기록을_남긴다() throws Exception {
        User owner = entity(User.class, Map.of("id", 2L, "role", "farmer", "name", "최민호"));
        HistoryReport hr = entity(HistoryReport.class, Map.of("id", 5L, "farm", farm(owner), "type", "death"));
        Delivery failed = entity(Delivery.class, Map.of("id", 12L, "historyReport", hr, "route", "coop",
                "org", hongseong, "status", "failed"));
        given(deliveryRepository.findById(12L)).willReturn(Optional.of(failed));

        mvc.perform(post("/api/admin/deliveries/12/retry").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("delivered"))
                .andExpect(jsonPath("$.data.receiver_status").value("new"));
        assertThat(auditOf(0).getAction()).isEqualTo("retry");
        assertThat(auditOf(0).getTargetId()).isEqualTo(12L);

        // 이미 도착한 건은 다시 보내지 않는다
        mvc.perform(post("/api/admin/deliveries/12/retry").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT-409"));
        mvc.perform(post("/api/admin/deliveries/99/retry").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    // ---- ADM-09 공공데이터 수집 ----

    @Test
    void 수집_현황은_작업_3개를_고정_순서로_마지막_실행과_마지막_성공을_함께_보여준다() throws Exception {
        EtlJobRun failed = entity(EtlJobRun.class, Map.of("id", 3L, "job", "disinfection_facility", "status", "failed",
                "startedAt", Instant.parse("2026-11-13T21:00:00Z"), "error", "API 응답 시간 초과"));
        EtlJobRun ok = entity(EtlJobRun.class, Map.of("id", 2L, "job", "disinfection_facility", "status", "success",
                "startedAt", Instant.parse("2026-11-12T21:00:00Z"), "rows", 214));
        given(metricsRepository.latestEtlRuns()).willReturn(List.of(failed));
        given(etlRepository.lastSuccesses()).willReturn(List.of(ok));
        given(etlRepository.busyJobs()).willReturn(List.of("outbreak"));

        mvc.perform(get("/api/admin/etl/jobs").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].job").value("outbreak"))
                .andExpect(jsonPath("$.data[0].busy").value(true))
                .andExpect(jsonPath("$.data[0].last_run").value(nullValue()))
                .andExpect(jsonPath("$.data[1].schedule").value("monthly"))
                .andExpect(jsonPath("$.data[2].last_run.status").value("failed"))
                .andExpect(jsonPath("$.data[2].last_run.error").value("API 응답 시간 초과"))
                .andExpect(jsonPath("$.data[2].last_success.rows").value(214))
                .andExpect(jsonPath("$.data[2].busy").value(false));
    }

    @Test
    void 지금_수집은_요청_기록만_남기고_실행_중이면_CONFLICT_모르는_작업은_404() throws Exception {
        given(etlRepository.save(any())).willAnswer(inv -> withId(inv.getArgument(0), 7L));

        mvc.perform(post("/api/admin/etl/jobs/outbreak/run").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.etl_job_run_id").value(7))
                .andExpect(jsonPath("$.data.status").value("requested"))
                .andExpect(jsonPath("$.data.job").value("outbreak"));
        assertThat(auditOf(0).getAction()).isEqualTo("run");

        given(etlRepository.existsByJobAndStatusIn(eq("farm_scale"), any())).willReturn(true);
        mvc.perform(post("/api/admin/etl/jobs/farm_scale/run").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/etl/jobs/weather/run").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        verify(etlRepository).save(any());
    }

    // ---- ADM-11 AI 지표·로그 ----

    @Test
    void AI_지표는_기능별_값을_합치고_평소와_다름_문제_없음_비율을_준다() throws Exception {
        // [feature, 호출, 실패(503·504), 평균 지연, 404, 422]
        given(metricsRepository.inferenceStatsByFeature(any())).willReturn(List.of(
                new Object[] {"guide", 10L, 0L, 1000.0, 3L, 0L},
                new Object[] {"stt", 30L, 2L, 2000.0, 0L, 1L}));
        given(metricsRepository.diffFeedback()).willReturn(List.<Object[]>of(new Object[] {3L, 1L}));

        mvc.perform(get("/api/admin/ai-metrics").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total.calls").value(40))
                .andExpect(jsonPath("$.data.total.failures").value(2))
                .andExpect(jsonPath("$.data.total.error_rate").value(0.05))
                .andExpect(jsonPath("$.data.total.avg_latency_ms").value(1750))
                .andExpect(jsonPath("$.data.total.no_evidence").value(3))
                .andExpect(jsonPath("$.data.features[0].feature").value("guide"))
                .andExpect(jsonPath("$.data.features[0].error_rate").value(0.0))
                .andExpect(jsonPath("$.data.diff_ok_rate").value(0.25))
                .andExpect(jsonPath("$.data.diff_feedback_count").value(4));
    }

    @Test
    void AI_호출이_없으면_오류율과_평균_지연은_null() throws Exception {
        given(metricsRepository.inferenceStatsByFeature(any())).willReturn(List.of());
        given(metricsRepository.diffFeedback()).willReturn(List.<Object[]>of(new Object[] {null, null}));

        mvc.perform(get("/api/admin/ai-metrics").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.data.total.calls").value(0))
                .andExpect(jsonPath("$.data.total.error_rate").value(nullValue()))
                .andExpect(jsonPath("$.data.total.avg_latency_ms").value(nullValue()))
                .andExpect(jsonPath("$.data.diff_ok_rate").value(nullValue()));
    }

    @Test
    void AI_호출_로그는_기능으로_거르고_응답_상태를_보여준다() throws Exception {
        InferenceLog log = entity(InferenceLog.class, Map.of("id", 9L, "feature", "part_check",
                "modelVersion", "siglip2-v1", "resultSummary", "hoof→hoof 0.91", "latencyMs", 800,
                "httpStatus", (short) 200, "at", Instant.parse("2026-11-14T00:58:10Z")));
        given(metricsRepository.searchInferenceLogs(eq("part_check"), eq(Instant.EPOCH),
                eq(Instant.parse("9999-12-31T00:00:00Z")), eq(Long.MAX_VALUE), any()))
                .willReturn(List.of(log));

        mvc.perform(get("/api/admin/inference-logs").param("feature", "part_check")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].model_version").value("siglip2-v1"))
                .andExpect(jsonPath("$.data.items[0].http_status").value(200))
                .andExpect(jsonPath("$.data.items[0].error").value(nullValue()));
    }

    // ---- ADM-12 감사 로그 ----

    @Test
    void 감사_로그는_기간과_사용자로_거르고_누가_무엇을_했는지_보여준다() throws Exception {
        User receiver = entity(User.class, Map.of("id", 20L, "role", "receiver", "name", "박서연"));
        AuditLog log = entity(AuditLog.class, Map.of("id", 5L, "user", receiver, "action", "view",
                "targetType", "deliveries", "targetId", 1L, "at", Instant.parse("2026-11-14T00:50:00Z")));
        Instant from = Instant.parse("2026-11-11T00:00:00Z");
        given(auditRepository.search(eq(20L), isNull(), isNull(), eq(from), eq(Instant.parse("9999-12-31T00:00:00Z")),
                eq(Long.MAX_VALUE), any()))
                .willReturn(List.of(log));

        mvc.perform(get("/api/admin/audit-logs").param("users_id", "20").param("from", "2026-11-11T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].user_name").value("박서연"))
                .andExpect(jsonPath("$.data.items[0].action").value("view"))
                .andExpect(jsonPath("$.data.has_next").value(false));

        mvc.perform(get("/api/admin/audit-logs").param("cursor", "abc").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void 임시_비밀번호는_영문과_숫자를_섞은_4_4자리() {
        for (int i = 0; i < 200; i++) {
            assertThat(AdminUserService.tempPassword())
                    .matches("[A-Za-z2-9]{4}-[A-Za-z2-9]{4}")
                    .containsPattern("[A-Za-z]")
                    .containsPattern("\\d");
        }
    }

    // ---- helpers ----

    private ResultActions createUser(String json) throws Exception {
        return mvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions updateUser(long id, String json) throws Exception {
        return mvc.perform(patch("/api/admin/users/" + id).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions updateOrg(String json) throws Exception {
        return mvc.perform(patch("/api/admin/orgs/1").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions updateGuide(long id, String json) throws Exception {
        return mvc.perform(patch("/api/admin/guides/" + id).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private AuditLog auditOf(int index) {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().get(index);
    }

    private FarmInfo farm(User owner) {
        return entity(FarmInfo.class, Map.of("id", 1L, "farmNo", "448012", "owner", owner, "name", "김한우 농장",
                "headCount", 42, "org", hongseong));
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    /** 엔티티는 생성자·setter 를 열지 않으므로 테스트에서만 필드를 직접 채운다 */
    private static <T> T entity(Class<T> type, Map<String, Object> fields) {
        T entity = BeanUtils.instantiateClass(type);
        fields.forEach((name, value) -> ReflectionTestUtils.setField(entity, name, value));
        return entity;
    }
}
