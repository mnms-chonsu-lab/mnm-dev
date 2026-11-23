package com.mnm.api.farmer;

import com.mnm.api.domain.delivery.Delivery;
import com.mnm.api.domain.diary.DiaryEntry;
import com.mnm.api.domain.farm.Cow;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.report.HistoryReport;
import com.mnm.api.domain.symptom.FieldMedia;
import com.mnm.api.farmer.DiaryDtos.DiaryDay;
import com.mnm.api.farmer.DiaryDtos.DiaryEntryDetail;
import com.mnm.api.farmer.DiaryDtos.DiaryEntryResponse;
import com.mnm.api.farmer.DiaryDtos.DiaryItemRequest;
import com.mnm.api.farmer.DiaryDtos.DiarySaveRequest;
import com.mnm.api.farmer.DiaryDtos.DiarySaveResponse;
import com.mnm.api.farmer.DiaryDtos.DiaryUpdateRequest;
import com.mnm.api.farmer.DiaryDtos.DraftItem;
import com.mnm.api.farmer.DiaryDtos.RecordDraft;
import com.mnm.api.farmer.DiaryDtos.ReportNotice;
import com.mnm.api.farmer.DiaryDtos.SentTo;
import com.mnm.api.global.ai.AiClient;
import com.mnm.api.global.ai.AiClient.RouteCow;
import com.mnm.api.global.ai.AiClient.RouteRequest;
import com.mnm.api.global.ai.AiClient.RouteResult;
import com.mnm.api.global.ai.AiException;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 말·글 기록 → 정리 초안, 일지 저장·보기·고치기·지우기 (F-03·F-13) */
@Slf4j
@Service
@RequiredArgsConstructor
class DiaryService {

    /** 목록 한 번에 30일씩 (F-S15) */
    static final int WINDOW_DAYS = 30;
    /** 전달 때 함께 보내는 일지 기간 (F-11) */
    static final int SENT_DIARY_DAYS = 14;

    private final FarmerSupport support;
    private final AiClient aiClient;
    private final FarmerCowRepository cowRepository;
    private final FarmerDiaryRepository diaryRepository;
    private final FarmerHolidayRepository holidayRepository;
    private final FarmerDeliveryRepository deliveryRepository;
    private final FarmerReceiverCheckRepository receiverCheckRepository;
    private final HistoryReportService historyReportService;
    private final MediaService mediaService;

