package com.mnm.api.farmer;

import com.mnm.api.domain.delivery.Delivery;
import com.mnm.api.domain.diary.DiaryEntry;
import com.mnm.api.domain.farm.Cow;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.report.ReportSummary;
import com.mnm.api.domain.symptom.SymptomReport;
import com.mnm.api.farmer.DeliveryDtos.DeliveryRequest;
import com.mnm.api.farmer.DeliveryDtos.DeliveryResponse;
import com.mnm.api.farmer.DeliveryDtos.SentRecordResponse;
import com.mnm.api.global.ai.AiClient;
import com.mnm.api.global.ai.AiClient.SummaryResult;
import com.mnm.api.global.ai.AiException;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 승인 → 요약 카드 생성 → 경로별 전달 (F-11), 내가 보낸 기록 (F-15).
 * 농가가 [네, 보낼게요]를 누른 요청만 이 API 로 온다. 승인 없이는 어떤 기관에도 기록이 가지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class DeliveryService {

    /** 기기 시계가 조금 빨라도 받는다 (대기열에서 보낸 승인 시각) */
    static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final FarmerSupport support;
    private final AiClient aiClient;
    private final SymptomService symptomService;
    private final FarmerOrgRepository orgRepository;
    private final FarmerReportSummaryRepository summaryRepository;
    private final FarmerDeliveryRepository deliveryRepository;
    private final FarmerDiaryRepository diaryRepository;
    private final FarmerReceiverCheckRepository receiverCheckRepository;

    /**
     * FARM-15. coop → 담당 축협, gov → 관할 시군청 수신함. call·web 은 send_to_coop 이면 담당 축협(cc)에만, 끄면 전달 없이 일지에만.
     * 같은 Idempotency-Key 를 다시 보내면 CONFLICT-409 (DB 고유 제약).
     */
    @Transactional
    DeliveryResponse deliver(Long userId, String idempotencyKey, DeliveryRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Idempotency-Key 헤더 필요");
        }
        FarmInfo farm = support.farm(userId);
        SymptomReport report = symptomService.find(farm, request.symptomReportId());
        Instant now = Instant.now();
        Instant approvedAt = request.approvedAt() == null ? now : request.approvedAt();
        if (approvedAt.isAfter(now.plus(CLOCK_SKEW))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "승인 시각이 서버 시각보다 늦음");
        }
        String route = request.route();
        boolean ccOn = request.sendToCoop() == null || request.sendToCoop();
        boolean sharePhone = request.sharePhone() == null || request.sharePhone();
        ReceiverOrg org = switch (route) {
            case "coop" -> required(farm.getOrg(), "담당 축협 없음");
            case "gov" -> required(govOrg(farm), "관할 시군청 없음");
            default -> null;
        };
        ReceiverOrg cc = org == null && ccOn ? required(farm.getOrg(), "담당 축협 없음") : null;

        Map<String, Object> answers = support.readMap(report.getAnswers());
        String disease = FarmerRules.disease(FarmerRules.SYMPTOM_KEYS.stream()
                .collect(Collectors.toMap(k -> k, k -> String.valueOf(answers.get(k)))));
        SummaryResult summary = null;
        try {
            summary = aiClient.summary(symptomService.summaryRequest(farm, report));
        } catch (AiException e) {
            // 요약이 안 돼도 전달은 멈추지 않는다. 접수자 화면은 원본 기록을 보여준다
            log.warn("요약 카드 생성 실패, 원본 기록으로 전달: {}", e.getMessage());
        }
        Cow cow = report.getCow();
        String what = cow == null ? "소 상태 기록" : cow.getTagLast4() + "번 기록";
        LocalDate today = FarmerRules.today();

        if (org == null && cc == null) {
            diaryRepository.save(DiaryEntry.record(farm, today, "신고", cow, what + " · " + routeName(route) + " 신고",
                    null, null, null));
            return new DeliveryResponse(null, route, "not_sent", null, null, approvedAt, disease,
                    summary == null ? null : summary.form6Text());
        }
        ReportSummary saved = summaryRepository.save(ReportSummary.create(report,
                summary == null ? null : support.write(summary.card()),
                summary == null ? null : support.write(summary.form6())));
        Delivery delivery = deliveryRepository.save(Delivery.approve(saved, null, route, org, cc, sharePhone,
                idempotencyKey, approvedAt, "delivered"));
        ReceiverOrg to = org != null ? org : cc;
        diaryRepository.save(DiaryEntry.record(farm, today, "신고", cow, what + " 전달 · " + to.getName(),
                null, null, null));
        return new DeliveryResponse(delivery.getId(), route, delivery.getStatus(), org == null ? null : org.getName(),
                cc == null ? null : cc.getName(), approvedAt, disease, summary == null ? null : summary.form6Text());
    }

    /** FARM-27 내가 보낸 기록 (최근순, 20건씩) */
    @Transactional(readOnly = true)
    PageResponse<SentRecordResponse> mine(Long userId, String cursor) {
        FarmInfo farm = support.farm(userId);
        List<Delivery> rows = deliveryRepository.findMine(farm.getId(), FarmerSupport.cursor(cursor),
                PageRequest.of(0, FarmerSupport.PAGE_SIZE + 1));
        return FarmerSupport.page(rows, FarmerSupport.PAGE_SIZE, this::toSent, Delivery::getId);
    }

    private SentRecordResponse toSent(Delivery d) {
        ReceiverOrg org = d.getOrg() != null ? d.getOrg() : d.getCcOrg();
        var check = receiverCheckRepository.findFirstByDeliveryIdOrderByAtDescIdDesc(d.getId());
        String kind;
        String title;
        Long symptomId = null;
        Long historyId = null;
        if (d.getHistoryReport() != null) {
            kind = "history";
            title = d.getHistoryReport().getType() + " 신고서";
            historyId = d.getHistoryReport().getId();
        } else {
            kind = "symptom";
            SymptomReport sr = d.getReportSummary().getSymptomReport();
            symptomId = sr.getId();
            title = sr.getCow() == null ? "소 상태 기록" : "소 상태 기록 (" + sr.getCow().getTagLast4() + "번)";
        }
        return new SentRecordResponse(d.getId(), d.getApprovedAt(), kind, title, d.getRoute(),
                org == null ? null : org.getName(), d.getStatus(), check.map(c -> c.getStatus()).orElse("new"),
                check.map(c -> c.getResult()).orElse(null), symptomId, historyId);
    }

    /** 농장 시군구(법정동 앞 5자리)를 관할로 둔 시군청. 농장 지역 코드가 없으면 담당 축협의 관할로 찾는다 */
    private ReceiverOrg govOrg(FarmInfo farm) {
        Set<String> sigungu = farm.getRegionCode() != null
                ? Set.of(farm.getRegionCode().substring(0, 5))
                : farm.getOrg() == null ? Set.of()
                        : Arrays.stream(farm.getOrg().getRegionCodes()).map(c -> c.substring(0, 5)).collect(Collectors.toSet());
        return orgRepository.findByType("시군청").stream()
                .filter(o -> Arrays.stream(o.getRegionCodes()).anyMatch(c -> sigungu.contains(c.substring(0, 5))))
                .findFirst()
                .orElse(null);
    }

    private static ReceiverOrg required(ReceiverOrg org, String detail) {
        if (org == null) {
            throw new BusinessException(ErrorCode.DELIVER_FAIL, detail);
        }
        return org;
    }

    private static String routeName(String route) {
        return Map.of("call", "전화", "web", "인터넷 신고서").getOrDefault(route, route);
    }
}
