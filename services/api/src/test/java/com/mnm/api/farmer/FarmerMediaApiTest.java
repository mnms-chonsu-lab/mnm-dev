package com.mnm.api.farmer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mnm.api.TestTokens;
import com.mnm.api.domain.diary.DiaryEntry;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.symptom.BehaviorRecord;
import com.mnm.api.domain.symptom.FieldMedia;
import com.mnm.api.domain.symptom.SymptomReport;
import com.mnm.api.domain.user.User;
import com.mnm.api.global.ai.AiClient;
import com.mnm.api.global.ai.AiClient.PartCheckResult;
import com.mnm.api.global.ai.AiClient.Quality;
import com.mnm.api.global.ai.AiException;
import com.mnm.api.global.storage.StorageClient;
import com.mnm.api.global.storage.StorageException;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 사진·영상·음성 원본 API (FARM-12 · 13 · 21, 일지 음성). DB 없이(nodb) 리포지토리는 가짜, 저장소·AI 는 mock 구현을 쓴다
 * (STORAGE_ENDPOINT 가 없으면 MockStorageClient).
 */
@SpringBootTest
@ActiveProfiles("nodb")
@AutoConfigureMockMvc
class FarmerMediaApiTest {

    private static final long USER_ID = 10L;
    private static final long FARM_ID = 1L;

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtEncoder jwtEncoder;

    @MockitoSpyBean
    AiClient aiClient;
    @MockitoSpyBean
    StorageClient storage;
    @MockitoBean
    PlatformTransactionManager transactionManager;
    @MockitoBean
    FarmerFarmRepository farmRepository;
    @MockitoBean
    FarmerSymptomReportRepository symptomReportRepository;
    @MockitoBean
    FarmerMediaRepository mediaRepository;
    @MockitoBean
    FarmerBehaviorRepository behaviorRepository;
    @MockitoBean
    FarmerDiaryRepository diaryRepository;
    @MockitoBean
    FarmerDeliveryRepository deliveryRepository;

    private String token;
    private FarmInfo farm;
    private SymptomReport report;

    @BeforeEach
    void setUp() {
        token = TestTokens.bearer(jwtEncoder, USER_ID, "farmer", false, Duration.ofHours(1));
        User owner = entity(User.class, Map.of("id", USER_ID, "role", "farmer", "name", "김한우"));
        farm = entity(FarmInfo.class, Map.of("id", FARM_ID, "owner", owner, "name", "김한우 농장", "headCount", 42));
        report = entity(SymptomReport.class, Map.of("id", 7L, "farm", farm,
                "answers", "{\"drool\":\"yes\",\"blister\":\"no\",\"feed\":\"no\",\"lump\":\"no\",\"found_date\":\""
                        + FarmerRules.today() + "\"}",
                "matchedCount", (short) 1, "foundAt", Instant.now()));
        given(farmRepository.findFirstByOwnerId(USER_ID)).willReturn(Optional.of(farm));
        given(symptomReportRepository.findByIdAndFarmId(7L, FARM_ID)).willReturn(Optional.of(report));
        given(mediaRepository.save(any())).willAnswer(i -> {
            FieldMedia m = i.getArgument(0);
            return m.getId() == null ? withId(m, 77L) : m;
        });
        given(mediaRepository.findBySymptomReportIdAndKindOrderByIdAsc(anyLong(), anyString())).willReturn(List.of());
    }