    /**
     * FARM-03. 음성 → 문장 → 정리 초안. 일지는 저장하지 않는다. 음성 원본은 저장소에 먼저 올리고 audio_media_id 를 돌려준다
     * (FARM-04 저장 때 연결). AI 호출 동안 DB 트랜잭션을 잡지 않으려고 @Transactional 을 붙이지 않는다.
     */
    RecordDraft draftFromVoice(Long userId, MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "녹음된 소리가 없어요. 다시 말해 주세요.", "빈 파일");
        }
        String contentType = audio.getContentType();
        if (contentType == null || !(contentType.startsWith("audio/") || contentType.equals("video/webm")
                || contentType.equals("video/mp4"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "음성 파일만 올릴 수 있어요.", "음성 형식 아님");
        }
        FarmInfo farm = support.farm(userId);
        RecordDraft textInput = new RecordDraft(null, null, List.of(), null, Map.of(), List.of(), null, "text_input");
        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            log.warn("음성 파일 읽기 실패, 글로 쓰기로 안내: {}", e.getMessage());
            return textInput;
        }
        Long audioMediaId = mediaService.storeAudio(farm, bytes, contentType);
        String text;
        try {
            text = aiClient.stt(bytes, contentType).text();
        } catch (AiException e) {
            log.warn("음성 인식 실패, 글로 쓰기로 안내: {}", e.getMessage());
            return textInput.withAudio(audioMediaId);
        }
        return draft(farm, text).withAudio(audioMediaId);
    }

    /** FARM-25. 글 → 정리 초안 (음성 인식만 건너뜀) */
    RecordDraft draftFromText(Long userId, String text) {
        return draft(support.farm(userId), text.trim());
    }

    private RecordDraft draft(FarmInfo farm, String text) {
        Map<String, Cow> cows = cowRepository.findByFarmIdAndStatus(farm.getId(), "active").stream()
                .filter(c -> c.getTagLast4() != null)
                .collect(Collectors.toMap(Cow::getTagLast4, Function.identity(), (a, b) -> a));
        RouteResult route;
        try {
            List<RouteCow> context = cows.values().stream().map(c -> new RouteCow(c.getTagLast4(), null, c.getBarn())).toList();
            route = aiClient.route(new RouteRequest(text, FarmerRules.today(), context));
        } catch (AiException e) {
            log.warn("기록 정리 실패, 선택형 입력으로 안내: {}", e.getMessage());
            return new RecordDraft(text, null, List.of(), null, Map.of(), List.of(), null, "select_input");
        }
        List<DraftItem> items = route.items().stream()
                .map(i -> new DraftItem(i.category(), i.value(), i.cowTag4(),
                        Optional.ofNullable(i.cowTag4()).map(cows::get).map(Cow::getId).orElse(null),
                        i.vaccineType(), i.headCount()))
                .toList();
        if (route.reportType() == null) {
            return new RecordDraft(text, route.intent(), items, null, Map.of(), route.missing(), null, null);
        }
        Map<String, Object> raw = new LinkedHashMap<>(route.reportFields());
        String type = FarmerRules.reportType(route.reportType(), raw.remove("move_type"));
        // 틀린 값은 빈칸으로 (저장 때 VALID-422 로 멈추지 않게). 송아지 품종은 어미 소 품종으로 채워진다
        Map<String, Object> fields = historyReportService.cleanAiFields(farm, type, raw);
        List<String> missing = FarmerRules.missing(type, fields);
        return new RecordDraft(text, route.intent(), items, type, fields, missing, notice(type, fields), null);
    }

    /** 신고 기한 안내 (F-S03 "송아지 출생은 5일 안에 신고해야 해요") */
    private ReportNotice notice(String type, Map<String, Object> fields) {
        String event = HistoryReportService.str(fields.get(FarmerRules.eventDateField(type)));
        if (event == null) {
            return null;
        }
        LocalDate eventDate;
        try {
            eventDate = LocalDate.parse(event);
        } catch (DateTimeParseException e) {
            return null;
        }
        var holidays = holidayRepository.findByDateBetween(eventDate, eventDate.plusDays(30)).stream()
                .map(h -> h.getDate())
                .collect(Collectors.toSet());
        LocalDate due = FarmerRules.reportDue(eventDate, holidays);
        return new ReportNotice(due, ChronoUnit.DAYS.between(FarmerRules.today(), due));
    }

    /** FARM-04 저장. 확인된 항목만 일지에 남기고, 신고 대상이면 신고서 초안도 만든다 (한 트랜잭션) */
    @Transactional
    DiarySaveResponse save(Long userId, DiarySaveRequest request) {
        FarmInfo farm = support.farm(userId);
        LocalDate date = request.date() == null ? FarmerRules.today() : notFuture(request.date());
        FieldMedia audio = request.audioMediaId() == null ? null : mediaService.ownAudio(farm, request.audioMediaId());
        List<Long> ids = new ArrayList<>();
        for (DiaryItemRequest item : request.items()) {
            DiaryEntry entry = DiaryEntry.record(farm, date, item.category(), cow(farm, item.cowId()), item.value(),
                    item.vaccineType(), item.headCount(), request.rawText());
            entry.attachAudio(audio);
            ids.add(diaryRepository.save(entry).getId());
        }
        if (request.report() == null) {
            return new DiarySaveResponse(ids, null);
        }
        HistoryReport report = historyReportService.createDraft(farm, request.report().type(), request.report().fields());
        return new DiarySaveResponse(ids, historyReportService.toResponse(report, farm));
    }

    /** FARM-04 목록. cursor(YYYY-MM-DD) 날짜부터 30일씩 최근순. 기록이 없는 기간은 건너뛴다 */
    @Transactional(readOnly = true)
    PageResponse<DiaryDay> list(Long userId, String cursor) {
        FarmInfo farm = support.farm(userId);
        LocalDate from = cursor == null || cursor.isBlank() ? LocalDate.of(9999, 12, 31) : parseCursor(cursor);
        Optional<DiaryEntry> latest = diaryRepository.findFirstByFarmIdAndDateLessThanEqualOrderByDateDesc(farm.getId(), from);
        if (latest.isEmpty()) {
            return new PageResponse<>(List.of(), false, null);
        }
        LocalDate end = latest.get().getDate();
        LocalDate start = end.minusDays(WINDOW_DAYS - 1);
        Map<LocalDate, List<DiaryEntryResponse>> byDate = new LinkedHashMap<>();
        diaryRepository.findByFarmIdAndDateBetweenOrderByDateDescIdDesc(farm.getId(), start, end)
                .forEach(e -> byDate.computeIfAbsent(e.getDate(), d -> new ArrayList<>()).add(toResponse(e)));
        Optional<DiaryEntry> older = diaryRepository.findFirstByFarmIdAndDateLessThanEqualOrderByDateDesc(
                farm.getId(), start.minusDays(1));
        List<DiaryDay> days = byDate.entrySet().stream().map(e -> new DiaryDay(e.getKey(), e.getValue())).toList();
        return new PageResponse<>(days, older.isPresent(), older.map(e -> e.getDate().toString()).orElse(null));
    }

    /** FARM-26 보기 */
    @Transactional(readOnly = true)
    DiaryEntryDetail get(Long userId, Long entryId) {
        FarmInfo farm = support.farm(userId);
        DiaryEntry entry = find(farm, entryId);
        var from = entry.getDate().atStartOfDay(FarmerRules.KST).toInstant();
        var to = entry.getDate().plusDays(SENT_DIARY_DAYS + 1L).atStartOfDay(FarmerRules.KST).toInstant();
        List<SentTo> sent = deliveryRepository.findSymptomDeliveries(farm.getId(), from, to).stream()
                .map(this::sentTo)
                .toList();
        return new DiaryEntryDetail(toResponse(entry), entry.getRawText(), mediaService.audioUrl(entry.getAudioMedia(), entry.getDate()), sent);
    }

    /** FARM-26 고치기. 서버가 자동으로 남긴 "신고" 기록은 고치지 않는다 */
    @Transactional
    DiaryEntryResponse update(Long userId, Long entryId, DiaryUpdateRequest request) {
        FarmInfo farm = support.farm(userId);
        DiaryEntry entry = find(farm, entryId);
        if ("신고".equals(entry.getCategory())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "신고 기록은 고칠 수 없어요.", "자동 기록");
        }
        LocalDate date = request.date() == null ? entry.getDate() : notFuture(request.date());
        entry.revise(date, request.category(), cow(farm, request.cowId()), request.value(), request.vaccineType(),
                request.headCount());
        return toResponse(entry);
    }

    /** FARM-26 지우기. 이미 전달된 기록은 받은 곳의 사본이 남는다 (화면에서 안내) */
    @Transactional
    void delete(Long userId, Long entryId) {
        diaryRepository.delete(find(support.farm(userId), entryId));
    }

    private SentTo sentTo(Delivery d) {
        var org = d.getOrg() != null ? d.getOrg() : d.getCcOrg();
        String status = receiverCheckRepository.findFirstByDeliveryIdOrderByAtDescIdDesc(d.getId())
                .map(c -> c.getStatus())
                .orElse("new");
        return new SentTo(d.getId(), d.getApprovedAt(), org == null ? null : org.getName(), status);
    }

    private DiaryEntry find(FarmInfo farm, Long entryId) {
        return diaryRepository.findByIdAndFarmId(entryId, farm.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "일지 기록 없음"));
    }

    private Cow cow(FarmInfo farm, Long cowId) {
        return cowId == null ? null : cowRepository.findByIdAndFarmId(cowId, farm.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATTLE_NOT_FOUND, "내 농장 소 아님"));
    }

    private static LocalDate notFuture(LocalDate date) {
        if (date.isAfter(FarmerRules.today())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "날짜를 다시 확인해 주세요.", "미래 날짜");
        }
        return date;
    }

    private static LocalDate parseCursor(String cursor) {
        try {
            return LocalDate.parse(cursor);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "cursor 형식 오류");
        }
    }

    static DiaryEntryResponse toResponse(DiaryEntry e) {
        Cow cow = e.getCow();
        return new DiaryEntryResponse(e.getId(), e.getDate(), e.getCategory(), cow == null ? null : cow.getId(),
                cow == null ? null : cow.getTagLast4(), e.getValue(), e.getVaccineType(), e.getHeadCount());
    }
}
