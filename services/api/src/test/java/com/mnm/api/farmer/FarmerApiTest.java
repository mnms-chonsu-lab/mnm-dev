package com.mnm.api.farmer;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mnm.api.TestTokens;
import com.mnm.api.domain.delivery.Delivery;
import com.mnm.api.domain.farm.Cow;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.report.HistoryReport;
import com.mnm.api.domain.report.ReportSummary;
import com.mnm.api.domain.symptom.SymptomReport;
import com.mnm.api.domain.task.Task;
import com.mnm.api.domain.user.User;
import com.mnm.api.global.ai.AiClient;
import com.mnm.api.global.ai.AiClient.GuideCitation;
import com.mnm.api.global.ai.AiClient.GuideResult;
import com.mnm.api.global.ai.AiClient.RouteItem;
import com.mnm.api.global.ai.AiClient.RouteResult;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 농가 API 를 실제 보안·검증·오류 처리 설정 그대로 확인한다. DB 없이(nodb) 리포지토리·트랜잭션은 가짜로 바꾼다.
 * AI·이력제는 mock 구현(MockAiClient · MockTraceClient)을 그대로 쓴다.
 */
@SpringBootTest
@ActiveProfiles("nodb")
@AutoConfigureMockMvc
class FarmerApiTest {

    private static final long USER_ID = 10L;
    private static final long FARM_ID = 1L;

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtEncoder jwtEncoder;

    @MockitoSpyBean
    AiClient aiClient;
    @MockitoBean
    PlatformTransactionManager transactionManager;
    @MockitoBean
    FarmerFarmRepository farmRepository;
    @MockitoBean
    FarmerCowRepository cowRepository;
    @MockitoBean
    FarmerDiaryRepository diaryRepository;
    @MockitoBean
    FarmerHistoryReportRepository historyReportRepository;
    @MockitoBean
    FarmerTaskRepository taskRepository;
    @MockitoBean
    FarmerAlertRepository alertRepository;
    @MockitoBean
    FarmerSymptomReportRepository symptomReportRepository;
    @MockitoBean
    FarmerMediaRepository mediaRepository;
    @MockitoBean
    FarmerRuleRepository ruleRepository;
    @MockitoBean
    FarmerOutbreakRepository outbreakRepository;
    @MockitoBean
    FarmerFacilityRepository facilityRepository;
    @MockitoBean
    FarmerDeliveryRepository deliveryRepository;
    @MockitoBean
    FarmerReportSummaryRepository summaryRepository;
    @MockitoBean
    FarmerReceiverCheckRepository receiverCheckRepository;
    @MockitoBean
    FarmerOrgRepository orgRepository;
    @MockitoBean
    FarmerHolidayRepository holidayRepository;
    @MockitoBean
    FarmerUserRepository userRepository;
    @MockitoBean
    FarmerSettingsRepository settingsRepository;
    @MockitoBean
    FarmerRegionRepository regionRepository;

    private String token;
    private FarmInfo farm;
    private Cow mother;

    @BeforeEach
    void setUp() {
        token = TestTokens.bearer(jwtEncoder, USER_ID, "farmer", false, Duration.ofHours(1));
        User owner = entity(User.class, Map.of("id", USER_ID, "role", "farmer", "name", "김한우", "phone", "01000000937"));
        ReceiverOrg coop = entity(ReceiverOrg.class, Map.of("id", 1L, "name", "홍성축협", "type", "축협",
                "regionCodes", new String[] {"4480000000"}));
        farm = entity(FarmInfo.class, Map.of("id", FARM_ID, "farmNo", "448012", "owner", owner, "name", "김한우 농장",
                "address", "충남 홍성군 홍성읍", "headCount", 42, "org", coop));
        mother = entity(Cow.class, Map.of("id", 5L, "farm", farm, "tagLast4", "0871", "tagFull", "002123450871",
                "breed", "한우", "sex", "암", "status", "active", "source", "trace"));
        given(farmRepository.findFirstByOwnerId(USER_ID)).willReturn(Optional.of(farm));
        given(cowRepository.findByFarmIdAndStatus(FARM_ID, "active")).willReturn(List.of(mother));
        given(cowRepository.findByFarmId(FARM_ID)).willReturn(List.of(mother));
        given(cowRepository.findByIdAndFarmId(5L, FARM_ID)).willReturn(Optional.of(mother));
        given(holidayRepository.findByDateBetween(any(), any())).willReturn(List.of());
        given(historyReportRepository.save(any())).willAnswer(i -> withId(i.getArgument(0), 30L));
        given(deliveryRepository.save(any())).willAnswer(i -> withId(i.getArgument(0), 40L));
        given(summaryRepository.save(any())).willAnswer(i -> withId(i.getArgument(0), 50L));
        given(diaryRepository.save(any())).willAnswer(i -> withId(i.getArgument(0), 60L));
    }

