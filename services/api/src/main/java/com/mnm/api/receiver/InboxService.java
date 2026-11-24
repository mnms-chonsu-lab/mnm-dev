package com.mnm.api.receiver;

import static com.mnm.api.receiver.ReceiverRules.CHECKING;
import static com.mnm.api.receiver.ReceiverRules.DONE;
import static com.mnm.api.receiver.ReceiverRules.NEW;

import com.mnm.api.domain.alert.Alert;
import com.mnm.api.domain.delivery.Delivery;
import com.mnm.api.domain.delivery.ReceiverCheck;
import com.mnm.api.domain.diary.DiaryEntry;
import com.mnm.api.domain.farm.Cow;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.guide.BiosecurityRule;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.report.HistoryReport;
import com.mnm.api.domain.report.ReportSummary;
import com.mnm.api.domain.symptom.BehaviorRecord;
import com.mnm.api.domain.symptom.FieldMedia;
import com.mnm.api.domain.symptom.SymptomReport;
import com.mnm.api.domain.task.Task;
import com.mnm.api.domain.user.User;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import com.mnm.api.global.storage.StorageClient;
import com.mnm.api.receiver.InboxDtos.CowPart;
import com.mnm.api.receiver.InboxDtos.DiaryItem;
import com.mnm.api.receiver.InboxDtos.FarmBasics;
import com.mnm.api.receiver.InboxDtos.FarmPart;
import com.mnm.api.receiver.InboxDtos.Form6Item;
import com.mnm.api.receiver.InboxDtos.Form6Response;
import com.mnm.api.receiver.InboxDtos.GuideLink;
import com.mnm.api.receiver.InboxDtos.HistoryPart;
import com.mnm.api.receiver.InboxDtos.InboxDetail;
import com.mnm.api.receiver.InboxDtos.InboxItem;
import com.mnm.api.receiver.InboxDtos.InboxResponse;
import com.mnm.api.receiver.InboxDtos.MemoRequest;
import com.mnm.api.receiver.InboxDtos.Outbreak;
import com.mnm.api.receiver.InboxDtos.PhotoPart;
import com.mnm.api.receiver.InboxDtos.ProxyRequest;
import com.mnm.api.receiver.InboxDtos.ProxyResponse;
import com.mnm.api.receiver.InboxDtos.StatusRequest;
import com.mnm.api.receiver.InboxDtos.StatusResponse;
import com.mnm.api.receiver.InboxDtos.SymptomPart;
import com.mnm.api.receiver.InboxDtos.TimelineItem;
import com.mnm.api.receiver.InboxDtos.VideoPart;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접수 목록 · 한 장 요약 · 상태 변경 · 메모 · 신고서 6개 항목 · 이력 신고 대행 (R-01~05, R-09).
 * 농가가 승인해 우리 기관에 도착한 전달만 보여준다. 열람·변경은 감사 기록을 남긴다 (COM-07).
 */
@Service
@RequiredArgsConstructor
class InboxService {

    static final int OUTBREAK_DAYS = 30;
    static final int DIARY_DAYS = 14;
    static final String EAR_TAG_TASK = "ear_tag";
    private static final Set<String> FILTERS = Set.of("all", NEW, CHECKING, DONE);
    private static final String PUBLISHED = "published";

    private final ReceiverSupport support;
    private final ReceiverDeliveryRepository deliveryRepository;
    private final ReceiverCheckRepository checkRepository;
    private final ReceiverAlertRepository alertRepository;
    private final ReceiverDiaryRepository diaryRepository;
    private final ReceiverMediaRepository mediaRepository;
    private final ReceiverBehaviorRepository behaviorRepository;
    private final StorageClient storage;
    private final ReceiverRuleRepository ruleRepository;
    private final ReceiverOutbreakRepository outbreakRepository;
    private final ReceiverTaskRepository taskRepository;
    private final ReceiverCowRepository cowRepository;

