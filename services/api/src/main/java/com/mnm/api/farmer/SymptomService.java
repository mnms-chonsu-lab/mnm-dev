package com.mnm.api.farmer;

import com.mnm.api.domain.farm.Cow;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.guide.BiosecurityRule;
import com.mnm.api.domain.symptom.FieldMedia;
import com.mnm.api.domain.symptom.SymptomReport;
import com.mnm.api.farmer.DiaryDtos.DiaryEntryResponse;
import com.mnm.api.farmer.SymptomDtos.ActionsResponse;
import com.mnm.api.farmer.SymptomDtos.GuideText;
import com.mnm.api.farmer.SymptomDtos.LatestVaccine;
import com.mnm.api.farmer.SymptomDtos.PartGuide;
import com.mnm.api.farmer.SymptomDtos.PhotoResult;
import com.mnm.api.farmer.SymptomDtos.QuestionResponse;
import com.mnm.api.farmer.SymptomDtos.SummaryCow;
import com.mnm.api.farmer.SymptomDtos.SymptomReportRequest;
import com.mnm.api.farmer.SymptomDtos.SymptomReportResponse;
import com.mnm.api.farmer.SymptomDtos.SymptomSummaryResponse;
import com.mnm.api.global.ai.AiClient;
import com.mnm.api.global.ai.AiClient.SummaryDiary;
import com.mnm.api.global.ai.AiClient.SummaryPhoto;
import com.mnm.api.global.ai.AiClient.SummaryRequest;
import com.mnm.api.global.ai.AiClient.SummaryResult;
import com.mnm.api.global.ai.AiException;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공식 증상 질문·소 상태 기록·오늘 기록 요약·질병별 지금 할 일 (F-08 · F-09 · F-10 · F-12) */
@Slf4j
@Service
@RequiredArgsConstructor
class SymptomService {

    static final String PUBLISHED = "published";
    /** 요약에 붙이는 관련 일지 (같은 소 최근 7일), 전달 묶음 일지 (최근 14일) */
    static final int RELATED_DAYS = 7;
    static final int SENT_DAYS = 14;
    static final int MAX_ACTIONS = 3;

    private static final Map<String, String> PART_NAMES = Map.of(
            FarmerRules.MOUTH, "입", FarmerRules.HOOF, "발굽", FarmerRules.FLANK, "몸 옆면", "udder", "유방");
    /** 부위 확인(/ai/part-check) 코드 → 요약·규칙 코드 */
    private static final Map<String, String> PART_CHECK_CODES = Map.of(
            "head_mouth", "mouth", "hoof", "hoof", "body_side_skin", "flank", "udder_teat", "udder");
    private static final Map<String, String> DISEASE_NAMES = Map.of("FMD", "구제역", "LSD", "럼피스킨");

    private final FarmerSupport support;
    private final AiClient aiClient;
    private final FarmerRuleRepository ruleRepository;
    private final FarmerCowRepository cowRepository;
    private final FarmerSymptomReportRepository reportRepository;
    private final FarmerDiaryRepository diaryRepository;
    private final FarmerMediaRepository mediaRepository;
    private final FarmerDeliveryRepository deliveryRepository;
    private final MediaService mediaService;

