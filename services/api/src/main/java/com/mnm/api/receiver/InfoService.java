package com.mnm.api.receiver;

import com.mnm.api.domain.delivery.Delivery;
import com.mnm.api.domain.delivery.ReceiverCheck;
import com.mnm.api.domain.guide.BiosecurityRule;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.receiver.InfoDtos.DailyCount;
import com.mnm.api.receiver.InfoDtos.GuideDoc;
import com.mnm.api.receiver.InfoDtos.GuideItem;
import com.mnm.api.receiver.InfoDtos.WeeklyStats;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공식 지침 보기 (R-07) · 주간 통계 (R-08) */
@Service
@RequiredArgsConstructor
class InfoService {

    static final int WEEK_DAYS = 7;
    /** 농가 앱에 나가는 문구 종류 (요약 · 증상 질문 · 촬영 안내 · 할 일 · 신고서 항목). RAG 검색용 조항 본문(clause)은 뺀다 */
    static final List<String> GUIDE_KINDS = List.of("summary", "question", "photo_guide", "action", "form_field");

    private final ReceiverSupport support;
    private final ReceiverRuleRepository ruleRepository;
    private final ReceiverDeliveryRepository deliveryRepository;
    private final ReceiverCheckRepository checkRepository;
    private final ReceiverMediaRepository mediaRepository;

    /** RCV-08 농가 앱 문구의 출처인 게시된 지침을 문서별로 묶는다 (문서 안에서는 종류 → 표시 순서) */
    @Transactional(readOnly = true)
    List<GuideDoc> guides(Long userId) {
        support.receiver(userId);
        Map<String, List<BiosecurityRule>> byDoc = new LinkedHashMap<>();
        List<BiosecurityRule> published = new ArrayList<>(
                ruleRepository.findByKindInAndStatusOrderByDisplayOrderAscIdAsc(GUIDE_KINDS, "published"));
        published.sort(Comparator.comparingInt(r -> GUIDE_KINDS.indexOf(r.getKind())));
        for (BiosecurityRule r : published) {
            byDoc.computeIfAbsent(r.getSourceDoc(), k -> new ArrayList<>()).add(r);
        }
        List<GuideDoc> docs = new ArrayList<>();
        byDoc.forEach((doc, rules) -> docs.add(new GuideDoc(doc, rules.getFirst().getDisease(),
                rules.stream().map(BiosecurityRule::getRevisedAt).filter(d -> d != null).max(LocalDate::compareTo)
                        .orElse(null),
                rules.stream().map(r -> new GuideItem(r.getId(), r.getText(), r.getClause())).toList())));
        return docs;
    }

    /** RCV-09 최근 7일 접수 · 처리 완료 · 사진 포함 건수와 날짜별 접수 */
    @Transactional(readOnly = true)
    WeeklyStats weekly(Long userId) {
        ReceiverOrg org = support.receiver(userId).getOrg();
        LocalDate to = ReceiverRules.today();
        LocalDate from = to.minusDays(WEEK_DAYS - 1);
        List<Delivery> rows = deliveryRepository.findReceivedSince(org.getId(), ReceiverRules.startOf(from));

        long done = 0;
        if (!rows.isEmpty()) {
            done = checkRepository.findLatest(rows.stream().map(Delivery::getId).toList()).stream()
                    .map(ReceiverCheck::getStatus).filter(ReceiverRules.DONE::equals).count();
        }
        List<Long> reportIds = rows.stream().filter(d -> d.getReportSummary() != null)
                .map(d -> d.getReportSummary().getSymptomReport().getId()).toList();
        Set<Long> withPhoto = reportIds.isEmpty() ? Set.of()
                : new HashSet<>(mediaRepository.findReportIdsWithPhoto(reportIds));
        long photos = reportIds.stream().filter(withPhoto::contains).count();

        Map<LocalDate, Long> byDate = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDate.put(d, 0L);
        }
        for (Delivery d : rows) {
            byDate.computeIfPresent(LocalDate.ofInstant(d.getReceivedAt(), ReceiverRules.KST), (k, v) -> v + 1);
        }
        List<DailyCount> daily = byDate.entrySet().stream().map(e -> new DailyCount(e.getKey(), e.getValue())).toList();
        return new WeeklyStats(from, to, rows.size(), done, photos, daily);
    }
}
