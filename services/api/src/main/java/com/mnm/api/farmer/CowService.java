package com.mnm.api.farmer;

import com.mnm.api.domain.farm.Cow;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.report.HistoryReport;
import com.mnm.api.farmer.CowDtos.CowRegisterRequest;
import com.mnm.api.farmer.CowDtos.CowResponse;
import com.mnm.api.farmer.CowDtos.CowUpdateRequest;
import com.mnm.api.farmer.CowDtos.CowUpdateResponse;
import com.mnm.api.farmer.CowDtos.LookupResponse;
import com.mnm.api.farmer.TraceClient.Status;
import com.mnm.api.farmer.TraceClient.TraceResult;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 소 목록·이력제 조회·등록·축사 수정·목록에서 빼기 (F-17) */
@Service
@RequiredArgsConstructor
class CowService {

    private final FarmerSupport support;
    private final TraceClient traceClient;
    private final FarmerCowRepository cowRepository;
    private final FarmerDiaryRepository diaryRepository;
    private final HistoryReportService historyReportService;

    /** FARM-02 목록에 있는 소. 최근 일지가 있는 소 먼저, 그다음 끝 4자리 순 */
    @Transactional(readOnly = true)
    PageResponse<CowResponse> list(Long userId) {
        FarmInfo farm = support.farm(userId);
        Map<Long, LocalDate> latest = new HashMap<>();
        for (Object[] row : diaryRepository.latestDateByCow(farm.getId())) {
            latest.put((Long) row[0], (LocalDate) row[1]);
        }
        List<CowResponse> cows = cowRepository.findByFarmIdAndStatus(farm.getId(), "active").stream()
                .sorted(Comparator.<Cow, LocalDate>comparing(c -> latest.get(c.getId()),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Cow::getTagLast4, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CowService::toResponse)
                .toList();
        return new PageResponse<>(cows, false, null);
    }

    /** FARM-22 귀표 12자리 → 이력제 조회. 못 찾음 CATTLE-NOTFOUND · 조회 실패 TRACE-API-FAIL · 다른 농장 CATTLE-OTHER-FARM */
    @Transactional(readOnly = true)
    LookupResponse lookup(Long userId, String tagNo) {
        FarmInfo farm = support.farm(userId);
        String tag = tag(tagNo);
        TraceResult trace = found(traceClient.lookup(tag));
        if (isOtherFarm(trace, farm)) {
            throw new BusinessException(ErrorCode.CATTLE_OTHER_FARM, "이력제 농장식별번호가 다름");
        }
        boolean registered = cowRepository.findByFarmIdAndTagFull(farm.getId(), tag).filter(Cow::isActive).isPresent();
        return new LookupResponse(tag, trace.breed(), trace.sex(), trace.birthDate(), trace.vaccineLastDate(),
                trace.vaccineLastOrder(), registered);
    }

    /** FARM-23 등록. 서버가 이력제를 다시 조회해 값을 넣는다 (화면에서 보낸 값으로 이력제 값을 덮지 않음) */
    @Transactional
    CowResponse register(Long userId, CowRegisterRequest request) {
        FarmInfo farm = support.farm(userId);
        String tag = tag(request.tagNo());
        Optional<Cow> existing = cowRepository.findByFarmIdAndTagFull(farm.getId(), tag);
        if (existing.filter(Cow::isActive).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 등록된 소예요.", "같은 귀표 등록됨");
        }
        String barn = blankToNull(request.barn());
        TraceResult trace = traceClient.lookup(tag);
        Cow cow;
        if (trace.status() == Status.FOUND) {
            if (isOtherFarm(trace, farm) && !request.otherFarmConfirmed()) {
                throw new BusinessException(ErrorCode.CATTLE_OTHER_FARM, "확인 전");
            }
            Instant now = Instant.now();
            cow = existing.map(c -> {
                c.reRegister(trace.breed(), trace.sex(), trace.birthDate(), trace.vaccineLastDate(),
                        trace.vaccineLastOrder(), barn, "trace", trace.farmNo(), now);
                return c;
            }).orElseGet(() -> Cow.register(farm, tag, trace.breed(), trace.sex(), trace.birthDate(),
                    trace.vaccineLastDate(), trace.vaccineLastOrder(), barn, "trace", trace.farmNo(), now));
        } else {
            // 못 찾았거나 조회가 안 되면 성별·출생일만 넣어 등록 (F-P04N)
            if (request.sex() == null || request.birthDate() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "성별과 출생일을 넣어 주세요.", "직접 입력 값 없음");
            }
            LocalDate birth = birthDate(request.birthDate());
            cow = existing.map(c -> {
                c.reRegister(null, request.sex(), birth, null, null, barn, "manual", null, null);
                return c;
            }).orElseGet(() -> Cow.register(farm, tag, null, request.sex(), birth, null, null, barn, "manual", null, null));
        }
        return toResponse(cowRepository.save(cow));
    }

