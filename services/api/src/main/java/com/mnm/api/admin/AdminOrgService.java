package com.mnm.api.admin;

import com.mnm.api.admin.AdminOrgDtos.Inbox;
import com.mnm.api.admin.AdminOrgDtos.InboxResponse;
import com.mnm.api.admin.AdminOrgDtos.OrgRequest;
import com.mnm.api.admin.AdminOrgDtos.OrgResponse;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

@Service
@RequiredArgsConstructor
class AdminOrgService {

    private static final TypeReference<List<Inbox>> INBOXES = new TypeReference<>() { };
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminOrgRepository orgRepository;
    private final AdminUserRepository userRepository;
    private final AdminSupport support;

    /** ADM-05 기관 목록. 기관 수가 적어 한 번에 준다 */
    @Transactional(readOnly = true)
    PageResponse<OrgResponse> list() {
        Map<Long, Map<String, Long>> counts = receiverCounts();
        List<OrgResponse> orgs = orgRepository.findAllByOrderByIdAsc().stream()
                .map(o -> toResponse(o, counts.getOrDefault(o.getId(), Map.of())))
                .toList();
        return new PageResponse<>(orgs, false, null);
    }

    @Transactional
    OrgResponse create(Long adminId, OrgRequest req) {
        ReceiverOrg org = orgRepository.save(ReceiverOrg.create(req.name().strip(), req.type(),
                regionCodes(req), req.phone(), support.write(inboxes(req, Set.of()))));
        support.audit(adminId, "create", "receiver_org", org.getId());
        return toResponse(org, Map.of());
    }

    /** 편집 패널 저장. 접수자가 남아 있는 수신함은 뺄 수 없다 (그 수신함 기록을 아무도 못 보게 되므로) */
    @Transactional
    OrgResponse update(Long adminId, Long orgId, OrgRequest req) {
        ReceiverOrg org = orgRepository.findById(orgId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "기관 없음"));
        Set<String> existing = new HashSet<>();
        support.readList(org.getInboxes(), INBOXES).forEach(i -> existing.add(i.inboxId()));
        List<Inbox> inboxes = inboxes(req, existing);

        Map<String, Long> counts = receiverCounts().getOrDefault(orgId, Map.of());
        Set<String> kept = new HashSet<>();
        inboxes.forEach(i -> kept.add(i.inboxId()));
        boolean removesStaffed = counts.entrySet().stream()
                .anyMatch(e -> e.getKey() != null && e.getValue() > 0 && !kept.contains(e.getKey()));
        if (removesStaffed) {
            throw new BusinessException(ErrorCode.CONFLICT, "접수자가 있는 수신함은 뺄 수 없어요. 접수자를 먼저 옮겨 주세요.",
                    "접수자가 있는 수신함 삭제");
        }
        org.update(req.name().strip(), req.type(), regionCodes(req), req.phone(), support.write(inboxes));
        support.audit(adminId, "update", "receiver_org", orgId);
        return toResponse(org, counts);
    }

    /** 새 수신함에는 id 를 만들어 준다. 기존 id 는 이 기관에 있던 것만, 한 번씩만 쓸 수 있다 */
    private static List<Inbox> inboxes(OrgRequest req, Set<String> existing) {
        Set<String> seen = new HashSet<>();
        return req.inboxes().stream().map(i -> {
            String id = i.inboxId() == null ? newInboxId() : i.inboxId();
            if (i.inboxId() != null && !existing.contains(id) || !seen.add(id)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "수신함을 다시 확인해 주세요.", "없는 inbox_id 또는 중복");
            }
            return new Inbox(id, i.type(), i.name().strip(), i.phone());
        }).toList();
    }

    private static String[] regionCodes(OrgRequest req) {
        return req.regionCodes().stream().distinct().toArray(String[]::new);
    }

    private static String newInboxId() {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        return "inbox-" + HexFormat.of().formatHex(bytes);
    }

    /** org_id → (inbox_id → 접수자 수). 수신함이 없는 접수자는 inbox_id null 로 센다 */
    private Map<Long, Map<String, Long>> receiverCounts() {
        Map<Long, Map<String, Long>> counts = new HashMap<>();
        for (Object[] row : userRepository.countReceiversByInbox()) {
            counts.computeIfAbsent((Long) row[0], k -> new HashMap<>()).put((String) row[1], (Long) row[2]);
        }
        return counts;
    }

    private OrgResponse toResponse(ReceiverOrg org, Map<String, Long> counts) {
        List<InboxResponse> inboxes = support.readList(org.getInboxes(), INBOXES).stream()
                .map(i -> new InboxResponse(i.inboxId(), i.type(), i.name(), i.phone(),
                        counts.getOrDefault(i.inboxId(), 0L)))
                .toList();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new OrgResponse(org.getId(), org.getName(), org.getType(), List.of(org.getRegionCodes()), org.getPhone(),
                inboxes, total);
    }
}
