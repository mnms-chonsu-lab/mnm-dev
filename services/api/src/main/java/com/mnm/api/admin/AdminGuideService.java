package com.mnm.api.admin;

import com.mnm.api.admin.AdminGuideDtos.CreateGuideRequest;
import com.mnm.api.admin.AdminGuideDtos.GuideContent;
import com.mnm.api.admin.AdminGuideDtos.GuideResponse;
import com.mnm.api.admin.AdminGuideDtos.UpdateGuideRequest;
import com.mnm.api.domain.guide.BiosecurityRule;
import com.mnm.api.domain.user.User;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

@Service
@RequiredArgsConstructor
class AdminGuideService {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };

    private final AdminRuleRepository ruleRepository;
    private final AdminUserRepository userRepository;
    private final AdminSupport support;

    /** ADM-07 문구 목록. 분류별 화면 순서대로, 한 번에 준다 */
    @Transactional(readOnly = true)
    PageResponse<GuideResponse> list(String kind, String status, String disease) {
        List<GuideResponse> rules = ruleRepository.search(kind, status, disease).stream().map(this::toResponse).toList();
        return new PageResponse<>(rules, false, null);
    }

    /** ADM-07 문구 등록 (초안) */
    @Transactional
    GuideResponse create(Long adminId, CreateGuideRequest req) {
        BiosecurityRule rule = BiosecurityRule.draft(req.kind());
        apply(rule, req.content());
        rule = ruleRepository.save(rule);
        support.audit(adminId, "create", "biosecurity_rule", rule.getId());
        return toResponse(rule);
    }

    /** ADM-08 수정 · 검수 · 게시. 게시된 문구의 내용을 고치면 새 버전(초안)을 돌려준다 */
    @Transactional
    GuideResponse update(Long adminId, Long ruleId, UpdateGuideRequest req) {
        BiosecurityRule target = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "문구 없음"));
        if (req.content() != null) {
            if (target.isPublished()) {
                BiosecurityRule next = target.nextVersion();
                apply(next, req.content());
                target = ruleRepository.save(next);
                support.audit(adminId, "create", "biosecurity_rule", target.getId());
            } else {
                apply(target, req.content());
                support.audit(adminId, "update", "biosecurity_rule", target.getId());
            }
        }
        if (req.status() != null && !req.status().equals(target.getStatus())) {
            changeStatus(adminId, target, req.status(), req.reviewer());
        }
        return toResponse(target);
    }

    private void changeStatus(Long adminId, BiosecurityRule rule, String status, String reviewer) {
        boolean publish = "published".equals(status);
        if (publish && "draft".equals(rule.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "검수를 거친 문구만 게시할 수 있어요.", "초안에서 바로 게시");
        }
        if (publish) {
            String name = reviewer != null ? reviewer
                    : userRepository.findById(adminId).map(User::getName).orElse(null);
            retirePreviousVersions(rule);
            rule.changeStatus(status, name);
            support.audit(adminId, "publish", "biosecurity_rule", rule.getId());
            return;
        }
        rule.changeStatus(status, null);
        support.audit(adminId, "update", "biosecurity_rule", rule.getId());
    }

    /**
     * 새 버전을 게시하면 같은 자리의 이전 게시 버전을 내려(초안) 보관한다. 화면에 같은 문구가 두 번 나오지 않게.
     * shortcut: 이전 버전을 같은 분류·질병·단계·순서·부위·조항으로 찾는다. mnm-data 에 이전 버전 id 나 보관 상태가 생기면 그걸로 바꾼다
     */
    private void retirePreviousVersions(BiosecurityRule rule) {
        ruleRepository.findByKindAndStatus(rule.getKind(), "published").stream()
                .filter(r -> !r.getId().equals(rule.getId()) && r.getVersion() < rule.getVersion())
                .filter(r -> Objects.equals(r.getDisease(), rule.getDisease())
                        && Objects.equals(r.getStage(), rule.getStage())
                        && Objects.equals(r.getDisplayOrder(), rule.getDisplayOrder())
                        && Objects.equals(r.getPartCode(), rule.getPartCode())
                        && Objects.equals(r.getClause(), rule.getClause()))
                .forEach(r -> r.changeStatus("draft", null));
    }

    private void apply(BiosecurityRule rule, GuideContent c) {
        rule.edit(c.disease(), c.stage(), c.text(), c.sourceDoc().strip(), c.clause(), c.revisedAt(), c.displayOrder(),
                c.exampleImage(), c.partCode(), c.photoPart(), support.write(c.yesRequiredParts()));
    }

    private GuideResponse toResponse(BiosecurityRule r) {
        return new GuideResponse(r.getId(), r.getKind(), r.getDisease(), r.getStage(), r.getText(), r.getSourceDoc(),
                r.getClause(), r.getRevisedAt(), r.getDisplayOrder(), r.getExampleImage(), r.getPartCode(),
                r.getPhotoPart(), r.getYesRequiredParts() == null ? null : support.readList(r.getYesRequiredParts(), STRINGS),
                r.getStatus(), r.getReviewer(), r.getVersion());
    }
}