    @Test
    void 업로드_URL은_내_기록에_경로_규칙대로_발급하고_형식과_부위를_검사한다() throws Exception {
        farmer(post("/api/farmer/media/upload-url").contentType(MediaType.APPLICATION_JSON).content("""
                {"symptom_report_id":7,"kind":"photo","requested_part":"mouth","content_type":"image/jpeg"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.media_id").value(77))
                .andExpect(jsonPath("$.data.fields.key").value("field/1/7/77.jpg"))
                .andExpect(jsonPath("$.data.fields['Content-Type']").value("image/jpeg"))
                .andExpect(jsonPath("$.data.upload_url").exists())
                .andExpect(jsonPath("$.data.expires_at").exists());
        verify(storage).presignPost("field/1/7/77.jpg", "image/jpeg", 10L * 1024 * 1024, Duration.ofMinutes(10));

        farmer(post("/api/farmer/media/upload-url").contentType(MediaType.APPLICATION_JSON).content("""
                {"symptom_report_id":7,"kind":"video","content_type":"video/quicktime"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fields.key").value("field/1/7/77.mov"));
        verify(storage).presignPost("field/1/7/77.mov", "video/quicktime", 100L * 1024 * 1024, Duration.ofMinutes(10));

        farmer(post("/api/farmer/media/upload-url").contentType(MediaType.APPLICATION_JSON).content("""
                {"symptom_report_id":7,"kind":"photo","content_type":"image/jpeg"}
                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("어느 부위 사진인지 골라 주세요."));
        farmer(post("/api/farmer/media/upload-url").contentType(MediaType.APPLICATION_JSON).content("""
                {"symptom_report_id":7,"kind":"photo","requested_part":"mouth","content_type":"image/gif"}
                """))
                .andExpect(status().isUnprocessableContent());
        // 다른 농장 기록
        farmer(post("/api/farmer/media/upload-url").contentType(MediaType.APPLICATION_JSON).content("""
                {"symptom_report_id":8,"kind":"photo","requested_part":"mouth","content_type":"image/jpeg"}
                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void 부위_확인은_결과를_저장하고_AI가_실패하면_확인_안_됨으로_둔다() throws Exception {
        FieldMedia photo = media(77L, "photo", "mouth", "field/1/7/77.jpg");

        farmer(post("/api/farmer/media/77/part-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.match").value(true))
                .andExpect(jsonPath("$.data.dark").value(false))
                .andExpect(jsonPath("$.data.not_cow").value(false))
                .andExpect(jsonPath("$.data.predicted_part").doesNotExist());
        assertThat(photo.getMatch()).isTrue();
        assertThat(photo.getModelVersion()).isEqualTo("mock");
        verify(aiClient).partCheck(eq(storage.presignGet("field/1/7/77.jpg", StorageClient.VIEW_TTL)), eq("mouth"));

        FieldMedia other = media(78L, "photo", "hoof", "field/1/7/78.jpg");
        doThrow(new AiException("PART-FAIL", "시간 초과")).when(aiClient).partCheck(anyString(), eq("hoof"));
        farmer(post("/api/farmer/media/78/part-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.match").doesNotExist());
        assertThat(other.getMatch()).isNull();
    }

    @Test
    void 소가_아닌_사진은_not_cow로_알리고_사진은_3초가_넘어도_5초까지_기다린다() throws Exception {
        media(77L, "photo", "mouth", "field/1/7/77.jpg");
        doAnswer(inv -> {
            Thread.sleep(3500);
            return new PartCheckResult("mouth", "none", false, 0.9, new Quality(false, false, 120), "test", true);
        }).when(aiClient).partCheck(anyString(), eq("mouth"));

        farmer(post("/api/farmer/media/77/part-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.match").value(false))
                .andExpect(jsonPath("$.data.not_cow").value(true))
                .andExpect(jsonPath("$.data.predicted_part").doesNotExist());
    }

    @Test
    void 올라오지_않은_파일이나_종류가_다른_파일은_확인하지_않는다() throws Exception {
        media(77L, "photo", "mouth", "field/1/7/77.jpg");
        doReturn(false).when(storage).exists("field/1/7/77.jpg");
        farmer(post("/api/farmer/media/77/part-check"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD-FAIL"));
        farmer(post("/api/farmer/media/77/behavior"))
                .andExpect(status().isNotFound());
        verify(aiClient, never()).partCheck(anyString(), anyString());
    }

    @Test
    void 행동_영상은_프레임을_저장해_비율을_주고_다시_부르면_저장된_비율을_준다() throws Exception {
        media(80L, "video", null, "field/1/7/80.mp4");
        given(behaviorRepository.findByMediaId(80L)).willReturn(List.of());

        farmer(post("/api/farmer/media/80/behavior"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.ratio.lie").value(70))
                .andExpect(jsonPath("$.data.ratio.stand").value(20))
                .andExpect(jsonPath("$.data.ratio.eat").value(10));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BehaviorRecord>> saved = ArgumentCaptor.forClass(List.class);
        verify(behaviorRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(10);

        given(behaviorRepository.findByMediaId(80L)).willReturn(saved.getValue());
        farmer(post("/api/farmer/media/80/behavior"))
                .andExpect(jsonPath("$.data.ratio.lie").value(70));
        verify(aiClient).behavior(anyString());
    }

    @Test
    void 말로_기록하면_음성_원본을_저장하고_일지_저장_때_연결한다() throws Exception {
        doThrow(new AiException("STT-FAIL", "무음")).when(aiClient).stt(any(), anyString());
        farmer(multipart("/api/farmer/records/voice")
                .file(new MockMultipartFile("audio", "a.webm", "audio/webm;codecs=opus", new byte[] {1, 2, 3})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value("text_input"))
                .andExpect(jsonPath("$.data.audio_media_id").value(77));
        verify(storage).put(eq("audio/1/77.webm"), any(), eq("audio/webm;codecs=opus"));

        FieldMedia audio = media(77L, "audio", null, "audio/1/77.webm");
        given(mediaRepository.findById(77L)).willReturn(Optional.of(audio));
        given(diaryRepository.save(any())).willAnswer(i -> withId(i.getArgument(0), 60L));
        farmer(post("/api/farmer/diary").contentType(MediaType.APPLICATION_JSON).content("""
                {"raw_text":"소독했어","items":[{"category":"소독","value":"소독함"}],"audio_media_id":77}
                """))
                .andExpect(status().isCreated());
        ArgumentCaptor<DiaryEntry> entry = ArgumentCaptor.forClass(DiaryEntry.class);
        verify(diaryRepository).save(entry.capture());
        assertThat(entry.getValue().getAudioMedia()).isSameAs(audio);

        // 다른 농장 음성은 붙이지 않는다
        given(mediaRepository.findById(91L)).willReturn(Optional.of(media(91L, "audio", null, "audio/2/91.webm")));
        farmer(post("/api/farmer/diary").contentType(MediaType.APPLICATION_JSON).content("""
                {"items":[{"category":"소독","value":"소독함"}],"audio_media_id":91}
                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void 일지_음성은_기록일부터_1년이_지나면_듣기_주소를_주지_않는다() throws Exception {
        FieldMedia audio = media(77L, "audio", null, "audio/1/77.webm");
        DiaryEntry recent = DiaryEntry.record(farm, FarmerRules.today().minusDays(364), "소독", null, "소독함", null, null, "소독했어");
        DiaryEntry old = DiaryEntry.record(farm, FarmerRules.today().minusDays(365), "소독", null, "소독함", null, null, "소독했어");
        recent.attachAudio(audio);
        old.attachAudio(audio);
        given(diaryRepository.findByIdAndFarmId(60L, FARM_ID)).willReturn(Optional.of(withId(recent, 60L)));
        given(diaryRepository.findByIdAndFarmId(61L, FARM_ID)).willReturn(Optional.of(withId(old, 61L)));

        farmer(get("/api/farmer/diary/60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audio_url").value(storage.presignGet("audio/1/77.webm", StorageClient.VIEW_TTL)));
        farmer(get("/api/farmer/diary/61"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.raw_text").value("소독했어"))
                .andExpect(jsonPath("$.data.audio_url").doesNotExist());
    }

    @Test
    void 음성_원본을_못_올려도_말로_기록은_계속한다() throws Exception {
        doThrow(new StorageException("연결 실패")).when(storage).put(anyString(), any(), anyString());
        doThrow(new AiException("STT-FAIL", "무음")).when(aiClient).stt(any(), anyString());
        farmer(multipart("/api/farmer/records/voice")
                .file(new MockMultipartFile("audio", "a.m4a", "audio/mp4", new byte[] {1})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audio_media_id").doesNotExist());
        verify(mediaRepository).delete(any());
    }

    @Test
    void 말하기는_글만_돌려주고_저장하지_않으며_못_알아들으면_글_입력으로_안내한다() throws Exception {
        farmer(multipart("/api/farmer/stt")
                .file(new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[] {1, 2})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text").value("어젯밤에 0871번이 송아지 낳았어. 암컷이야."))
                .andExpect(jsonPath("$.data.fallback").doesNotExist());
        verify(storage, never()).put(anyString(), any(), anyString());

        doThrow(new AiException("STT-FAIL", "무음")).when(aiClient).stt(any(), anyString());
        farmer(multipart("/api/farmer/stt")
                .file(new MockMultipartFile("audio", "a.m4a", "audio/mp4", new byte[] {1})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text").doesNotExist())
                .andExpect(jsonPath("$.data.fallback").value("text_input"));

        farmer(multipart("/api/farmer/stt")
                .file(new MockMultipartFile("audio", "a.jpg", "image/jpeg", new byte[] {1})))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALID-422"));
    }

    @Test
    void 오늘_기록_요약에_사진과_영상_원본_주소와_행동_비율이_있다() throws Exception {
        FieldMedia photo = media(77L, "photo", "mouth", "field/1/7/77.jpg");
        FieldMedia hoof = media(78L, "photo", "hoof", "field/1/7/78.jpg");
        FieldMedia retaken = media(79L, "photo", "mouth", "field/1/7/79.jpg");
        FieldMedia video = media(80L, "video", null, "field/1/7/80.mp4");
        given(mediaRepository.findBySymptomReportIdAndKindOrderByIdAsc(7L, "photo")).willReturn(List.of(photo, hoof, retaken));
        given(mediaRepository.findBySymptomReportIdAndKindOrderByIdAsc(7L, "video")).willReturn(List.of(video));
        given(behaviorRepository.findByMediaId(80L)).willReturn(List.of(
                BehaviorRecord.of(video, null, "lie", null, null, "m"), BehaviorRecord.of(video, null, "eat", null, null, "m")));

        farmer(get("/api/farmer/symptom-reports/7/summary"))
                .andExpect(status().isOk())
                // 다시 찍은 사진은 부위별 마지막 1장만 (#37)
                .andExpect(jsonPath("$.data.photos.length()").value(2))
                .andExpect(jsonPath("$.data.photos[0].media_id").value(79))
                .andExpect(jsonPath("$.data.photos[0].url").value(storage.presignGet("field/1/7/79.jpg", StorageClient.VIEW_TTL)))
                .andExpect(jsonPath("$.data.photos[1].media_id").value(78))
                .andExpect(jsonPath("$.data.video.media_id").value(80))
                .andExpect(jsonPath("$.data.video.url").exists())
                .andExpect(jsonPath("$.data.video.ratio.lie").value(50))
                .andExpect(jsonPath("$.data.video.ratio.stand").value(0));
    }

    /** 내 농장 기록에 딸린 파일 (사진·영상) 또는 일지 음성 */
    private FieldMedia media(Long id, String kind, String part, String path) {
        FieldMedia media = withId(FieldMedia.create("audio".equals(kind) ? null : report, kind, part), id);
        media.placeAt(path);
        if (!"audio".equals(kind)) {
            given(mediaRepository.findByIdAndSymptomReportFarmId(id, FARM_ID)).willReturn(Optional.of(media));
            given(mediaRepository.findById(id)).willReturn(Optional.of(media));
        }
        return media;
    }

    private ResultActions farmer(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
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