    /** FARM-24 축사 수정 · 목록에서 빼기 (+ 신고서 초안) · 이력제 다시 불러오기 */
    @Transactional
    CowUpdateResponse update(Long userId, Long cowId, CowUpdateRequest request) {
        FarmInfo farm = support.farm(userId);
        Cow cow = cowRepository.findByIdAndFarmId(cowId, farm.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATTLE_NOT_FOUND, "내 농장 소 아님"));
        if (request.traceRefreshRequested()) {
            refreshTrace(farm, cow);
        }
        if (request.barn() != null) {
            cow.changeBarn(blankToNull(request.barn()));
        }
        Long reportId = null;
        if (request.removeReason() != null) {
            cow.remove();
            String type = switch (request.removeReason()) {
                case "폐사" -> "폐사";
                case "팔림" -> "양도";
                default -> null;
            };
            if (request.reportRequested() && type != null) {
                // 날짜는 모르므로 비워 두고 F-S04Q 에서 묻는다
                Map<String, Object> fields = new LinkedHashMap<>();
                // 12자리로 넘겨야 같은 끝 4자리 소가 있어도 이 소로 정해진다
                fields.put("cow_tag", cow.getTagFull() == null ? cow.getTagLast4() : cow.getTagFull());
                HistoryReport report = historyReportService.createDraft(farm, type, fields);
                reportId = report.getId();
            }
        }
        return new CowUpdateResponse(toResponse(cow), reportId);
    }

    /** 귀표로 이력제를 다시 조회해 그 값으로 바꾼다. 못 찾음·조회 실패·다른 농장 소면 오류를 내고 값은 그대로 둔다 */
    private void refreshTrace(FarmInfo farm, Cow cow) {
        if (cow.getTagFull() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "귀표에 적힌 12자리 숫자가 없는 소예요.", "귀표 없음");
        }
        TraceResult trace = found(traceClient.lookup(cow.getTagFull()));
        if (isOtherFarm(trace, farm)) {
            throw new BusinessException(ErrorCode.CATTLE_OTHER_FARM, "이력제 농장식별번호가 다름");
        }
        cow.refreshFromTrace(trace.breed(), trace.sex(), trace.birthDate(), trace.vaccineLastDate(),
                trace.vaccineLastOrder(), trace.farmNo(), Instant.now());
    }

    private static TraceResult found(TraceResult trace) {
        return switch (trace.status()) {
            case FOUND -> trace;
            case NOT_FOUND -> throw new BusinessException(ErrorCode.CATTLE_NOT_FOUND, "이력제에 없음");
            case FAILED -> throw new BusinessException(ErrorCode.TRACE_API_FAIL, "이력제 조회 실패");
        };
    }

    private static boolean isOtherFarm(TraceResult trace, FarmInfo farm) {
        return trace.farmNo() != null && !trace.farmNo().equals(farm.getFarmNo());
    }

    /** 3-4-4-1 띄어쓰기·하이픈을 빼고 12자리인지 본다 */
    private static String tag(String tagNo) {
        String digits = tagNo == null ? "" : tagNo.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{12}")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "귀표에 적힌 12자리 숫자를 넣어 주세요.", "귀표 형식 오류");
        }
        return digits;
    }

    private static LocalDate birthDate(String value) {
        try {
            LocalDate date = LocalDate.parse(value.length() == 7 ? value + "-01" : value);
            if (date.isAfter(FarmerRules.today())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "출생일을 다시 확인해 주세요.", "미래 날짜");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "출생일을 다시 확인해 주세요.", "날짜 형식 오류");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static CowResponse toResponse(Cow c) {
        return new CowResponse(c.getId(), c.getTagLast4(), c.getTagFull(), c.getBreed(), c.getSex(), c.getBirthDate(),
                FarmerSupport.age(c.getBirthDate()), c.getBarn(), c.getVaccineLastDate(), c.getVaccineLastOrder(),
                c.getSource(), c.getTraceCheckedAt(), c.getStatus());
    }
}
