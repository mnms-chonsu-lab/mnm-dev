package com.mnm.api.admin;

import com.mnm.api.admin.AdminOrgDtos.Inbox;
import com.mnm.api.admin.AdminUserDtos.CreateUserRequest;
import com.mnm.api.admin.AdminUserDtos.CreateUserResponse;
import com.mnm.api.admin.AdminUserDtos.FarmSummary;
import com.mnm.api.admin.AdminUserDtos.UpdateUserRequest;
import com.mnm.api.admin.AdminUserDtos.UpdateUserResponse;
import com.mnm.api.admin.AdminUserDtos.UserDetail;
import com.mnm.api.admin.AdminUserDtos.UserRow;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.user.User;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

@Service
@RequiredArgsConstructor
class AdminUserService {

    /** 농가 로그인 키라 휴대폰 번호만 받는다 (AuthDtos 와 같은 형식) */
    private static final String MOBILE = "01\\d-?\\d{3,4}-?\\d{4}";
    /** 헷갈리는 글자(0·O·1·l·I)를 뺀 임시 비밀번호 글자 */
    private static final String TEMP_PW_CHARS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final TypeReference<List<Inbox>> INBOXES = new TypeReference<>() { };

    private final AdminUserRepository userRepository;
    private final AdminFarmRepository farmRepository;
    private final AdminOrgRepository orgRepository;
    private final AdminMetricsRepository metricsRepository;
    private final AdminSupport support;
    private final PasswordEncoder passwordEncoder;

    /** ADM-02 목록·검색. 최근에 만든 계정부터 20개씩 */
    @Transactional(readOnly = true)
    PageResponse<UserRow> list(String role, String status, Long orgId, String q, String cursor) {
        String like = q == null || q.isBlank() ? null : "%" + q.strip() + "%";
        List<Object[]> rows = userRepository.search(role, status, orgId, like, AdminSupport.cursor(cursor),
                Limit.of(AdminSupport.PAGE_SIZE + 1));
        return AdminSupport.page(rows, AdminSupport.PAGE_SIZE, r -> toRow((User) r[0], (FarmInfo) r[1]),
                r -> ((User) r[0]).getId());
    }

    /** ADM-02 상세 (A-S03 · A-S03F) */
    @Transactional(readOnly = true)
    UserDetail detail(Long userId) {
        return toDetail(user(userId));
    }

    /** ADM-03 계정 만들기. 농가는 농장도 함께 만들고, 접수자·운영자는 임시 비밀번호를 한 번만 돌려준다 */
    @Transactional
    CreateUserResponse create(Long adminId, CreateUserRequest req) {
        if ("farmer".equals(req.role())) {
            return createFarmer(adminId, req);
        }
        if (isBlank(req.loginId())) {
            throw invalid("아이디를 넣어 주세요.", "login_id 없음");
        }
        if (userRepository.existsByLoginId(req.loginId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 쓰는 아이디예요.", "login_id 중복");
        }
        boolean receiver = "receiver".equals(req.role());
        if (receiver && req.orgId() == null) {
            throw invalid("소속 기관을 골라 주세요.", "org_id 없음");
        }
        ReceiverOrg org = req.orgId() == null ? null : org(req.orgId());
        String inboxId = receiver ? checkedInbox(org, req.inboxId()) : null;
        String tempPassword = tempPassword();
        User user = userRepository.save(User.staff(req.role(), req.name().strip(), req.loginId(), req.phone(), org,
                inboxId, passwordEncoder.encode(tempPassword)));
        support.audit(adminId, "create", "user", user.getId());
        return new CreateUserResponse(user.getId(), user.getRole(), user.getLoginId(), tempPassword);
    }

    /** ADM-04 계정 수정 · 정지 · 비밀번호 초기화. 보낸 칸만 바꾸고, 바꾼 것마다 감사 기록을 남긴다 */
    @Transactional
    UpdateUserResponse update(Long adminId, Long userId, UpdateUserRequest req) {
        User user = user(userId);
        boolean farmer = "farmer".equals(user.getRole());
        boolean changed = false;

        if (req.status() != null && !req.status().equals(user.getStatus())) {
            if (userId.equals(adminId) && "suspended".equals(req.status())) {
                throw invalid("내 계정은 정지할 수 없어요.", "자기 계정 정지");
            }
            user.changeStatus(req.status());
            changed = true;
        }
        if (req.phone() != null) {
            if (farmer) {
                checkFarmerPhone(req.phone(), userId);
            }
            user.changePhone(req.phone());
            changed = true;
        }
        if (farmer && req.orgId() != null) {
            FarmInfo farm = farmRepository.findFirstByOwnerId(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "농장 정보 없음"));
            farm.changeOrg(org(req.orgId()));
            changed = true;
        }
        if (!farmer && (req.orgId() != null || req.inboxId() != null)) {
            ReceiverOrg org = req.orgId() != null ? org(req.orgId()) : user.getOrg();
            boolean orgMoved = req.orgId() != null && (user.getOrg() == null || !req.orgId().equals(user.getOrg().getId()));
            // 기관을 옮기면 예전 수신함은 새 기관에 없으므로 비운다
            String inboxId = req.inboxId() != null ? req.inboxId() : orgMoved ? null : user.getInboxId();
            boolean receiver = "receiver".equals(user.getRole());
            user.assignOrg(org, receiver ? checkedInbox(org, inboxId) : null);
            changed = true;
        }
        if (changed) {
            support.audit(adminId, "update", "user", userId);
        }

        String tempPassword = null;
        if (Boolean.TRUE.equals(req.resetPassword())) {
            if (farmer) {
                throw invalid("농가 계정은 비밀번호가 없어요. 농장식별번호와 휴대폰 번호로 로그인해요.", "농가 비밀번호 초기화");
            }
            tempPassword = tempPassword();
            user.resetPassword(passwordEncoder.encode(tempPassword));
            support.audit(adminId, "password_reset", "user", userId);
        }
        return new UpdateUserResponse(toDetail(user), tempPassword);
    }

