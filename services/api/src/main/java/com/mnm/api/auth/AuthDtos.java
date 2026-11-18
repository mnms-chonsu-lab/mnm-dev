package com.mnm.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

/** 인증 API 요청·응답 */
final class AuthDtos {

    private AuthDtos() {
    }

    /** AUTH-01 농가 로그인 (F-S00). 휴대폰 번호는 하이픈이 있어도 된다 */
    record FarmerLoginRequest(
            @NotBlank(message = "농장식별번호 6자리를 넣어 주세요.")
            @Pattern(regexp = "\\d{6}", message = "농장식별번호 6자리를 넣어 주세요.")
            String farmNo,
            @NotBlank(message = "휴대폰 번호를 넣어 주세요.")
            @Pattern(regexp = "01\\d-?\\d{3,4}-?\\d{4}", message = "휴대폰 번호를 다시 확인해 주세요.")
            String phone) {
    }

    /** AUTH-02 접수자·운영자 로그인 (R-S01) */
    record StaffLoginRequest(
            @NotBlank(message = "아이디를 넣어 주세요.") String loginId,
            @NotBlank(message = "비밀번호를 넣어 주세요.") String password) {
    }

    /** AUTH-04 임시 비밀번호 → 새 비밀번호 (R-S06). 확인 칸 일치는 화면에서 검사 */
    record PasswordChangeRequest(
            @NotBlank(message = "새 비밀번호를 넣어 주세요.")
            @Pattern(regexp = "(?=.*[A-Za-z])(?=.*\\d)\\S{8,64}", message = "8자 이상, 영문과 숫자를 섞어 주세요.")
            String newPassword) {
    }

    /** 로그인·비밀번호 변경 결과. must_change_pw 가 true 면 R-S06 으로 보낸다 */
    record LoginResponse(String accessToken, Instant expiresAt, String role, boolean mustChangePw) {
    }

    /** AUTH-03 내 계정·역할·소속 */
    record MeResponse(Long usersId, String role, String name, String phone, String loginId,
            boolean mustChangePw, Org org, String inboxId, Farm farm) {
    }

    /** 접수자 소속 기관 */
    record Org(Long orgId, String name, String type, List<String> regionCodes) {
    }

    /** 농가의 농장. org_id · org_name 은 담당 축협 */
    record Farm(Long farmId, String farmNo, String name, Integer headCount, Long orgId, String orgName) {
    }
}
