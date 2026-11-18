package com.mnm.api.farmer;

import com.mnm.api.domain.delivery.Delivery;
import com.mnm.api.domain.diary.DiaryEntry;
import com.mnm.api.domain.farm.Cow;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.publicdata.Holiday;
import com.mnm.api.domain.report.HistoryReport;
import com.mnm.api.domain.task.Task;
import com.mnm.api.farmer.HistoryReportDtos.CopyLine;
import com.mnm.api.farmer.HistoryReportDtos.CopyTextResponse;
import com.mnm.api.farmer.HistoryReportDtos.HistoryReportResponse;
import com.mnm.api.farmer.HistoryReportDtos.ReportCow;
import com.mnm.api.farmer.HistoryReportDtos.ReportFarm;
import com.mnm.api.farmer.HistoryReportDtos.ReportPatchRequest;
import com.mnm.api.farmer.HistoryReportDtos.SubmitResponse;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이력 신고서 초안·빈칸 채우기·직접 제출·축협 부탁 (F-06·F-07).
 * 이력제 시스템과 직접 연동하지 않는다. 직접 제출은 "항목 복사 → 신고 마침 표시"뿐이다.
 */
@Service
@RequiredArgsConstructor
class HistoryReportService {

    static final List<String> REPORT_TASK_KINDS = List.of("birth_report", "death_report", "move_report");
    static final String EAR_TAG_TASK = "ear_tag";

    private static final Map<String, List<String>> ALLOWED_FIELDS = Map.of(
            "출생", List.of("mother_tag4", "birth_date", "sex", "breed", "calf_tag"),
            "폐사", List.of("cow_tag", "death_date"),
            "이동", List.of("cow_tag", "move_date", "counterpart_farm_no", "counterpart_name"));
    private static final Set<String> TAG_FIELDS = Set.of("mother_tag4", "cow_tag");
    private static final Set<String> DATE_FIELDS = Set.of("birth_date", "death_date", "move_date");

    private final FarmerSupport support;
    private final FarmerHistoryReportRepository reportRepository;
    private final FarmerCowRepository cowRepository;
    private final FarmerTaskRepository taskRepository;
    private final FarmerHolidayRepository holidayRepository;
    private final FarmerDiaryRepository diaryRepository;
    private final FarmerDeliveryRepository deliveryRepository;

    /** 초안 만들기. 일지 저장(FARM-04)·소 목록에서 빼기(FARM-24)가 같은 트랜잭션 안에서 부른다 */
    HistoryReport createDraft(FarmInfo farm, String type, Map<String, Object> fields) {
        Map<String, Object> merged = merge(farm, type, new LinkedHashMap<>(), fields == null ? Map.of() : fields);
        List<String> missing = FarmerRules.missing(type, merged);
        LocalDate due = due(type, merged);
        HistoryReport report = reportRepository.save(
                HistoryReport.draft(farm, type, support.write(merged), support.write(missing), due));
        if (due != null) {
            taskRepository.save(Task.create(farm, taskKind(type), FarmerRules.taskTitle(type), due, report.getId()));
        }
        return report;
    }

    /** FARM-06 */
    @Transactional(readOnly = true)
    HistoryReportResponse get(Long userId, Long reportId) {
        FarmInfo farm = support.farm(userId);
        return toResponse(find(farm, reportId), farm);
    }

