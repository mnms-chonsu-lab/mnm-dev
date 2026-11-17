package com.mnm.api.auth;

import com.mnm.api.auth.AuthDtos.Farm;
import com.mnm.api.auth.AuthDtos.LoginResponse;
import com.mnm.api.auth.AuthDtos.MeResponse;
import com.mnm.api.auth.AuthDtos.Org;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.user.User;
import com.mnm.api.global.config.SecurityConfig;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AuthService {

    /** 로그인 유지 시간 (COM-01) */
    static final Duration FARMER_TOKEN_TTL = Duration.ofDays(30);
    static final Duration STAFF_TOKEN_TTL = Duration.ofHours(8);

    private final AuthUserRepository userRepository;
    private final AuthFarmRepository farmRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    /** AUTH-01. 농장식별번호로 계정을 찾고 휴대폰 번호가 같으면 로그인. 틀린 횟수는 그 농장 계정에 센다 */
    @Transactional(noRollbackFor = BusinessException.class)
    LoginResponse loginFarmer(String farmNo, String phone) {
        Instant now = Instant.now();
        User user = farmRepository.findByFarmNo(farmNo)
                .map(FarmInfo::getOwner)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FAIL, "농장식별번호 없음"));
        checkCredentials(user, digits(phone).equals(digits(user.getPhone())), now);
        return issue(user, now);
    }

    /** AUTH-02. 접수자·운영자 아이디·비밀번호 로그인 */
    @Transactional(noRollbackFor = BusinessException.class)
    LoginResponse loginStaff(String loginId, String password) {
        Instant now = Instant.now();
        User user = userRepository.findByLoginId(loginId)
                .filter(u -> !"farmer".equals(u.getRole()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_FAIL, "아이디 없음"));
        boolean matched = user.getPasswordHash() != null && passwordEncoder.matches(password, user.getPasswordHash());
        checkCredentials(user, matched, now);
        return issue(user, now);
    }

    /** AUTH-04. 임시 비밀번호로 로그인한 상태에서만 바꾼다 (R-S06). 새 토큰을 준다 */
    @Transactional
    LoginResponse changePassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_EXPIRED, "계정 없음"));
        if (!user.isMustChangePw()) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "임시 비밀번호 상태가 아님");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "임시 비밀번호와 다른 비밀번호를 정해 주세요.", "임시 비밀번호와 같음");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        return issue(user, Instant.now());
    }

    /** AUTH-03 */
    @Transactional(readOnly = true)
    MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_EXPIRED, "계정 없음"));
        ReceiverOrg org = user.getOrg();
        Farm farm = "farmer".equals(user.getRole())
                ? farmRepository.findFirstByOwnerId(userId).map(AuthService::toFarm).orElse(null)
                : null;
        return new MeResponse(user.getId(), user.getRole(), user.getName(), user.getPhone(), user.getLoginId(),
                user.isMustChangePw(),
                org == null ? null : new Org(org.getId(), org.getName(), org.getType(), List.of(org.getRegionCodes())),
                user.getInboxId(), farm);
    }

    /** 잠금 → 불일치(5번째면 잠금) → 정지 순서로 확인하고, 통과하면 틀린 횟수를 지운다 */
    private void checkCredentials(User user, boolean matched, Instant now) {
        if (user.isLocked(now)) {
            throw locked(user, now);
        }
        if (!matched) {
            user.recordLoginFailure(now);
            throw user.isLocked(now) ? locked(user, now) : new BusinessException(ErrorCode.AUTH_FAIL, "로그인 정보 불일치");
        }
        if (!user.isActive()) {
            // 명세에 정지 계정 코드가 없어 AUTH-403 을 쓴다 (제안)
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN,
                    "로그인할 수 없는 계정이에요. 담당 축협에 물어보세요.", "정지된 계정");
        }
        user.recordLoginSuccess(now);
    }

    private static BusinessException locked(User user, Instant now) {
        long minutes = Math.max(1, (Duration.between(now, user.getLockedUntil()).toSeconds() + 59) / 60);
        return new BusinessException(ErrorCode.AUTH_LOCK,
                "5번 틀려서 " + minutes + "분 동안 로그인할 수 없어요. 농장식별번호는 담당 축협에 물어보세요.",
                "로그인 잠금");
    }

    private LoginResponse issue(User user, Instant now) {
        Instant expiresAt = now.plus("farmer".equals(user.getRole()) ? FARMER_TOKEN_TTL : STAFF_TOKEN_TTL);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(SecurityConfig.CLAIM_ROLE, user.getRole())
                .claim(SecurityConfig.CLAIM_MUST_CHANGE_PW, user.isMustChangePw())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new LoginResponse(token, expiresAt, user.getRole(), user.isMustChangePw());
    }

    private static Farm toFarm(FarmInfo farm) {
        ReceiverOrg org = farm.getOrg();
        return new Farm(farm.getId(), farm.getFarmNo(), farm.getName(), farm.getHeadCount(),
                org == null ? null : org.getId(), org == null ? null : org.getName());
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