    /** FARM-10 게시된 증상 질문. 순서대로 drool · blister · feed · lump 답 키를 붙인다 */
    @Transactional(readOnly = true)
    List<QuestionResponse> questions() {
        List<BiosecurityRule> rules = ruleRepository.findByKindAndStatusOrderByDisplayOrderAscIdAsc("question", PUBLISHED);
        List<QuestionResponse> result = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            BiosecurityRule r = rules.get(i);
            // shortcut: 질문↔답 키 연결 컬럼이 없어 표시 순서로 정한다. 질문이 4개가 아니게 되면 mnm-data 에 키 컬럼을 요청한다
            String key = i < FarmerRules.SYMPTOM_KEYS.size() ? FarmerRules.SYMPTOM_KEYS.get(i) : null;
            result.add(new QuestionResponse(r.getId(), key, r.getText(), r.getExampleImage(),
                    support.readList(r.getYesRequiredParts()), r.getSourceDoc(), r.getClause()));
        }
        return result;
    }

    /** FARM-11 소 상태 기록 저장 + 공식 증상 일치 수 + 찍을 부위 + 지침 분기 */
    @Transactional
    SymptomReportResponse create(Long userId, SymptomReportRequest request) {
        FarmInfo farm = support.farm(userId);
        Cow cow = request.cowId() == null ? null : cowRepository.findByIdAndFarmId(request.cowId(), farm.getId())
                .filter(Cow::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATTLE_NOT_FOUND, "내 소 목록에 없음"));
        LocalDate found = request.foundDate() == null ? FarmerRules.today() : request.foundDate();
        if (found.isAfter(FarmerRules.today())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "날짜를 다시 확인해 주세요.", "미래 날짜");
        }
        Map<String, String> answers = request.answers().toMap();
        // answers JSON 에 답 4개와 신고서 빈칸 답(F-S09Q)을 함께 둔다 (symptom_report 에 따로 컬럼이 없음)
        Map<String, Object> stored = new LinkedHashMap<>(answers);
        stored.put("observed_text", blankToNull(request.observedText()));
        stored.put("head_count", request.headCount());
        stored.put("dead_count", request.deadCount());
        stored.put("found_date", found.toString());
        List<String> matched = FarmerRules.matchedSymptoms(answers);
        String barn = request.barn() != null ? blankToNull(request.barn()) : cow == null ? null : cow.getBarn();
        Instant foundAt = found.atStartOfDay(FarmerRules.KST).toInstant();
        SymptomReport report = reportRepository.save(
                SymptomReport.create(farm, cow, support.write(stored), (short) matched.size(), foundAt, barn));
        return new SymptomReportResponse(report.getId(), matched.size(), matched,
                partGuides(FarmerRules.requiredParts(answers)), FarmerRules.disease(answers));
    }

    /** FARM-14 오늘 기록 요약. 6개 항목은 요약 규칙(/ai/summary)으로 만들고, 실패하면 비워 두고 원본 기록만 보여준다 */
    @Transactional(readOnly = true)
    SymptomSummaryResponse summary(Long userId, Long reportId) {
        FarmInfo farm = support.farm(userId);
        SymptomReport report = find(farm, reportId);
        Map<String, Object> stored = support.readMap(report.getAnswers());
        Map<String, String> answers = answers(stored);
        LocalDate found = foundDate(report, stored);
        Cow cow = report.getCow();
        List<DiaryEntryResponse> diary = diary(farm, found);
        List<FieldMedia> photos = mediaRepository.findBySymptomReportIdAndKindOrderByIdAsc(report.getId(), "photo");

        Map<String, Object> form6 = null;
        String form6Text = null;
        try {
            SummaryResult result = aiClient.summary(summaryRequest(farm, report, stored, diary, photos));
            form6 = result.form6();
            form6Text = result.form6Text();
        } catch (AiException e) {
            log.warn("요약 규칙 실패, 원본 기록만 보여줌: {}", e.getMessage());
        }
        List<String> matched = FarmerRules.matchedSymptoms(answers);
        return new SymptomSummaryResponse(report.getId(),
                cow == null ? null : new SummaryCow(cow.getId(), cow.getTagLast4(), cow.getBreed(), cow.getSex(),
                        FarmerSupport.age(cow.getBirthDate()), cow.getBarn()),
                matched.size(), matched, (String) stored.get("observed_text"), intOrNull(stored.get("head_count")), found,
                report.getBarn(), relatedDiary(diary, cow, found), vaccine(diary, cow, found),
                photos.stream().map(m -> new PhotoResult(m.getId(), partCode(m.getRequestedPart()), m.getMatch(),
                        mediaService.viewUrl(m))).toList(),
                mediaService.video(report.getId()), FarmerRules.disease(answers),
                deliveryRepository.existsByReportSummarySymptomReportId(report.getId()),
                form6, form6Text);
    }

    /** FARM-16 질병별 지금 할 일. 게시된 조치 문구 원문만, 단계는 신고 직후(after_report) 우선 */
    @Transactional(readOnly = true)
    ActionsResponse actions(String disease) {
        String code = disease.toUpperCase();
        String name = DISEASE_NAMES.get(code);
        List<BiosecurityRule> rules = ruleRepository.findByKindAndStatusOrderByDisplayOrderAscIdAsc("action", PUBLISHED)
                .stream()
                .filter(r -> r.getDisease() != null
                        && (r.getDisease().equalsIgnoreCase(code) || r.getDisease().startsWith(name)))
                .toList();
        List<BiosecurityRule> afterReport = rules.stream().filter(r -> "after_report".equals(r.getStage())).toList();
        List<GuideText> actions = (afterReport.isEmpty() ? rules : afterReport).stream()
                .limit(MAX_ACTIONS)
                .map(r -> new GuideText(r.getId(), r.getText(), r.getSourceDoc(), r.getClause(), r.getRevisedAt()))
                .toList();
        return new ActionsResponse(code, actions.isEmpty() ? null : actions.getFirst().sourceDoc(), actions);
    }

    /** 전달(FARM-15)과 요약(FARM-14)이 같은 입력으로 /ai/summary 를 부른다 */
    SummaryRequest summaryRequest(FarmInfo farm, SymptomReport report) {
        Map<String, Object> stored = support.readMap(report.getAnswers());
        LocalDate found = foundDate(report, stored);
        return summaryRequest(farm, report, stored, diary(farm, found),
                mediaRepository.findBySymptomReportIdAndKindOrderByIdAsc(report.getId(), "photo"));
    }

    SymptomReport find(FarmInfo farm, Long reportId) {
        return reportRepository.findByIdAndFarmId(reportId, farm.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "소 상태 기록 없음"));
    }

    private SummaryRequest summaryRequest(FarmInfo farm, SymptomReport report, Map<String, Object> stored,
            List<DiaryEntryResponse> diary, List<FieldMedia> photos) {
        Map<String, String> answers = answers(stored);
        Cow cow = report.getCow();
        String owner = farm.getOwner().getName();
        String place = joinNonNull(" ", farm.getAddress(), report.getBarn());
        List<String> symptoms = new ArrayList<>(FarmerRules.matchedSymptoms(answers));
        if (stored.get("observed_text") != null) {
            symptoms.add(stored.get("observed_text").toString());
        }
        Integer heads = intOrNull(stored.get("head_count"));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("owner_and_location", place.isEmpty() ? null : owner + " · " + place);
        fields.put("species", cow != null && cow.getBreed() != null ? cow.getBreed() : "한우");
        fields.put("head_count", heads != null ? heads : cow != null ? 1 : null);
        fields.put("symptoms", symptoms);
        fields.put("found_date", foundDate(report, stored).toString());
        fields.put("reporter", farm.getAddress() == null ? owner : owner + " · " + farm.getAddress());
        fields.put("other", null);
        Map<String, Boolean> yes = FarmerRules.SYMPTOM_KEYS.stream()
                .collect(Collectors.toMap(Function.identity(), k -> "yes".equals(answers.get(k))));
        return new SummaryRequest(String.valueOf(report.getId()), foundDate(report, stored),
                new AiClient.SummaryCow(cow == null ? null : cow.getTagLast4(), null, report.getBarn()), yes,
                photos.stream().filter(m -> m.getMatch() != null && partCode(m.getRequestedPart()) != null)
                        .map(m -> new SummaryPhoto(partCode(m.getRequestedPart()), m.getMatch())).toList(),
                diary.stream().map(d -> new SummaryDiary(d.cowTag4(), d.category(), d.value(), d.date())).toList(),
                fields, farm.getHeadCount());
    }

    private List<PartGuide> partGuides(List<String> parts) {
        if (parts.isEmpty()) {
            return List.of();
        }
        Map<String, BiosecurityRule> guides = ruleRepository
                .findByKindAndStatusOrderByDisplayOrderAscIdAsc("photo_guide", PUBLISHED).stream()
                .filter(r -> r.getPartCode() != null)
                .collect(Collectors.toMap(BiosecurityRule::getPartCode, r -> r, (a, b) -> a));
        return parts.stream().map(p -> {
            BiosecurityRule g = guides.get(p);
            String name = g != null && g.getPhotoPart() != null ? g.getPhotoPart() : PART_NAMES.get(p);
            return new PartGuide(p, name, g == null ? null : g.getText(), g == null ? null : g.getId());
        }).toList();
    }

    /** 일지 (발견일 포함 최근 14일) */
    private List<DiaryEntryResponse> diary(FarmInfo farm, LocalDate found) {
        return diaryRepository.findByFarmIdAndDateBetweenOrderByDateDescIdDesc(farm.getId(),
                        found.minusDays(SENT_DAYS - 1), found).stream()
                .map(DiaryService::toResponse)
                .toList();
    }

    private static List<DiaryEntryResponse> relatedDiary(List<DiaryEntryResponse> diary,
            Cow cow, LocalDate found) {
        if (cow == null) {
            return List.of();
        }
        return diary.stream()
                .filter(d -> cow.getId().equals(d.cowId()) && !d.date().isBefore(found.minusDays(RELATED_DAYS - 1)))
                .toList();
    }

    private static LatestVaccine vaccine(List<DiaryEntryResponse> diary, Cow cow, LocalDate found) {
        return diary.stream()
                .filter(d -> "접종".equals(d.category())
                        && (d.cowId() == null || (cow != null && cow.getId().equals(d.cowId()))))
                .max(Comparator.comparing(DiaryEntryResponse::date))
                .map(d -> new LatestVaccine(d.date(), joinNonNull(" ", d.vaccineType(), d.value())))
                .orElseGet(() -> cow == null || cow.getVaccineLastDate() == null ? null
                        : new LatestVaccine(cow.getVaccineLastDate(),
                                cow.getVaccineLastOrder() == null ? "백신 접종 (이력제)" : "백신 " + cow.getVaccineLastOrder() + "차 (이력제)"));
    }

    private static Map<String, String> answers(Map<String, Object> stored) {
        Map<String, String> answers = new LinkedHashMap<>();
        FarmerRules.SYMPTOM_KEYS.forEach(k -> answers.put(k, Objects.toString(stored.get(k), "unknown")));
        return answers;
    }

    private static LocalDate foundDate(SymptomReport report, Map<String, Object> stored) {
        Object found = stored.get("found_date");
        if (found != null) {
            return LocalDate.parse(found.toString());
        }
        Instant at = report.getFoundAt() != null ? report.getFoundAt() : report.getCreatedAt();
        return at == null ? FarmerRules.today() : at.atZone(FarmerRules.KST).toLocalDate();
    }

    private static String partCode(String requestedPart) {
        if (requestedPart == null) {
            return null;
        }
        return PART_CHECK_CODES.getOrDefault(requestedPart, PART_NAMES.containsKey(requestedPart) ? requestedPart : null);
    }

    private static Integer intOrNull(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String joinNonNull(String sep, String... parts) {
        return Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(sep));
    }
}
