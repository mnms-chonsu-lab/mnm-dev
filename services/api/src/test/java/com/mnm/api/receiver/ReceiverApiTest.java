package com.mnm.api.receiver;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mnm.api.TestTokens;
import com.mnm.api.domain.alert.Alert;
import com.mnm.api.domain.audit.AuditLog;
import com.mnm.api.domain.delivery.Delivery;
import com.mnm.api.domain.delivery.ReceiverCheck;
import com.mnm.api.domain.farm.Cow;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.guide.BiosecurityRule;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.report.HistoryReport;
import com.mnm.api.domain.report.ReportSummary;
import com.mnm.api.domain.symptom.SymptomReport;
import com.mnm.api.domain.task.Task;
import com.mnm.api.domain.user.User;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 접수자 API 를 실제 보안·검증·오류 처리 설정 그대로 확인한다. DB 없이(nodb) 리포지토리·트랜잭션은 가짜로 바꾼다.
 * 엔티티 변경은 메모리의 객체에 그대로 남는다.
 */
@SpringBootTest
@ActiveProfiles("nodb")
@AutoConfigureMockMvc
class ReceiverApiTest {

    private static final long RECEIVER_ID = 20L;

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtEncoder jwtEncoder;

    @MockitoBean
    PlatformTransactionManager transactionManager;
    @MockitoBean
    ReceiverUserRepository userRepository;
    @MockitoBean
    ReceiverDeliveryRepository deliveryRepository;
    @MockitoBean
    ReceiverCheckRepository checkRepository;
    @MockitoBean
    ReceiverAlertRepository alertRepository;
    @MockitoBean
    ReceiverAuditRepository auditRepository;
    @MockitoBean
    ReceiverFarmRepository farmRepository;
    @MockitoBean
    ReceiverDiaryRepository diaryRepository;
    @MockitoBean
    ReceiverTaskRepository taskRepository;
    @MockitoBean
    ReceiverRuleRepository ruleRepository;
    @MockitoBean
    ReceiverOutbreakRepository outbreakRepository;
    @MockitoBean
    ReceiverCowRepository cowRepository;
    @MockitoBean
    ReceiverMediaRepository mediaRepository;

    private String token;
    private ReceiverOrg coop;
    private ReceiverOrg otherOrg;
    private User receiver;
    private User owner;
    private FarmInfo farm;
    private Cow cow;

    @BeforeEach
    void setUp() {
        token = TestTokens.bearer(jwtEncoder, RECEIVER_ID, "receiver", false, Duration.ofHours(1));
        coop = entity(ReceiverOrg.class, Map.of("id", 1L, "name", "홍성축협", "type", "축협",
                "regionCodes", new String[] {"4480000000"}));
        otherOrg = entity(ReceiverOrg.class, Map.of("id", 2L, "name", "예산축협", "type", "축협",
                "regionCodes", new String[] {"4481000000"}));
        receiver = entity(User.class, Map.of("id", RECEIVER_ID, "role", "receiver", "name", "박서연", "org", coop));
        owner = entity(User.class, Map.of("id", 10L, "role", "farmer", "name", "김한우", "phone", "01024810937"));
        farm = entity(FarmInfo.class, Map.of("id", 1L, "farmNo", "448012", "owner", owner, "name", "김한우 농장",
                "address", "충남 홍성군 홍북읍", "regionCode", "4480025000", "headCount", 42, "org", coop));
        cow = entity(Cow.class, Map.of("id", 5L, "farm", farm, "tagLast4", "1234", "tagFull", "002123451234",
                "breed", "한우", "sex", "암", "status", "active"));
        given(userRepository.findWithOrgById(RECEIVER_ID)).willReturn(Optional.of(receiver));
        given(checkRepository.save(any())).willAnswer(i -> withField(i.getArgument(0), "at", Instant.now()));
        given(alertRepository.save(any())).willAnswer(i -> withField(i.getArgument(0), "id", 70L));
    }