    /** RCV-01 관할 접수 목록 (최신순 20건씩) + 상태별 개수 + 관할 최근 30일 발생 */
    @Transactional(readOnly = true)
    InboxResponse list(Long userId, String status, String cursor) {
        ReceiverOrg org = support.receiver(userId).getOrg();
        String filter = status == null || status.isBlank() ? "all" : status;
        if (!FILTERS.contains(filter)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "status 값 오류");
        }
        List<Delivery> rows = deliveryRepository.findInbox(org.getId(), filter, ReceiverSupport.cursor(cursor),
                PageRequest.of(0, ReceiverSupport.PAGE_SIZE + 1));
        Map<Long, ReceiverCheck> latest = latest(rows.stream().map(Delivery::getId).toList());
        PageResponse<InboxItem> page = ReceiverSupport.page(rows, ReceiverSupport.PAGE_SIZE,
                d -> item(d, latest.get(d.getId())), Delivery::getId);
        return new InboxResponse(counts(org), outbreak(org), page);
    }

    /** RCV-02 한 장 요약. 열면 바로 감사 기록을 남긴다 (그래서 쓰기 트랜잭션, 동시 열람 중복을 막으려 행을 잠금) */
    @Transactional
    InboxDetail detail(Long userId, Long deliveryId) {
        User me = support.receiver(userId);
        Delivery d = findForUpdate(deliveryId, me.getOrg());
        support.auditView(me, "delivery", d.getId());
        ReceiverCheck check = checkRepository.findFirstByDeliveryIdOrderByIdDesc(d.getId()).orElse(null);
        FarmInfo farm = farmOf(d);
        User owner = farm.getOwner();
        FarmPart farmPart = new FarmPart(farm.getId(), farm.getFarmNo(), farm.getName(), owner.getName(),
                farm.getAddress(), farm.getHeadCount(), d.isSharePhone(), d.isSharePhone() ? owner.getPhone() : null);

        SymptomPart symptom = null;
        HistoryPart history = null;
        List<GuideLink> guides = List.of();
        if (d.getHistoryReport() != null) {
            history = toHistory(d.getHistoryReport());
        } else {
            SymptomReport sr = d.getReportSummary().getSymptomReport();
            Map<String, Object> answers = support.readMap(sr.getAnswers());
            Cow cow = sr.getCow();
            List<PhotoPart> photos = FieldMedia.latestPerPart(
                            mediaRepository.findBySymptomReportIdAndKindOrderByIdAsc(sr.getId(), "photo"))
                    .stream()
                    .map(m -> new PhotoPart(m.getId(), m.getRequestedPart(), m.getPredictedPart(), m.getMatch(), url(m)))
                    .toList();
            List<FieldMedia> videos = mediaRepository.findBySymptomReportIdAndKindOrderByIdAsc(sr.getId(), "video");
            VideoPart video = videos.isEmpty() ? null : new VideoPart(videos.getLast().getId(), url(videos.getLast()),
                    BehaviorRecord.ratio(behaviorRepository.findByMediaId(videos.getLast().getId())));
            String card = d.getReportSummary().getCardJson();
            symptom = new SymptomPart(sr.getId(),
                    cow == null ? null : new CowPart(cow.getTagLast4(), cow.getTagFull(), cow.getBreed(), cow.getSex(),
                            cow.getBirthDate()),
                    ReceiverRules.matchedSymptoms(answers),
                    sr.getMatchedCount() == null ? null : sr.getMatchedCount().intValue(), sr.getFoundAt(),
                    sr.getBarn(), photos, video, card == null ? null : support.readMap(card));
            guides = guides(ReceiverRules.disease(answers));
        }
        FarmBasics basics = new FarmBasics(latestDiary(farm, "접종"), latestDiary(farm, "소독"),
                latestDiary(farm, "출입"));
        List<DiaryItem> diary = diaryRepository
                .findByFarmIdAndDateGreaterThanEqualOrderByDateDescIdDesc(farm.getId(),
                        ReceiverRules.today().minusDays(DIARY_DAYS - 1))
                .stream().map(InboxService::toDiary).toList();
        List<TimelineItem> timeline = checkRepository.findByDeliveryIdOrderByIdAsc(d.getId()).stream()
                .map(c -> new TimelineItem(c.getStatus(), c.getResult(), c.getMemo(), c.getAt(), c.getUser().getName(),
                        c.getOrg() == null ? null : c.getOrg().getName()))
                .toList();
        return new InboxDetail(d.getId(), kind(d), status(check), check == null ? null : check.getResult(),
                d.getRoute(), d.getApprovedAt(), d.getReceivedAt(), farmPart, symptom, history, basics, diary,
                timeline, guides);
    }

    /**
     * RCV-03 상태 변경 · 처리 완료 · 다시 열기. 화면에서 보던 상태와 지금 상태가 다르면 CONFLICT-409
     * (다른 담당자가 먼저 처리함). 바뀌면 농가 알림함에 처리 상태를 넣는다 (F-14).
     */
    @Transactional
    StatusResponse changeStatus(Long userId, Long deliveryId, StatusRequest request) {
        User me = support.receiver(userId);
        Delivery d = findForUpdate(deliveryId, me.getOrg());
        String current = status(checkRepository.findFirstByDeliveryIdOrderByIdDesc(d.getId()).orElse(null));
        if (!current.equals(request.expectedStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "다른 담당자가 먼저 상태를 바꿈");
        }
        String next = request.status();
        ReceiverRules.checkTransition(current, next, d.getHistoryReport() != null);
        if (DONE.equals(next) && request.result() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "처리 결과를 골라 주세요.", "result 필요");
        }
        String result = DONE.equals(next) ? request.result() : null;
        ReceiverCheck saved = checkRepository.save(
                ReceiverCheck.record(d, me.getOrg(), me, next, result, blankToNull(request.memo())));
        if (d.getHistoryReport() != null) {
            d.getHistoryReport().startCoopCheck();
        }
        notifyFarmer(d, me.getOrg(), current, next, result);
        support.audit(me, "update", "delivery", d.getId());
        return new StatusResponse(d.getId(), next, result, saved.getAt());
    }

    /** RCV-04 통화·처리 메모. 새로 도착한 기록이면 확인 중으로 바꾼다 */
    @Transactional
    StatusResponse addMemo(Long userId, Long deliveryId, MemoRequest request) {
        User me = support.receiver(userId);
        Delivery d = findForUpdate(deliveryId, me.getOrg());
        ReceiverCheck latest = checkRepository.findFirstByDeliveryIdOrderByIdDesc(d.getId()).orElse(null);
        String current = status(latest);
        String next = NEW.equals(current) ? CHECKING : current;
        String result = latest == null ? null : latest.getResult();
        ReceiverCheck saved = checkRepository.save(
                ReceiverCheck.record(d, me.getOrg(), me, next, result, request.memo().strip()));
        if (!next.equals(current)) {
            if (d.getHistoryReport() != null) {
                d.getHistoryReport().startCoopCheck();
            }
            notifyFarmer(d, me.getOrg(), current, next, null);
        }
        support.audit(me, "update", "delivery", d.getId());
        return new StatusResponse(d.getId(), next, result, saved.getAt());
    }

    /**
     * RCV-05 신고서 6개 항목 (시행규칙 제13조). 승인 때 만든 form6 을 쓰고, 없으면 기록값으로 채운다.
     * 질병명 칸은 "발견 당시 상태"로만 채운다 (추정 병명 없음)
     */
    @Transactional
    Form6Response form6(Long userId, Long deliveryId) {
        User me = support.receiver(userId);
        Delivery d = find(deliveryId, me.getOrg());
        if (d.getReportSummary() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "이력 신고서에는 6개 항목이 없음");
        }
        support.auditView(me, "form6", d.getId());
        ReportSummary summary = d.getReportSummary();
        Map<String, Object> form6 = summary.getForm6Json() == null ? fallbackForm6(summary.getSymptomReport())
                : support.readMap(summary.getForm6Json());
        List<Form6Item> items = ReceiverRules.FORM6_LABELS.entrySet().stream()
                .map(e -> new Form6Item(e.getKey(), e.getValue(), str(form6.get(e.getKey()))))
                .toList();
        List<String> missing = items.stream().filter(i -> i.value() == null).map(Form6Item::key).toList();
        return new Form6Response(d.getId(), items, missing, ReceiverRules.form6Text(form6));
    }

    /**
     * RCV-10 이력 신고 초안: 빈칸 채우기 · 대신 신고 마침. 마치면 신고서 완료, 농가 알림 "축협이 대신 신고했어요",
     * 출생이면 송아지 귀표 30일 할 일을 만든다 (송아지 귀표를 아직 안 적었을 때).
     */
    @Transactional
    ProxyResponse proxyDone(Long userId, Long historyReportId, ProxyRequest request) {
        User me = support.receiver(userId);
        Delivery found = deliveryRepository.findFirstByHistoryReportIdOrderByIdDesc(historyReportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "이력 신고서 없음"));
        Delivery d = findForUpdate(found.getId(), me.getOrg());
        HistoryReport report = d.getHistoryReport();
        if (report.isDone()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 신고 마침");
        }
        String current = status(checkRepository.findFirstByDeliveryIdOrderByIdDesc(d.getId()).orElse(null));
        Map<String, String> changes = request.fields() == null ? Map.of() : request.fields();
        List<String> missing = new ArrayList<>(support.readList(report.getMissing()));
        if (!changes.isEmpty()) {
            Map<String, Object> fields = support.readMap(report.getFields());
            fill(report.getType(), fields, missing, changes);
            // shortcut: 빈 발생일을 채워도 신고 기한은 다시 계산하지 않는다 (공휴일 규칙이 농가 쪽에만 있음), 필요하면 공통 규칙으로 옮긴다
            report.fillFields(support.write(fields), support.write(missing), report.getDueDate());
        }
        FarmInfo farm = report.getFarm();
        String next = current;
        LocalDate earTagDue = null;
        if (request.done()) {
            if (!missing.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "빈칸을 모두 채워야 신고 마침으로 바꿀 수 있어요.",
                        "빈칸 있음");
            }
            next = DONE;
            report.finishByCoop();
            checkRepository.save(ReceiverCheck.record(d, me.getOrg(), me, DONE, ReceiverRules.PROXY_REPORTED, null));
            Map<String, Object> fields = support.readMap(report.getFields());
            String tag4 = str(fields.get("출생".equals(report.getType()) ? "mother_tag4" : "cow_tag"));
            Cow subject = tag4 == null ? null
                    : cowRepository.findFirstByFarmIdAndTagLast4AndStatus(farm.getId(), tag4, "active").orElse(null);
            if (("폐사".equals(report.getType()) || "양도".equals(report.getType())) && subject != null) {
                subject.remove();
            }
            LocalDate today = ReceiverRules.today();
            if ("출생".equals(report.getType()) && str(fields.get("calf_tag")) == null) {
                earTagDue = today.plusDays(30); // 계산식 ① 귀표 기한 = 출생 신고일 + 30일
                taskRepository.save(Task.create(farm, EAR_TAG_TASK, "송아지 귀표 달기", earTagDue, report.getId()));
            }
            diaryRepository.save(DiaryEntry.record(farm, today, "신고", subject,
                    report.getType() + " 신고 마침 (축협 대신 신고)", null, null, null));
            alertRepository.save(Alert.create(farm.getOwner(), "result", "축협이 대신 신고했어요",
                    report.getType() + " 신고를 이력제에 마쳤어요.", d.getId()));
        } else if (NEW.equals(current) && !changes.isEmpty()) {
            next = CHECKING;
            checkRepository.save(ReceiverCheck.record(d, me.getOrg(), me, CHECKING, null, null));
            report.startCoopCheck();
            notifyFarmer(d, me.getOrg(), current, next, null);
        }
        support.audit(me, "update", "history_report", report.getId());
        return new ProxyResponse(d.getId(), toHistory(report), next, earTagDue);
    }

    /** 농가가 비워 둔 칸과 출생의 송아지 귀표만 채운다. 농가가 적은 값은 바꾸지 않는다 */
    private static void fill(String type, Map<String, Object> fields, List<String> missing,
            Map<String, String> changes) {
        Set<String> allowed = new HashSet<>(missing);
        if ("출생".equals(type)) {
            allowed.add("calf_tag");
        }
        for (Map.Entry<String, String> e : changes.entrySet()) {
            String key = e.getKey();
            if (!allowed.contains(key)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "채울 수 없는 칸: " + key);
            }
            String value = blankToNull(e.getValue());
            if (value != null && value.length() > 100) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "입력한 내용을 다시 확인해 주세요.", "값이 너무 김: " + key);
            }
            if (value != null && key.endsWith("_date")) {
                try {
                    LocalDate.parse(value);
                } catch (DateTimeParseException ex) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "날짜를 다시 확인해 주세요.", "날짜 형식 오류: " + key);
                }
            }
            fields.put(key, value);
            if (value != null) {
                missing.remove(key);
            }
        }
    }

    /** 처리 상태를 농가 알림함에 넣는다 (kind=result, ref_id=전달 번호) */
    private void notifyFarmer(Delivery d, ReceiverOrg org, String previous, String next, String result) {
        String title = DONE.equals(next) ? org.getName() + "에서 처리 결과를 남겼어요"
                : DONE.equals(previous) ? org.getName() + "에서 기록을 다시 확인하고 있어요"
                        : org.getName() + "에서 보낸 기록을 확인하고 있어요";
        String body = DONE.equals(next) ? ReceiverRules.RESULTS.get(result) : null;
        alertRepository.save(Alert.create(farmOf(d).getOwner(), "result", title, body, d.getId()));
    }

    private Delivery find(Long deliveryId, ReceiverOrg org) {
        Delivery d = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "전달 없음"));
        ReceiverSupport.checkInbox(d, org);
        return d;
    }

    private Delivery findForUpdate(Long deliveryId, ReceiverOrg org) {
        Delivery d = deliveryRepository.findForUpdate(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "전달 없음"));
        ReceiverSupport.checkInbox(d, org);
        return d;
    }

    private Map<Long, ReceiverCheck> latest(Collection<Long> deliveryIds) {
        if (deliveryIds.isEmpty()) {
            return Map.of();
        }
        return checkRepository.findLatest(deliveryIds).stream()
                .collect(Collectors.toMap(c -> c.getDelivery().getId(), Function.identity()));
    }

    private Map<String, Long> counts(ReceiverOrg org) {
        long all = deliveryRepository.countInbox(org.getId());
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : checkRepository.countLatestByStatus(org.getId())) {
            byStatus.put((String) row[0], (Long) row[1]);
        }
        long checking = byStatus.getOrDefault(CHECKING, 0L);
        long done = byStatus.getOrDefault(DONE, 0L);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", all);
        counts.put(NEW, all - checking - done);
        counts.put(CHECKING, checking);
        counts.put(DONE, done);
        return counts;
    }

    private Outbreak outbreak(ReceiverOrg org) {
        Set<String> sigungu = ReceiverSupport.sigungu(org);
        if (sigungu.isEmpty()) {
            return new Outbreak(0, 0, outbreakRepository.lastCollectedAt());
        }
        LocalDate since = ReceiverRules.today().minusDays(OUTBREAK_DAYS);
        return new Outbreak(outbreakRepository.countRecent(sigungu, "구제역", since),
                outbreakRepository.countRecent(sigungu, "럼피스킨", since), outbreakRepository.lastCollectedAt());
    }

    private static InboxItem item(Delivery d, ReceiverCheck check) {
        if (d.getHistoryReport() != null) {
            HistoryReport h = d.getHistoryReport();
            FarmInfo farm = h.getFarm();
            return new InboxItem(d.getId(), "history", status(check), check == null ? null : check.getResult(),
                    farm.getName(), farm.getAddress(), farm.getRegionCode(), null, null, h.getType(), h.getId(),
                    h.getDueDate(), d.getRoute(), d.getApprovedAt(), d.getReceivedAt());
        }
        SymptomReport sr = d.getReportSummary().getSymptomReport();
        FarmInfo farm = sr.getFarm();
        return new InboxItem(d.getId(), "symptom", status(check), check == null ? null : check.getResult(),
                farm.getName(), farm.getAddress(), farm.getRegionCode(),
                sr.getCow() == null ? null : sr.getCow().getTagLast4(),
                sr.getMatchedCount() == null ? null : sr.getMatchedCount().intValue(), null, null, null, d.getRoute(),
                d.getApprovedAt(), d.getReceivedAt());
    }

    /** 관련 공식 지침: 질병 분기에 맞는 게시된 지침 요약 + 질병 구분 없는 법령 요약. 문서별 1건 */
    private List<GuideLink> guides(String disease) {
        Map<String, LocalDate> docs = new LinkedHashMap<>();
        for (BiosecurityRule r : ruleRepository.findByKindAndStatusOrderByDisplayOrderAscIdAsc("summary", PUBLISHED)) {
            if (r.getDisease() == null || disease != null && (r.getDisease().equalsIgnoreCase(disease)
                    || r.getDisease().startsWith(ReceiverRules.DISEASE_NAMES.get(disease)))) {
                LocalDate prev = docs.get(r.getSourceDoc());
                LocalDate revised = r.getRevisedAt();
                if (!docs.containsKey(r.getSourceDoc()) || revised != null && (prev == null || revised.isAfter(prev))) {
                    docs.put(r.getSourceDoc(), revised); // 개정일이 없는 행도 문서는 보여준다 (HashMap.merge 는 null 불가)
                }
            }
        }
        List<GuideLink> links = new ArrayList<>();
        docs.forEach((doc, revised) -> links.add(new GuideLink(doc, revised)));
        return links;
    }

    /** AI 요약(form6)이 없을 때 기록값으로 채운 6개 항목 (규칙만, 값이 없으면 빈칸) */
    private Map<String, Object> fallbackForm6(SymptomReport sr) {
        FarmInfo farm = sr.getFarm();
        String owner = farm.getOwner().getName();
        Cow cow = sr.getCow();
        List<String> matched = ReceiverRules.matchedSymptoms(support.readMap(sr.getAnswers()));
        String species = (cow == null || cow.getBreed() == null ? "한우" : cow.getBreed()) + " 1두"
                + (cow == null ? "" : " (" + cow.getTagLast4() + "번)")
                + (farm.getHeadCount() == null ? "" : " / 전체 " + farm.getHeadCount() + "두");
        Map<String, Object> form6 = new LinkedHashMap<>();
        form6.put("owner_and_location", join(owner, farm.getAddress(), sr.getBarn()));
        form6.put("species_and_head_count", species);
        form6.put("condition", matched.isEmpty() ? null : "발견 당시 상태: " + String.join(", ", matched));
        form6.put("found_date", sr.getFoundAt() == null ? null
                : LocalDate.ofInstant(sr.getFoundAt(), ReceiverRules.KST).toString());
        form6.put("reporter", join(owner, farm.getAddress()));
        return form6;
    }

    private DiaryItem latestDiary(FarmInfo farm, String category) {
        return diaryRepository.findFirstByFarmIdAndCategoryOrderByDateDescIdDesc(farm.getId(), category)
                .map(InboxService::toDiary).orElse(null);
    }

    private static DiaryItem toDiary(DiaryEntry e) {
        return new DiaryItem(e.getDate(), e.getCategory(), e.getValue(), e.getVaccineType(),
                e.getCow() == null ? null : e.getCow().getTagLast4());
    }

    private HistoryPart toHistory(HistoryReport h) {
        LocalDate due = h.getDueDate();
        return new HistoryPart(h.getId(), h.getType(), support.readMap(h.getFields()), support.readList(h.getMissing()),
                due, due == null ? null : ChronoUnit.DAYS.between(ReceiverRules.today(), due), h.getStatus());
    }

    private static FarmInfo farmOf(Delivery d) {
        return d.getHistoryReport() != null ? d.getHistoryReport().getFarm()
                : d.getReportSummary().getSymptomReport().getFarm();
    }

    private static String kind(Delivery d) {
        return d.getHistoryReport() != null ? "history" : "symptom";
    }

    private static String status(ReceiverCheck check) {
        return check == null ? NEW : check.getStatus();
    }

    private static String str(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String join(String... parts) {
        String joined = Stream.of(parts).filter(Objects::nonNull).filter(p -> !p.isBlank())
                .collect(Collectors.joining(" · "));
        return joined.isEmpty() ? null : joined;
    }

    /** 원본 열람 서명 URL (만료 10분, REQ-OPS-07). 아직 올라오지 않은 파일은 null */
    private String url(FieldMedia media) {
        return FieldMedia.PENDING_PATH.equals(media.getFilePath()) ? null
                : storage.presignGet(media.getFilePath(), StorageClient.VIEW_TTL);
    }
}