    private CreateUserResponse createFarmer(Long adminId, CreateUserRequest req) {
        if (isBlank(req.farmNo())) {
            throw invalid("농장식별번호 6자리를 넣어 주세요.", "farm_no 없음");
        }
        if (isBlank(req.phone())) {
            throw invalid("휴대폰 번호를 넣어 주세요.", "phone 없음");
        }
        if (farmRepository.existsByFarmNo(req.farmNo())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 등록된 농장식별번호예요.", "farm_no 중복");
        }
        checkFarmerPhone(req.phone(), -1L);
        ReceiverOrg org = req.orgId() == null ? null : org(req.orgId());
        String name = req.name().strip();
        User user = userRepository.save(User.farmer(name, req.phone()));
        String farmName = isBlank(req.farmName()) ? name + " 농장" : req.farmName().strip();
        farmRepository.save(FarmInfo.create(req.farmNo(), user, farmName, req.address(), req.regionCode(),
                req.headCount(), org));
        support.audit(adminId, "create", "user", user.getId());
        return new CreateUserResponse(user.getId(), user.getRole(), null, null);
    }

    private void checkFarmerPhone(String phone, Long excludeId) {
        if (!phone.matches(MOBILE)) {
            throw invalid("휴대폰 번호를 다시 확인해 주세요.", "phone 형식 오류");
        }
        if (userRepository.existsFarmerPhone(AdminSupport.digits(phone), excludeId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 등록된 휴대폰 번호예요.", "phone 중복");
        }
    }

    /** 수신함은 그 기관에 있는 것만 고를 수 있다. 없으면(null) 그대로 둔다 */
    private String checkedInbox(ReceiverOrg org, String inboxId) {
        if (inboxId == null) {
            return null;
        }
        boolean exists = org != null && support.readList(org.getInboxes(), INBOXES).stream()
                .anyMatch(i -> inboxId.equals(i.inboxId()));
        if (!exists) {
            throw invalid("수신함을 다시 골라 주세요.", "기관에 없는 inbox_id");
        }
        return inboxId;
    }

    private User user(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자 없음"));
    }

    private ReceiverOrg org(Long orgId) {
        return orgRepository.findById(orgId)
                .orElseThrow(() -> invalid("소속 기관을 다시 골라 주세요.", "없는 org_id"));
    }

    private UserRow toRow(User u, FarmInfo f) {
        ReceiverOrg org = u.getOrg() != null ? u.getOrg() : f == null ? null : f.getOrg();
        return new UserRow(u.getId(), u.getRole(), u.getName(), u.getLoginId(), AdminSupport.maskPhone(u.getPhone()),
                org == null ? null : org.getId(), org == null ? null : org.getName(),
                f == null ? null : f.getFarmNo(), f == null ? null : f.getName(), u.getStatus(), u.getLastLoginAt());
    }

    private UserDetail toDetail(User u) {
        FarmSummary farm = "farmer".equals(u.getRole())
                ? farmRepository.findFirstByOwnerId(u.getId()).map(this::toFarmSummary).orElse(null)
                : null;
        ReceiverOrg org = u.getOrg();
        return new UserDetail(u.getId(), u.getRole(), u.getName(), u.getLoginId(), AdminSupport.maskPhone(u.getPhone()),
                u.getStatus(), u.isMustChangePw(), u.isLocked(Instant.now()), u.getLastLoginAt(), u.getCreatedAt(),
                org == null ? null : org.getId(), org == null ? null : org.getName(), u.getInboxId(), farm);
    }

    private FarmSummary toFarmSummary(FarmInfo f) {
        LocalDate from = LocalDate.now(AdminSupport.KST).minusDays(29);
        long records = metricsRepository.countDiary(f.getId(), from)
                + metricsRepository.countSymptomReports(f.getId(), from.atStartOfDay(AdminSupport.KST).toInstant());
        ReceiverOrg org = f.getOrg();
        return new FarmSummary(f.getId(), f.getFarmNo(), f.getName(), f.getAddress(), f.getRegionCode(),
                f.getHeadCount(), org == null ? null : org.getId(), org == null ? null : org.getName(),
                metricsRepository.countActiveCows(f.getId()), records, metricsRepository.countFarmDeliveries(f.getId()));
    }

    /** 예: Kd7m-29qx. 영문·숫자를 하나 이상 섞는다 */
    static String tempPassword() {
        while (true) {
            StringBuilder sb = new StringBuilder(9);
            for (int i = 0; i < 8; i++) {
                if (i == 4) {
                    sb.append('-');
                }
                sb.append(TEMP_PW_CHARS.charAt(RANDOM.nextInt(TEMP_PW_CHARS.length())));
            }
            String pw = sb.toString();
            if (pw.matches(".*[A-Za-z].*") && pw.matches(".*\\d.*")) {
                return pw;
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BusinessException invalid(String message, String detail) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message, detail);
    }
}