    @Test
    void 접수자는_농가_API를_쓸_수_없다() throws Exception {
        mvc.perform(get("/api/farmer/home").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtEncoder, "receiver")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-403"));
    }

    @Test
    void 홈은_할_일_기한순_미확인_알림_수_지역_발생을_준다() throws Exception {
        LocalDate due = FarmerRules.today().plusDays(3);
        Task task = entity(Task.class, Map.of("id", 1L, "farm", farm, "kind", "birth_report", "title", "송아지 출생 신고",
                "dueDate", due, "refId", 30L));
        given(taskRepository.findByFarmIdAndDoneAtIsNullOrderByDueDateAscIdAsc(FARM_ID)).willReturn(List.of(task));
        given(alertRepository.countByUserIdAndReadAtIsNull(USER_ID)).willReturn(2L);

        farmer(get("/api/farmer/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.farmer_name").value("김한우"))
                .andExpect(jsonPath("$.data.task_count").value(1))
                .andExpect(jsonPath("$.data.tasks[0].d_day").value(3))
                .andExpect(jsonPath("$.data.tasks[0].ref_id").value(30))
                .andExpect(jsonPath("$.data.unread_alert_count").value(2))
                .andExpect(jsonPath("$.data.region.fmd_count").value(0));
    }

    @Test
    void 글_기록은_저장하지_않고_신고서_초안과_기한_안내를_돌려준다() throws Exception {
        farmer(post("/api/farmer/records/text").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"어젯밤에 0871번이 송아지 낳았어. 암컷이야.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report_type").value("출생"))
                .andExpect(jsonPath("$.data.items[0].cow_id").value(5))
                .andExpect(jsonPath("$.data.report_fields.mother_tag4").value("0871"))
                .andExpect(jsonPath("$.data.report_fields.breed").value("한우"))
                .andExpect(jsonPath("$.data.missing").isEmpty())
                .andExpect(jsonPath("$.data.report_notice.report_due").exists())
                .andExpect(jsonPath("$.data.fallback").value(org.hamcrest.Matchers.nullValue()));
        verify(diaryRepository, never()).save(any());
    }

    @Test
    void AI가_뽑은_값이_틀리면_빈칸으로_두고_다시_묻는다() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("mother_tag4", "08712");
        fields.put("birth_date", FarmerRules.today().plusDays(1).toString());
        fields.put("sex", "암컷");
        fields.put("weight", "30");
        doReturn(new RouteResult("history_report", List.of(new RouteItem("출생", "송아지 낳음", null, null, null, null)),
                "birth", fields, List.of())).when(aiClient).route(any());

        farmer(post("/api/farmer/records/text").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"송아지 낳았어\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report_fields.sex").value("암"))
                .andExpect(jsonPath("$.data.report_fields.weight").doesNotExist())
                .andExpect(jsonPath("$.data.missing", contains("mother_tag4", "birth_date", "breed")))
                .andExpect(jsonPath("$.data.report_notice").value(org.hamcrest.Matchers.nullValue()));
    }

    /** 같은 농장에 끝 4자리가 같은 소 (시드 farm 1 과 같은 상황) */
    private Cow twin() {
        Cow twin = entity(Cow.class, Map.of("id", 6L, "farm", farm, "tagLast4", "0871", "tagFull", "002123460871",
                "breed", "한우", "sex", "암", "status", "active", "source", "trace"));
        given(cowRepository.findByFarmIdAndStatus(FARM_ID, "active")).willReturn(List.of(mother, twin));
        given(cowRepository.findByFarmId(FARM_ID)).willReturn(List.of(mother, twin));
        return twin;
    }

    @Test
    void 끝_4자리가_겹치는데_4자리만_말하면_소를_비워_두고_다시_묻는다() throws Exception {
        twin();
        farmer(post("/api/farmer/records/text").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"어젯밤에 0871번이 송아지 낳았어\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].cow_id").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.report_fields.mother_tag4").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.missing", org.hamcrest.Matchers.hasItem("mother_tag4")));
    }

    @Test
    void 끝_4자리가_겹쳐도_12자리를_말하면_그_소로_정해진다() throws Exception {
        twin();
        farmer(post("/api/farmer/records/text").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"어젯밤에 002123460871번이 송아지 낳았어\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].cow_id").value(6))
                .andExpect(jsonPath("$.data.report_fields.mother_tag4").value("002123460871"));
    }

    @Test
    void 끝_4자리가_겹치는_소를_4자리로_저장하면_VALID_422() throws Exception {
        twin();
        farmer(post("/api/farmer/diary").contentType(MediaType.APPLICATION_JSON).content("""
                {"items":[{"category":"출생","value":"송아지 낳음","cow_id":5}],
                 "report":{"type":"출생","fields":{"mother_tag4":"0871"}}}
                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("0871번이 2마리예요. 어느 소인지 골라 주세요."));
    }

    @Test
    void 일지를_저장하면_신고서_초안과_기한_할_일이_생긴다() throws Exception {
        String yesterday = FarmerRules.today().minusDays(1).toString();
        farmer(post("/api/farmer/diary").contentType(MediaType.APPLICATION_JSON).content("""
                {"raw_text":"어젯밤에 0871번이 송아지 낳았어","items":[{"category":"출생","value":"송아지 낳음","cow_id":5}],
                 "report":{"type":"출생","fields":{"mother_tag4":"0871","birth_date":"%s","sex":"암"}}}
                """.formatted(yesterday)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.entry_ids[0]").value(60))
                .andExpect(jsonPath("$.data.history_report.history_report_id").value(30))
                .andExpect(jsonPath("$.data.history_report.fields.breed").value("한우"))
                .andExpect(jsonPath("$.data.history_report.can_submit").value(true))
                .andExpect(jsonPath("$.data.history_report.cows[0].tag_full").value("002123450871"));
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void 일지_분류가_틀리거나_목록에_없는_소면_VALID_422() throws Exception {
        farmer(post("/api/farmer/diary").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"category\":\"신고\",\"value\":\"x\"}]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].category"));
        farmer(post("/api/farmer/diary").contentType(MediaType.APPLICATION_JSON).content("""
                {"items":[{"category":"출생","value":"x"}],"report":{"type":"출생","fields":{"mother_tag4":"9999"}}}
                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("목록에 없는 번호예요. 번호를 다시 확인해 주세요."));
    }

    @Test
    void 빈칸이_있으면_직접_제출은_막고_축협_부탁은_키가_있어야_보낸다() throws Exception {
        HistoryReport draft = entity(HistoryReport.class, Map.of("id", 30L, "farm", farm, "type", "출생",
                "fields", "{\"mother_tag4\":\"0871\"}", "missing", "[\"birth_date\",\"sex\",\"breed\"]", "status", "draft"));
        given(historyReportRepository.findByIdAndFarmId(30L, FARM_ID)).willReturn(Optional.of(draft));
        String self = "{\"mode\":\"self\"}";
        String coop = "{\"mode\":\"coop\"}";

        farmer(post("/api/farmer/history-reports/30/submit").contentType(MediaType.APPLICATION_JSON).content(self))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALID-422"));
        farmer(post("/api/farmer/history-reports/30/submit").contentType(MediaType.APPLICATION_JSON).content(coop))
                .andExpect(status().isUnprocessableContent());
        farmer(post("/api/farmer/history-reports/30/submit").header("Idempotency-Key", "k-1")
                .contentType(MediaType.APPLICATION_JSON).content(coop))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("sent"))
                .andExpect(jsonPath("$.data.delivery_id").value(40))
                .andExpect(jsonPath("$.data.org_name").value("홍성축협"));
        verify(deliveryRepository).save(any(Delivery.class));

        // 이미 보낸 신고서는 다시 보낼 수 없다
        farmer(post("/api/farmer/history-reports/30/submit").header("Idempotency-Key", "k-2")
                .contentType(MediaType.APPLICATION_JSON).content(coop))
                .andExpect(status().isConflict());
    }

    @Test
    void 전달은_키가_필요하고_축협_보내기를_끈_전화_경로는_수신함에_보내지_않는다() throws Exception {
        symptomReport();
        String call = "{\"symptom_report_id\":7,\"route\":\"call\",\"send_to_coop\":false}";
        farmer(post("/api/farmer/deliveries").contentType(MediaType.APPLICATION_JSON).content(call))
                .andExpect(status().isUnprocessableContent());

        farmer(post("/api/farmer/deliveries").header("Idempotency-Key", "k-1")
                .contentType(MediaType.APPLICATION_JSON).content(call))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("not_sent"))
                .andExpect(jsonPath("$.data.disease").value("FMD"));
        verify(deliveryRepository, never()).save(any());
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void 축협_경로는_요약을_만들어_담당_축협_수신함에_보낸다() throws Exception {
        symptomReport();
        farmer(post("/api/farmer/deliveries").header("Idempotency-Key", "k-1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"symptom_report_id\":7,\"route\":\"coop\",\"share_phone\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.delivery_id").value(40))
                .andExpect(jsonPath("$.data.status").value("delivered"))
                .andExpect(jsonPath("$.data.org_name").value("홍성축협"))
                .andExpect(jsonPath("$.data.form6_text").exists());
        verify(summaryRepository).save(any(ReportSummary.class));
    }

    @Test
    void 이력제_조회는_못_찾음_다른_농장_결과를_오류_코드로_알린다() throws Exception {
        farmer(get("/api/farmer/cows/lookup").param("tag_no", "002 1234 5678 0"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATTLE-NOTFOUND"));
        farmer(get("/api/farmer/cows/lookup").param("tag_no", "008123456781"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATTLE-OTHER-FARM"));
        farmer(get("/api/farmer/cows/lookup").param("tag_no", "009123456781"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TRACE-API-FAIL"));
        farmer(get("/api/farmer/cows/lookup").param("tag_no", "002123456781"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.breed").value("한우"))
                .andExpect(jsonPath("$.data.already_registered").value(false));
    }

    @Test
    void 소_등록은_중복이면_409_못_찾으면_성별과_출생일이_필요하다() throws Exception {
        given(cowRepository.findByFarmIdAndTagFull(FARM_ID, "002123450871")).willReturn(Optional.of(mother));
        farmer(post("/api/farmer/farms/me/cows").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag_no\":\"002123450871\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 등록된 소예요."));

        farmer(post("/api/farmer/farms/me/cows").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag_no\":\"002123456780\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("성별과 출생일을 넣어 주세요."));

        given(cowRepository.save(any())).willAnswer(i -> withId(i.getArgument(0), 6L));
        farmer(post("/api/farmer/farms/me/cows").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag_no\":\"002123456780\",\"sex\":\"수\",\"birth_date\":\"2024-01\",\"barn\":\"3번 축사\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("manual"))
                .andExpect(jsonPath("$.data.tag_last4").value("6780"))
                .andExpect(jsonPath("$.data.birth_date").value("2024-01-01"));
    }

    @Test
    void 직접_입력한_소를_이력제에서_다시_불러오면_이력제_값으로_바뀌고_축사는_그대로() throws Exception {
        Cow manual = manualCow(8L, "002123456781");
        farmer(patch("/api/farmer/cows/8").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_trace\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cow.source").value("trace"))
                .andExpect(jsonPath("$.data.cow.breed").value("한우"))
                .andExpect(jsonPath("$.data.cow.birth_date").value("2022-09-17"))
                .andExpect(jsonPath("$.data.cow.vaccine_last_order").value(7))
                .andExpect(jsonPath("$.data.cow.barn").value("3번 축사"))
                .andExpect(jsonPath("$.data.cow.trace_checked_at").exists())
                .andExpect(jsonPath("$.data.cow.status").value("active"));
    }

    @Test
    void 이력제_다시_불러오기가_안_되면_오류_코드를_주고_값은_그대로() throws Exception {
        Cow notFound = manualCow(8L, "002123456780");
        farmer(patch("/api/farmer/cows/8").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_trace\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATTLE-NOTFOUND"));
        org.assertj.core.api.Assertions.assertThat(notFound.getSource()).isEqualTo("manual");

        manualCow(8L, "009123456781");
        farmer(patch("/api/farmer/cows/8").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_trace\":true}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TRACE-API-FAIL"));

        Cow other = manualCow(8L, "008123456781");
        farmer(patch("/api/farmer/cows/8").contentType(MediaType.APPLICATION_JSON).content("{\"refresh_trace\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATTLE-OTHER-FARM"));
        org.assertj.core.api.Assertions.assertThat(other.getBreed()).isNull();
    }

    /** 직접 입력으로 등록한 소 (연·월만 넣어 1일로 저장됨) */
    private Cow manualCow(Long id, String tagFull) {
        Cow cow = entity(Cow.class, Map.of("id", id, "farm", farm, "tagLast4", tagFull.substring(8), "tagFull", tagFull,
                "sex", "수", "birthDate", LocalDate.of(2024, 3, 1), "barn", "3번 축사", "status", "active", "source", "manual"));
        given(cowRepository.findByIdAndFarmId(id, FARM_ID)).willReturn(Optional.of(cow));
        return cow;
    }

    @Test
    void 소_상태_기록은_혹만_예면_몸_옆면_사진과_럼피스킨_안내() throws Exception {
        given(symptomReportRepository.save(any())).willAnswer(i -> withId(i.getArgument(0), 7L));
        farmer(post("/api/farmer/symptom-reports").contentType(MediaType.APPLICATION_JSON).content("""
                {"cow_id":5,"answers":{"drool":"no","blister":"no","feed":"unknown","lump":"yes"},"head_count":1}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.matched_count").value(1))
                .andExpect(jsonPath("$.data.matched", contains("혹")))
                .andExpect(jsonPath("$.data.required_parts[0].part").value("flank"))
                .andExpect(jsonPath("$.data.required_parts[0].part_name").value("몸 옆면"))
                .andExpect(jsonPath("$.data.disease").value("LSD"));

        farmer(post("/api/farmer/symptom-reports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":{\"drool\":\"maybe\",\"blister\":\"no\",\"feed\":\"no\",\"lump\":\"no\"}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", hasItem("answers.drool")));
    }

    @Test
    void 지금_할_일은_FMD_LSD_만_받는다() throws Exception {
        farmer(get("/api/farmer/guide/actions").param("disease", "flu"))
                .andExpect(status().isUnprocessableContent());
        given(ruleRepository.findByKindAndStatusOrderByDisplayOrderAscIdAsc(eq("action"), anyString())).willReturn(List.of());
        farmer(get("/api/farmer/guide/actions").param("disease", "fmd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disease").value("FMD"))
                .andExpect(jsonPath("$.data.actions").isEmpty());
    }

    @Test
    void 지금_할_일은_AI가_고른_조항을_원문_그대로_쓴다() throws Exception {
        doReturn(new GuideResult(List.of("31"), List.of("축사 출입을 막아 주세요."),
                List.of(new GuideCitation("구제역 긴급행동지침", "3-1", LocalDate.of(2025, 1, 1))), false))
                .when(aiClient).guide("FMD", "after_report", null);
        farmer(get("/api/farmer/guide/actions").param("disease", "fmd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guide_name").value("구제역 긴급행동지침"))
                .andExpect(jsonPath("$.data.actions[0].rule_id").value(31))
                .andExpect(jsonPath("$.data.actions[0].text").value("축사 출입을 막아 주세요."))
                .andExpect(jsonPath("$.data.actions[0].clause").value("3-1"));
    }

    @Test
    void 연락처는_다른_농가와_같은_번호면_바꿀_수_없다() throws Exception {
        given(userRepository.existsByRoleAndPhoneAndIdNot("farmer", "01000000001", USER_ID)).willReturn(true);
        farmer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/farmer/me/farm")
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"010-0000-0001\"}"))
                .andExpect(status().isConflict());
        verify(userRepository).existsByRoleAndPhoneAndIdNot(eq("farmer"), eq("01000000001"), anyLong());
    }

    private void symptomReport() {
        SymptomReport report = entity(SymptomReport.class, Map.of("id", 7L, "farm", farm, "cow", mother,
                "answers", "{\"drool\":\"yes\",\"blister\":\"no\",\"feed\":\"yes\",\"lump\":\"no\",\"head_count\":1,"
                        + "\"found_date\":\"" + FarmerRules.today() + "\"}",
                "matchedCount", (short) 2, "foundAt", Instant.now()));
        given(symptomReportRepository.findByIdAndFarmId(7L, FARM_ID)).willReturn(Optional.of(report));
        given(diaryRepository.findByFarmIdAndDateBetweenOrderByDateDescIdDesc(anyLong(), any(), any())).willReturn(List.of());
        given(mediaRepository.findBySymptomReportIdAndKindOrderByIdAsc(anyLong(), anyString())).willReturn(List.of());
    }

    private ResultActions farmer(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token));
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static <T> T entity(Class<T> type, Map<String, Object> fields) {
        T entity = BeanUtils.instantiateClass(type);
        fields.forEach((name, value) -> ReflectionTestUtils.setField(entity, name, value));
        return entity;
    }
}