    /** FARM-07 빈칸 채우기. 초안일 때만 고칠 수 있다 */
    @Transactional
    HistoryReportResponse patch(Long userId, Long reportId, ReportPatchRequest request) {
        FarmInfo farm = support.farm(userId);
        HistoryReport report = find(farm, reportId);
        if (!report.isDraft()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 보낸 신고서는 고칠 수 없어요.", "초안 아님");
        }
        String type = report.getType();
        if (request.type() != null && !request.type().equals(type)) {
            if (!isMove(type)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "구분을 다시 골라 주세요.", "출생·폐사는 구분을 바꿀 수 없음");
            }
            type = request.type();
        }
        Map<String, Object> merged = merge(farm, type, support.readMap(report.getFields()), request.fields());
        List<String> missing = FarmerRules.missing(type, merged);
        LocalDate due = due(type, merged);
        if (!type.equals(report.getType())) {
            report.changeMoveType(type);
        }
        report.fillFields(support.write(merged), support.write(missing), due);
        syncTask(farm, report, due);
        return toResponse(report, farm);
    }

    /** FARM-08 직접 제출용 항목. 빈칸은 '(빈칸)'으로 보여준다 */
    @Transactional(readOnly = true)
    CopyTextResponse copyText(Long userId, Long reportId) {
        FarmInfo farm = support.farm(userId);
        HistoryReport report = find(farm, reportId);
        Map<String, Object> f = support.readMap(report.getFields());
        Map<String, Cow> cows = cowsByTag4(farm.getId());
        List<CopyLine> lines = new ArrayList<>();
        lines.add(new CopyLine("신고인", farm.getOwner().getName()));
        lines.add(new CopyLine("농장", farm.getName() + (farm.getAddress() == null ? "" : " (" + farm.getAddress() + ")")));
        lines.add(new CopyLine("농장식별번호", farm.getFarmNo()));
        switch (report.getType()) {
            case "출생" -> {
                Cow mother = cows.get(str(f.get("mother_tag4")));
                lines.add(new CopyLine("어미 소", cowLabel(mother, f.get("mother_tag4"))));
                lines.add(new CopyLine("태어난 날", koreanDate(f.get("birth_date"))));
                lines.add(new CopyLine("성별", value(f.get("sex"))));
                lines.add(new CopyLine("품종", value(f.get("breed"))));
                lines.add(new CopyLine("송아지 귀표", tagSpaced(str(f.get("calf_tag")))));
            }
            case "폐사" -> {
                Cow cow = cows.get(str(f.get("cow_tag")));
                lines.add(new CopyLine("개체식별번호", cowLabel(cow, f.get("cow_tag"))));
                lines.add(new CopyLine("폐사한 날", koreanDate(f.get("death_date"))));
                lines.add(new CopyLine("품종·성별", cow == null ? "(빈칸)" : value(cow.getBreed()) + " · " + value(cow.getSex())));
            }
            default -> {
                lines.add(new CopyLine("구분", report.getType()));
                lines.add(new CopyLine("개체식별번호", cowLabel(cows.get(str(f.get("cow_tag"))), f.get("cow_tag"))));
                lines.add(new CopyLine("날짜", koreanDate(f.get("move_date"))));
                lines.add(new CopyLine("상대 농장식별번호", value(f.get("counterpart_farm_no"))));
                lines.add(new CopyLine("상대방 성명", value(f.get("counterpart_name"))));
            }
        }
        String text = lines.stream().map(l -> l.label() + ": " + l.value()).collect(Collectors.joining("\n"));
        boolean canSubmit = support.readList(report.getMissing()).isEmpty();
        return new CopyTextResponse(report.getType() + " 신고서", lines, text, canSubmit);
    }

    /**
     * FARM-09. self: 빈칸이 없어야 하고, 신고 마침으로 표시 + 폐사·양도 소는 목록에서 빼기 + 출생이면 귀표 30일 할 일.
     * coop: 빈칸이 있어도 담당 축협 수신함으로 전달 (Idempotency-Key 필수). 둘 다 신고 할 일을 끝내고 일지에 "신고"를 남긴다.
     */
    @Transactional
    SubmitResponse submit(Long userId, Long reportId, String mode, String idempotencyKey) {
        FarmInfo farm = support.farm(userId);
        HistoryReport report = find(farm, reportId);
        if (!report.isDraft()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 보낸 신고서");
        }
        Instant now = Instant.now();
        LocalDate today = FarmerRules.today();
        Map<String, Object> fields = support.readMap(report.getFields());
        Map<String, Cow> cows = cowsByTag4(farm.getId());
        Cow subject = cows.get(str(fields.get("출생".equals(report.getType()) ? "mother_tag4" : "cow_tag")));
        String title = FarmerRules.taskTitle(report.getType());

        if ("self".equals(mode)) {
            if (!support.readList(report.getMissing()).isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "빈칸을 모두 채워야 직접 제출할 수 있어요. 축협에 부탁할 수도 있어요.", "빈칸 있음");
            }
            completeReportTasks(farm, report, now);
            report.finishSelf();
            if (("폐사".equals(report.getType()) || "양도".equals(report.getType())) && subject != null) {
                subject.remove();
            }
            LocalDate earTagDue = null;
            if ("출생".equals(report.getType()) && str(fields.get("calf_tag")) == null) {
                earTagDue = FarmerRules.earTagDue(today);
                taskRepository.save(Task.create(farm, EAR_TAG_TASK, "송아지 귀표 달기", earTagDue, report.getId()));
            }
            diaryRepository.save(DiaryEntry.record(farm, today, "신고", subject, title + " 마침 (직접 제출)", null, null, null));
            return new SubmitResponse(report.getId(), report.getStatus(), report.getMode(), null, null, earTagDue);
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Idempotency-Key 헤더 필요");
        }
        ReceiverOrg org = farm.getOrg();
        if (org == null) {
            throw new BusinessException(ErrorCode.DELIVER_FAIL, "담당 축협 없음");
        }
        completeReportTasks(farm, report, now);
        report.sendToCoop();
        Delivery delivery = deliveryRepository.save(
                Delivery.approve(null, report, "coop", org, null, true, idempotencyKey, now, "delivered"));
        diaryRepository.save(DiaryEntry.record(farm, today, "신고", subject, title + " 축협 부탁 · " + org.getName(),
                null, null, null));
        return new SubmitResponse(report.getId(), report.getStatus(), report.getMode(), delivery.getId(), org.getName(),
                null);
    }

    HistoryReportResponse toResponse(HistoryReport report, FarmInfo farm) {
        Map<String, Object> fields = support.readMap(report.getFields());
        List<String> missing = support.readList(report.getMissing());
        Map<String, Cow> cows = cowsByTag4(farm.getId());
        List<ReportCow> reportCows = new ArrayList<>();
        for (String field : TAG_FIELDS.stream().sorted().toList()) {
            Cow cow = cows.get(str(fields.get(field)));
            if (cow != null) {
                reportCows.add(new ReportCow(field, cow.getId(), cow.getTagLast4(), cow.getTagFull(), cow.getBreed(),
                        cow.getSex(), cow.getBirthDate(), FarmerSupport.age(cow.getBirthDate())));
            }
        }
        LocalDate due = report.getDueDate();
        return new HistoryReportResponse(report.getId(), report.getType(), fields, missing,
                missing.isEmpty() ? null : FarmerRules.question(missing.getFirst()), missing.isEmpty(), due,
                due == null ? null : ChronoUnit.DAYS.between(FarmerRules.today(), due), report.getMode(),
                report.getStatus(), reportCows,
                new ReportFarm(farm.getOwner().getName(), farm.getName(), farm.getFarmNo(), farm.getAddress(),
                        farm.getOrg() == null ? null : farm.getOrg().getName()));
    }

    private HistoryReport find(FarmInfo farm, Long reportId) {
        return reportRepository.findByIdAndFarmId(reportId, farm.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신고서 없음"));
    }

    /** 바뀐 칸을 검사해 합친다. 알 수 없는 칸·형식이 틀린 값은 VALID-422 */
    private Map<String, Object> merge(FarmInfo farm, String type, Map<String, Object> base, Map<String, Object> changes) {
        List<String> allowed = ALLOWED_FIELDS.get(isMove(type) ? "이동" : type);
        Map<String, Cow> cows = null;
        for (Map.Entry<String, Object> e : changes.entrySet()) {
            String key = e.getKey();
            if (!allowed.contains(key)) {
                throw invalid("입력한 내용을 다시 확인해 주세요.", "알 수 없는 칸: " + key);
            }
            String value = str(e.getValue());
            if (value == null) {
                base.put(key, null);
                continue;
            }
            if (TAG_FIELDS.contains(key)) {
                if (cows == null) {
                    cows = cowsByTag4(farm.getId());
                }
                base.put(key, tag(value, cows, "양수".equals(type)));
            } else if (DATE_FIELDS.contains(key)) {
                base.put(key, date(value).toString());
            } else if ("sex".equals(key)) {
                base.put(key, check(value, "암|수", "성별을 골라 주세요."));
            } else if ("calf_tag".equals(key)) {
                base.put(key, check(value.replaceAll("\\s|-", ""), "\\d{12}", "귀표 12자리를 넣어 주세요."));
            } else if ("counterpart_farm_no".equals(key)) {
                base.put(key, check(value, "\\d{6}", "농장식별번호 6자리를 넣어 주세요."));
            } else {
                base.put(key, value.length() > 50 ? value.substring(0, 50) : value);
            }
        }
        // 송아지 품종을 따로 말하지 않았으면 어미 소의 이력제 품종으로 채운다
        if ("출생".equals(type) && str(base.get("breed")) == null && str(base.get("mother_tag4")) != null) {
            Cow mother = (cows == null ? cowsByTag4(farm.getId()) : cows).get(str(base.get("mother_tag4")));
            if (mother != null && mother.getBreed() != null) {
                base.put("breed", mother.getBreed());
            }
        }
        return base;
    }

    /** 소 칸은 내 소 목록(뺀 소 포함)의 끝 4자리로 저장한다. 양수는 아직 목록에 없으므로 4자리·12자리 그대로 */
    private static String tag(String value, Map<String, Cow> cows, boolean bought) {
        String digits = value.replaceAll("\\s|-", "");
        if (bought) {
            return check(digits, "\\d{4}|\\d{12}", "귀표 번호를 다시 확인해 주세요.");
        }
        String tag4 = digits.length() == 12 ? digits.substring(8) : digits;
        if (!tag4.matches("\\d{4}") || !cows.containsKey(tag4)) {
            throw invalid("목록에 없는 번호예요. 번호를 다시 확인해 주세요.", "소 목록에 없는 귀표");
        }
        return tag4;
    }

    private static LocalDate date(String value) {
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.isAfter(FarmerRules.today())) {
                throw invalid("날짜를 다시 확인해 주세요.", "미래 날짜");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw invalid("날짜를 다시 확인해 주세요.", "날짜 형식 오류");
        }
    }

    private static String check(String value, String regex, String message) {
        if (!value.matches(regex)) {
            throw invalid(message, "형식 오류");
        }
        return value;
    }

    private static BusinessException invalid(String message, String detail) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message, detail);
    }

    /** 발생일이 있으면 신고 기한 (토·일·공휴일 제외 5일) */
    private LocalDate due(String type, Map<String, Object> fields) {
        String event = str(fields.get(FarmerRules.eventDateField(type)));
        if (event == null) {
            return null;
        }
        LocalDate eventDate = LocalDate.parse(event);
        Set<LocalDate> holidays = holidayRepository.findByDateBetween(eventDate, eventDate.plusDays(30)).stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());
        return FarmerRules.reportDue(eventDate, holidays);
    }

    private void completeReportTasks(FarmInfo farm, HistoryReport report, Instant now) {
        taskRepository.findByFarmIdAndKindInAndRefIdAndDoneAtIsNull(farm.getId(), REPORT_TASK_KINDS, report.getId())
                .forEach(t -> t.complete(now));
    }

    /** 신고 기한 할 일을 기한에 맞춘다 (없으면 만들고, 기한이 없어지면 끝낸다) */
    private void syncTask(FarmInfo farm, HistoryReport report, LocalDate due) {
        List<Task> tasks = taskRepository.findByFarmIdAndKindInAndRefIdAndDoneAtIsNull(farm.getId(), REPORT_TASK_KINDS,
                report.getId());
        if (due == null) {
            tasks.forEach(t -> t.complete(Instant.now()));
        } else if (tasks.isEmpty()) {
            taskRepository.save(Task.create(farm, taskKind(report.getType()), FarmerRules.taskTitle(report.getType()),
                    due, report.getId()));
        } else {
            tasks.forEach(t -> t.reschedule(due));
        }
    }

    /** 끝 4자리 → 소. 같은 끝 4자리가 여럿이면 목록에 있는 소를 먼저 */
    Map<String, Cow> cowsByTag4(Long farmId) {
        return cowRepository.findByFarmId(farmId).stream()
                .filter(c -> c.getTagLast4() != null)
                .sorted(Comparator.comparing(Cow::isActive))
                .collect(Collectors.toMap(Cow::getTagLast4, c -> c, (a, b) -> b, LinkedHashMap::new));
    }

    static String taskKind(String type) {
        return switch (type) {
            case "출생" -> "birth_report";
            case "폐사" -> "death_report";
            default -> "move_report";
        };
    }

    private static boolean isMove(String type) {
        return "양도".equals(type) || "양수".equals(type) || "이동".equals(type);
    }

    private static String cowLabel(Cow cow, Object tag) {
        if (cow != null && cow.getTagFull() != null) {
            return tagSpaced(cow.getTagFull());
        }
        String t = str(tag);
        return t == null ? "(빈칸)" : t.length() == 12 ? tagSpaced(t) : t + "번";
    }

    /** 귀표 12자리를 3-4-4-1 로 띄어 쓴다 */
    private static String tagSpaced(String tag) {
        if (tag == null) {
            return "(빈칸)";
        }
        return tag.substring(0, 3) + " " + tag.substring(3, 7) + " " + tag.substring(7, 11) + " " + tag.substring(11);
    }

    private static String koreanDate(Object value) {
        String v = str(value);
        if (v == null) {
            return "(빈칸)";
        }
        LocalDate d = LocalDate.parse(v);
        return d.getYear() + "년 " + d.getMonthValue() + "월 " + d.getDayOfMonth() + "일";
    }

    private static String value(Object value) {
        String v = str(value);
        return v == null ? "(빈칸)" : v;
    }

    static String str(Object value) {
        return Optional.ofNullable(value).map(Object::toString).filter(s -> !s.isBlank()).orElse(null);
    }
}
