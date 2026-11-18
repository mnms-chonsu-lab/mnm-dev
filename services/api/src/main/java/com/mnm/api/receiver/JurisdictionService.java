package com.mnm.api.receiver;

import com.mnm.api.domain.alert.Alert;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.task.Task;
import com.mnm.api.domain.user.User;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.receiver.JurisdictionDtos.FarmRow;
import com.mnm.api.receiver.JurisdictionDtos.JurisdictionResponse;
import com.mnm.api.receiver.JurisdictionDtos.Kpis;
import com.mnm.api.receiver.JurisdictionDtos.NextTask;
import com.mnm.api.receiver.JurisdictionDtos.NudgeResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관할 농가 현황 · 다시 알리기 (R-06). 발생 시 예찰 연락 순서를 정하는 용도 */
@Service
@RequiredArgsConstructor
class JurisdictionService {

    private final ReceiverSupport support;
    private final ReceiverFarmRepository farmRepository;
    private final ReceiverTaskRepository taskRepository;
    private final ReceiverAlertRepository alertRepository;
    private final ReceiverDiaryRepository diaryRepository;
    private final ReceiverDeliveryRepository deliveryRepository;

    /**
     * RCV-06 관할 농가 + 지표 4개. 정렬: 미응답 → 기한 남은 일수 적은 순 → 두수 적은 순.
     * shortcut: 관할 농가 전체를 한 번에 읽어 메모리에서 정렬한다 (시범 운영 규모), 수백 곳을 넘으면 페이지로 나눈다
     */
    @Transactional(readOnly = true)
    JurisdictionResponse farms(Long userId) {
        ReceiverOrg org = support.receiver(userId).getOrg();
        Set<String> sigungu = ReceiverSupport.sigungu(org);
        List<FarmInfo> farms = farmRepository.findJurisdiction(org.getId(), sigungu.isEmpty() ? Set.of("-") : sigungu);
        if (farms.isEmpty()) {
            return new JurisdictionResponse(new Kpis(0, 0, 0, 0), List.of());
        }
        List<Long> farmIds = farms.stream().map(FarmInfo::getId).toList();
        List<Long> ownerIds = farms.stream().map(f -> f.getOwner().getId()).toList();

        Map<Long, Task> nextTask = new HashMap<>();
        for (Task t : taskRepository.findByFarmIdInAndDoneAtIsNullOrderByDueDateAscIdAsc(farmIds)) {
            nextTask.putIfAbsent(t.getFarm().getId(), t);
        }
        Map<Long, List<Alert>> unread = alertRepository.findByUserIdInAndReadAtIsNullAndFeedbackIsNullOrderByIdDesc(ownerIds)
                .stream().collect(Collectors.groupingBy(a -> a.getUser().getId()));
        Set<Long> withAlerts = new HashSet<>(alertRepository.findUserIdsWithAlerts(ownerIds));
        Map<Long, LocalDate> lastDates = new HashMap<>();
        for (Object[] row : diaryRepository.findLastDates(farmIds)) {
            lastDates.put((Long) row[0], (LocalDate) row[1]);
        }
        Map<Long, Boolean> sharePhone = latestSharePhone(org, farmIds);

        LocalDate today = ReceiverRules.today();
        Instant now = Instant.now();
        List<FarmRow> rows = new ArrayList<>();
        for (FarmInfo farm : farms) {
            User owner = farm.getOwner();
            Task task = nextTask.get(farm.getId());
            NextTask next = task == null ? null : new NextTask(task.getKind(), task.getTitle(), task.getDueDate(),
                    ChronoUnit.DAYS.between(today, task.getDueDate()));
            String alertState = ReceiverRules.alertState(unread.getOrDefault(owner.getId(), List.of()),
                    withAlerts.contains(owner.getId()), now);
            LocalDate last = lastDates.get(farm.getId());
            boolean shared = sharePhone.getOrDefault(farm.getId(), false);
            List<String> reasons = new ArrayList<>();
            if ("unanswered".equals(alertState)) {
                reasons.add("unanswered");
            }
            if (next != null && next.dDay() <= ReceiverRules.DUE_SOON_DAYS) {
                reasons.add("due_soon");
            }
            if (farm.getHeadCount() != null && farm.getHeadCount() < ReceiverRules.SMALL_FARM_HEADS) {
                reasons.add("small");
            }
            rows.add(new FarmRow(farm.getId(), farm.getName(), farm.getAddress(), farm.getRegionCode(),
                    farm.getHeadCount(), ReceiverRules.maskPhone(owner.getPhone()), shared,
                    shared ? owner.getPhone() : null, last, today.equals(last), next, alertState, reasons));
        }
        rows.sort(ReceiverRules.contactPriority(r -> "unanswered".equals(r.alertState()),
                r -> r.nextTask() == null ? null : r.nextTask().dDay(), FarmRow::headCount));
        Kpis kpis = new Kpis(
                rows.stream().filter(r -> r.reasons().contains("due_soon")).count(),
                rows.stream().filter(r -> r.reasons().contains("unanswered")).count(),
                rows.stream().filter(r -> r.reasons().contains("small")).count(),
                rows.stream().filter(FarmRow::recordedToday).count());
        return new JurisdictionResponse(kpis, rows);
    }

    /**
     * RCV-07 다시 알리기: 48시간 넘게 안 읽은 가장 최근 알림을 농가 알림함에 한 번 더 넣는다.
     * 미응답 알림이 없으면 (이미 확인함) CONFLICT-409
     */
    @Transactional
    NudgeResponse nudge(Long userId, Long farmId) {
        User me = support.receiver(userId);
        FarmInfo farm = farmRepository.findWithOwnerById(farmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "농가 없음"));
        if (!ReceiverSupport.inJurisdiction(farm, me.getOrg())) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "관할 밖 농가");
        }
        Instant now = Instant.now();
        Alert unanswered = alertRepository
                .findByUserIdInAndReadAtIsNullAndFeedbackIsNullOrderByIdDesc(List.of(farm.getOwner().getId()))
                .stream().filter(a -> ReceiverRules.isUnanswered(a, now)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "미응답 알림 없음"));
        Alert again = alertRepository.save(Alert.create(farm.getOwner(), unanswered.getKind(), unanswered.getTitle(),
                unanswered.getBody(), unanswered.getRefId()));
        support.audit(me, "nudge", "farm", farm.getId());
        return new NudgeResponse(again.getId(), again.getCreatedAt());
    }

    /** 농가별로 우리 기관에 보낸 가장 최근 기록의 연락처 공개 여부 */
    private Map<Long, Boolean> latestSharePhone(ReceiverOrg org, List<Long> farmIds) {
        Map<Long, Boolean> shared = new HashMap<>();
        Map<Long, Long> latestId = new HashMap<>();
        for (Object[] row : deliveryRepository.findSharePhones(org.getId(), farmIds)) {
            Long farmId = (Long) row[0];
            Long deliveryId = (Long) row[2];
            if (latestId.getOrDefault(farmId, Long.MIN_VALUE) < deliveryId) {
                latestId.put(farmId, deliveryId);
                shared.put(farmId, (Boolean) row[1]);
            }
        }
        return shared;
    }
}