    @Test
    void 농가_토큰으로는_접수자_API를_쓸_수_없다() throws Exception {
        mvc.perform(get("/api/receiver/inbox").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtEncoder, "farmer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-403"));
    }

    @Test
    void 접수_목록은_상태별_개수와_기록_카드를_준다() throws Exception {
        Delivery symptom = symptomDelivery(40L, true, "{\"drool\":\"yes\"}");
        Delivery history = historyDelivery(41L, "출생", "{\"mother_tag4\":\"1234\"}", "[\"birth_date\"]");
        given(deliveryRepository.findInbox(eq(1L), eq("all"), eq(Long.MAX_VALUE), any()))
                .willReturn(List.of(history, symptom));
        given(checkRepository.findLatest(anyCollection())).willReturn(List.of(check(history, "checking", null)));
        given(deliveryRepository.countInbox(1L)).willReturn(2L);
        given(checkRepository.countLatestByStatus(1L)).willReturn(List.<Object[]>of(new Object[] {"checking", 1L}));

        receiver(get("/api/receiver/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.all").value(2))
                .andExpect(jsonPath("$.data.counts.new").value(1))
                .andExpect(jsonPath("$.data.counts.checking").value(1))
                .andExpect(jsonPath("$.data.page.items[0].kind").value("history"))
                .andExpect(jsonPath("$.data.page.items[0].status").value("checking"))
                .andExpect(jsonPath("$.data.page.items[0].history_type").value("출생"))
                .andExpect(jsonPath("$.data.page.items[1].status").value("new"))
                .andExpect(jsonPath("$.data.page.items[1].cow_tag4").value("1234"))
                .andExpect(jsonPath("$.data.outbreak.fmd").value(0));
    }

    @Test
    void 목록_상태_값이_틀리면_VALID_422() throws Exception {
        receiver(get("/api/receiver/inbox").param("status", "deleted"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALID-422"));
    }

    @Test
    void 다른_기관에_간_기록은_관할_밖_AUTH_403() throws Exception {
        Delivery d = symptomDelivery(40L, true, "{}");
        ReflectionTestUtils.setField(d, "org", otherOrg);
        given(deliveryRepository.findById(40L)).willReturn(Optional.of(d));

        receiver(get("/api/receiver/inbox/40"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-403"));
        verify(auditRepository, never()).save(any());
    }

    @Test
    void 한_장_요약은_열람_감사_기록을_남기고_연락처는_공개했을_때만_준다() throws Exception {
        Delivery d = symptomDelivery(40L, false, "{\"drool\":\"yes\",\"feed\":\"yes\",\"lump\":\"no\"}");
        given(deliveryRepository.findById(40L)).willReturn(Optional.of(d));
        BiosecurityRule fmd = entity(BiosecurityRule.class, Map.of("id", 1L, "kind", "summary", "disease", "구제역",
                "sourceDoc", "구제역 긴급행동지침(SOP)", "text", "의심축 발견 시 즉시 신고", "status", "published"));
        BiosecurityRule lsd = entity(BiosecurityRule.class, Map.of("id", 2L, "kind", "summary", "disease", "럼피스킨",
                "sourceDoc", "럼피스킨 긴급행동지침(SOP)", "text", "x", "status", "published"));
        given(ruleRepository.findByKindAndStatusOrderByDisplayOrderAscIdAsc("summary", "published"))
                .willReturn(List.of(fmd, lsd));

        receiver(get("/api/receiver/inbox/40"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("new"))
                .andExpect(jsonPath("$.data.farm.phone_shared").value(false))
                .andExpect(jsonPath("$.data.farm.phone").value(nullValue()))
                .andExpect(jsonPath("$.data.symptom.matched_symptoms[0]").value("침 흘림"))
                .andExpect(jsonPath("$.data.symptom.matched_symptoms[1]").value("사료 남김"))
                .andExpect(jsonPath("$.data.guides.length()").value(1))
                .andExpect(jsonPath("$.data.guides[0].source_doc").value("구제역 긴급행동지침(SOP)"));
        verify(auditRepository).save(argThat((AuditLog a) -> "view".equals(a.getAction()) && a.getTargetId() == 40L));
    }

    @Test
    void 보던_상태와_지금_상태가_다르면_CONFLICT_409() throws Exception {
        Delivery d = symptomDelivery(40L, true, "{}");
        given(deliveryRepository.findForUpdate(40L)).willReturn(Optional.of(d));
        given(checkRepository.findFirstByDeliveryIdOrderByIdDesc(40L)).willReturn(Optional.of(check(d, "done", "vet_guide")));

        receiver(patch("/api/receiver/inbox/40/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"checking\",\"expected_status\":\"new\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT-409"));
        verify(checkRepository, never()).save(any());
    }

    @Test
    void 확인_시작하면_처리_기록과_농가_알림이_생긴다() throws Exception {
        Delivery d = symptomDelivery(40L, true, "{}");
        given(deliveryRepository.findForUpdate(40L)).willReturn(Optional.of(d));

        receiver(patch("/api/receiver/inbox/40/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"checking\",\"expected_status\":\"new\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("checking"));
        verify(checkRepository).save(argThat((ReceiverCheck c) -> "checking".equals(c.getStatus())
                && c.getUser() == receiver && c.getOrg() == coop));
        verify(alertRepository).save(argThat((Alert a) -> a.getUser() == owner && "result".equals(a.getKind())
                && a.getTitle().equals("홍성축협에서 보낸 기록을 확인하고 있어요") && a.getRefId() == 40L));
        verify(auditRepository).save(argThat((AuditLog a) -> "update".equals(a.getAction())));
    }

    @Test
    void 처리_완료는_결과가_필요하고_결과를_농가_알림에_담는다() throws Exception {
        Delivery d = symptomDelivery(40L, true, "{}");
        given(deliveryRepository.findForUpdate(40L)).willReturn(Optional.of(d));
        given(checkRepository.findFirstByDeliveryIdOrderByIdDesc(40L)).willReturn(Optional.of(check(d, "checking", null)));

        receiver(patch("/api/receiver/inbox/40/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"done\",\"expected_status\":\"checking\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("처리 결과를 골라 주세요."));
        receiver(patch("/api/receiver/inbox/40/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"done\",\"expected_status\":\"checking\",\"result\":\"diagnosed\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("result"));

        receiver(patch("/api/receiver/inbox/40/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"done\",\"expected_status\":\"checking\",\"result\":\"vet_guide\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("vet_guide"));
        verify(alertRepository).save(argThat((Alert a) -> "수의사 진료 안내".equals(a.getBody())
                && a.getTitle().equals("홍성축협에서 처리 결과를 남겼어요")));
    }

    @Test
    void 이력_신고_초안은_상태_변경으로_완료하지_않는다() throws Exception {
        Delivery d = historyDelivery(41L, "출생", "{}", "[]");
        given(deliveryRepository.findForUpdate(41L)).willReturn(Optional.of(d));
        given(checkRepository.findFirstByDeliveryIdOrderByIdDesc(41L)).willReturn(Optional.of(check(d, "checking", null)));

        receiver(patch("/api/receiver/inbox/41/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"done\",\"expected_status\":\"checking\",\"result\":\"vet_guide\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void 새로_도착한_기록에_메모를_남기면_확인_중으로_바뀐다() throws Exception {
        Delivery d = historyDelivery(41L, "출생", "{}", "[]");
        given(deliveryRepository.findForUpdate(41L)).willReturn(Optional.of(d));

        receiver(post("/api/receiver/inbox/41/memos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\":\"통화함. 오후 2시 방문 예정\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("checking"));
        verify(checkRepository).save(argThat((ReceiverCheck c) -> "통화함. 오후 2시 방문 예정".equals(c.getMemo())));
        org.assertj.core.api.Assertions.assertThat(d.getHistoryReport().getStatus()).isEqualTo("checking");
        receiver(post("/api/receiver/inbox/41/memos").contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\" \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("메모를 적어 주세요."));
    }

    @Test
    void 요약이_없어도_신고서_6개_항목을_기록값으로_채운다() throws Exception {
        Delivery d = symptomDelivery(40L, true, "{\"drool\":\"yes\",\"blister\":\"no\"}");
        given(deliveryRepository.findById(40L)).willReturn(Optional.of(d));

        receiver(get("/api/receiver/inbox/40/form6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[0].value").value("김한우 · 충남 홍성군 홍북읍 · 2번 축사"))
                .andExpect(jsonPath("$.data.items[1].value").value("한우 1두 (1234번) / 전체 42두"))
                .andExpect(jsonPath("$.data.items[2].value").value("발견 당시 상태: 침 흘림"))
                .andExpect(jsonPath("$.data.text", containsString("신고자 성명 및 주소: 김한우 · 충남 홍성군 홍북읍")));
    }

    @Test
    void 대신_신고_마침은_빈칸이_남으면_VALID_422() throws Exception {
        Delivery d = historyDelivery(41L, "출생", "{\"mother_tag4\":\"1234\",\"sex\":\"암\",\"breed\":\"한우\"}",
                "[\"birth_date\"]");
        given(deliveryRepository.findFirstByHistoryReportIdOrderByIdDesc(30L)).willReturn(Optional.of(d));
        given(deliveryRepository.findForUpdate(41L)).willReturn(Optional.of(d));

        receiver(patch("/api/receiver/history-reports/30/proxy-done").contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\":true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("빈칸을 모두 채워야 신고 마침으로 바꿀 수 있어요."));
        receiver(patch("/api/receiver/history-reports/30/proxy-done").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fields\":{\"sex\":\"수\"},\"done\":false}"))
                .andExpect(status().isUnprocessableContent());
        receiver(patch("/api/receiver/history-reports/30/proxy-done").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fields\":{\"birth_date\":\"11월 1일\"},\"done\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("날짜를 다시 확인해 주세요."));
    }

    @Test
    void 빈칸을_채워_대신_신고를_마치면_완료_농가_알림_귀표_할_일이_생긴다() throws Exception {
        Delivery d = historyDelivery(41L, "출생", "{\"mother_tag4\":\"1234\",\"sex\":\"암\",\"breed\":\"한우\"}",
                "[\"birth_date\"]");
        given(deliveryRepository.findFirstByHistoryReportIdOrderByIdDesc(30L)).willReturn(Optional.of(d));
        given(deliveryRepository.findForUpdate(41L)).willReturn(Optional.of(d));
        given(cowRepository.findFirstByFarmIdAndTagLast4AndStatus(1L, "1234", "active")).willReturn(Optional.of(cow));

        receiver(patch("/api/receiver/history-reports/30/proxy-done").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fields\":{\"birth_date\":\"2026-11-01\"},\"done\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.history.status").value("done"))
                .andExpect(jsonPath("$.data.history.missing").isEmpty())
                .andExpect(jsonPath("$.data.history.fields.birth_date").value("2026-11-01"))
                .andExpect(jsonPath("$.data.ear_tag_due").value(ReceiverRules.today().plusDays(30).toString()));
        verify(checkRepository).save(argThat((ReceiverCheck c) -> "done".equals(c.getStatus())
                && "proxy_reported".equals(c.getResult())));
        verify(taskRepository).save(argThat((Task t) -> "ear_tag".equals(t.getKind()) && t.getRefId() == 30L));
        verify(alertRepository).save(argThat((Alert a) -> "축협이 대신 신고했어요".equals(a.getTitle())));
        org.assertj.core.api.Assertions.assertThat(cow.isActive()).isTrue();

        receiver(patch("/api/receiver/history-reports/30/proxy-done").contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void 관할_농가는_미응답_기한_소규모_순으로_정렬하고_연락처는_공개한_농가만_준다() throws Exception {
        User owner2 = entity(User.class, Map.of("id", 11L, "role", "farmer", "name", "박정희", "phone", "01011112210"));
        FarmInfo small = entity(FarmInfo.class, Map.of("id", 2L, "farmNo", "448013", "owner", owner2,
                "name", "박정희 농장", "regionCode", "4480031000", "headCount", 9));
        given(farmRepository.findJurisdiction(eq(1L), anyCollection())).willReturn(List.of(farm, small));
        Alert old = entity(Alert.class, Map.of("id", 3L, "user", owner2, "kind", "deadline", "title", "귀표 기한",
                "createdAt", Instant.now().minus(Duration.ofHours(50))));
        given(alertRepository.findByUserIdInAndReadAtIsNullAndFeedbackIsNullOrderByIdDesc(anyCollection()))
                .willReturn(List.of(old));
        given(alertRepository.findUserIdsWithAlerts(anyCollection())).willReturn(List.of(10L, 11L));
        Task task = entity(Task.class, Map.of("id", 1L, "farm", farm, "kind", "birth_report", "title", "송아지 출생 신고",
                "dueDate", ReceiverRules.today().plusDays(3)));
        given(taskRepository.findByFarmIdInAndDoneAtIsNullOrderByDueDateAscIdAsc(anyCollection())).willReturn(List.of(task));
        given(diaryRepository.findLastDates(anyCollection()))
                .willReturn(List.<Object[]>of(new Object[] {1L, ReceiverRules.today()}));
        given(deliveryRepository.findSharePhones(eq(1L), anyCollection()))
                .willReturn(List.<Object[]>of(new Object[] {1L, false, 39L}, new Object[] {1L, true, 40L}));

        receiver(get("/api/receiver/jurisdiction/farms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.unanswered").value(1))
                .andExpect(jsonPath("$.data.kpis.due_soon").value(1))
                .andExpect(jsonPath("$.data.kpis.small_farms").value(2))
                .andExpect(jsonPath("$.data.kpis.recorded_today").value(1))
                .andExpect(jsonPath("$.data.farms[0].name").value("박정희 농장"))
                .andExpect(jsonPath("$.data.farms[0].alert_state").value("unanswered"))
                .andExpect(jsonPath("$.data.farms[0].phone_masked").value("010-****-2210"))
                .andExpect(jsonPath("$.data.farms[0].phone").value(nullValue()))
                .andExpect(jsonPath("$.data.farms[1].alert_state").value("seen"))
                .andExpect(jsonPath("$.data.farms[1].phone").value("01024810937"))
                .andExpect(jsonPath("$.data.farms[1].next_task.d_day").value(3));
    }

    @Test
    void 다시_알리기는_미응답_알림을_다시_넣고_없으면_CONFLICT_409() throws Exception {
        given(farmRepository.findWithOwnerById(1L)).willReturn(Optional.of(farm));
        receiver(post("/api/receiver/jurisdiction/farms/1/nudge"))
                .andExpect(status().isConflict());

        Alert old = entity(Alert.class, Map.of("id", 3L, "user", owner, "kind", "deadline", "title", "송아지 출생 신고 기한",
                "refId", 1L, "createdAt", Instant.now().minus(Duration.ofHours(49))));
        given(alertRepository.findByUserIdInAndReadAtIsNullAndFeedbackIsNullOrderByIdDesc(List.of(10L)))
                .willReturn(List.of(old));
        receiver(post("/api/receiver/jurisdiction/farms/1/nudge"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.alert_id").value(70));
        verify(alertRepository).save(argThat((Alert a) -> a.getUser() == owner && "송아지 출생 신고 기한".equals(a.getTitle())
                && a.getRefId() == 1L));
    }

    @Test
    void 관할_밖_농가에는_다시_알릴_수_없다() throws Exception {
        FarmInfo outside = entity(FarmInfo.class, Map.of("id", 3L, "owner", owner, "name", "남의 농장",
                "regionCode", "4481025000", "org", otherOrg));
        given(farmRepository.findWithOwnerById(3L)).willReturn(Optional.of(outside));
        receiver(post("/api/receiver/jurisdiction/farms/3/nudge"))
                .andExpect(status().isForbidden());
        verify(alertRepository, never()).save(any());
    }

    @Test
    void 공식_지침은_게시된_요약을_문서별로_묶는다() throws Exception {
        BiosecurityRule a = entity(BiosecurityRule.class, Map.of("id", 1L, "kind", "summary", "disease", "구제역",
                "sourceDoc", "구제역 SOP", "text", "의심증상: 침 흘림", "revisedAt", LocalDate.of(2026, 5, 6)));
        BiosecurityRule b = entity(BiosecurityRule.class, Map.of("id", 2L, "kind", "summary", "disease", "구제역",
                "sourceDoc", "구제역 SOP", "text", "의심축 발견 시 즉시 신고"));
        BiosecurityRule c = entity(BiosecurityRule.class, Map.of("id", 3L, "kind", "summary",
                "sourceDoc", "가축전염병 예방법", "text", "제11조", "clause", "제11조"));
        given(ruleRepository.findByKindAndStatusOrderByDisplayOrderAscIdAsc("summary", "published"))
                .willReturn(List.of(a, b, c));

        receiver(get("/api/receiver/guides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].revised_at").value("2026-05-06"))
                .andExpect(jsonPath("$.data[0].items.length()").value(2))
                .andExpect(jsonPath("$.data[1].items[0].clause").value("제11조"));
    }

    @Test
    void 주간_통계는_최근_7일_접수_처리_사진_포함과_날짜별_접수를_준다() throws Exception {
        Delivery d1 = symptomDelivery(40L, true, "{}");
        Delivery d2 = historyDelivery(41L, "출생", "{}", "[]");
        given(deliveryRepository.findReceivedSince(eq(1L), any())).willReturn(List.of(d1, d2));
        given(checkRepository.findLatest(anyCollection())).willReturn(List.of(check(d1, "done", "vet_guide")));
        given(mediaRepository.findReportIdsWithPhoto(anyCollection())).willReturn(List.of(7L));

        receiver(get("/api/receiver/stats/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.received").value(2))
                .andExpect(jsonPath("$.data.done").value(1))
                .andExpect(jsonPath("$.data.with_photo").value(1))
                .andExpect(jsonPath("$.data.daily.length()").value(7))
                .andExpect(jsonPath("$.data.daily[6].count").value(2));
    }

    private Delivery symptomDelivery(Long id, boolean sharePhone, String answers) {
        SymptomReport sr = entity(SymptomReport.class, Map.of("id", 7L, "farm", farm, "cow", cow, "answers", answers,
                "matchedCount", (short) 1, "foundAt", Instant.now(), "barn", "2번 축사"));
        ReportSummary summary = entity(ReportSummary.class, Map.of("id", 50L, "symptomReport", sr));
        return entity(Delivery.class, Map.of("id", id, "reportSummary", summary, "route", "coop", "org", coop,
                "sharePhone", sharePhone, "idempotencyKey", "k" + id, "approvedAt", Instant.now(),
                "receivedAt", Instant.now(), "status", "delivered"));
    }

    private Delivery historyDelivery(Long id, String type, String fields, String missing) {
        HistoryReport report = entity(HistoryReport.class, Map.of("id", 30L, "farm", farm, "type", type,
                "fields", fields, "missing", missing, "mode", "coop", "status", "sent",
                "dueDate", ReceiverRules.today().plusDays(3)));
        return entity(Delivery.class, Map.of("id", id, "historyReport", report, "route", "coop", "org", coop,
                "sharePhone", true, "idempotencyKey", "k" + id, "approvedAt", Instant.now(),
                "receivedAt", Instant.now(), "status", "delivered"));
    }

    private ReceiverCheck check(Delivery delivery, String status, String result) {
        Map<String, Object> fields = new HashMap<>(Map.of("id", 90L, "delivery", delivery, "user", receiver,
                "org", coop, "status", status, "at", Instant.now()));
        if (result != null) {
            fields.put("result", result);
        }
        return entity(ReceiverCheck.class, fields);
    }

    private ResultActions receiver(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token));
    }

    private static <T> T withField(T entity, String name, Object value) {
        ReflectionTestUtils.setField(entity, name, value);
        return entity;
    }

    /** 엔티티는 생성자·setter 를 열지 않으므로 테스트에서만 필드를 직접 채운다 */
    private static <T> T entity(Class<T> type, Map<String, Object> fields) {
        T entity = BeanUtils.instantiateClass(type);
        fields.forEach((name, value) -> ReflectionTestUtils.setField(entity, name, value));
        return entity;
    }
}
