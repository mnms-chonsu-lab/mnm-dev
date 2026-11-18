package com.mnm.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 사용자 관리 요청·응답 (ADM-02~04, A-S02 · A-P01 · A-S03 · A-S03F) */
final class AdminUserDtos {

    private AdminUserDtos() {
    }

    /** A-S02 표 한 줄. 연락처는 마스킹. org = 접수자 소속 기관 또는 농가 담당 축협 */
    record UserRow(Long usersId, String role, String name, String loginId, String phoneMasked, Long orgId,
            String orgName, String farmNo, String farmName, String status, Instant lastLoginAt) {
    }

    /** A-S03 · A-S03F 상세. 농가만 farm 이 채워진다 */
    record UserDetail(Long usersId, String role, String name, String loginId, String phoneMasked, String status,
            boolean mustChangePw, boolean locked, Instant lastLoginAt, Instant createdAt, Long orgId, String orgName,
            String inboxId, FarmSummary farm) {
    }

    /** 농장 현황은 건수만 (기록 내용은 보여주지 않음). records_30d = 최근 30일 일지 + 소 상태 기록 */
    record FarmSummary(Long farmId, String farmNo, String name, String address, String regionCode, Integer headCount,
            Long orgId, String orgName, long cowCount, long records30d, long deliveredCount) {
    }

    /**
     * ADM-03 계정 만들기 (A-P01). 역할별 필수 칸은 서비스에서 확인한다.
     * 농가: farm_no · name · phone (+ 주소 · 두수 · 담당 축협 org_id · region_code 선택)
     * 접수자: name · login_id · org_id (+ inbox_id · phone 선택) / 운영자: name · login_id (+ phone 선택)
     */
    record CreateUserRequest(
            @NotBlank(message = "역할을 골라 주세요.")
            @Pattern(regexp = "farmer|receiver|admin", message = "역할을 골라 주세요.")
            String role,
            @NotBlank(message = "이름을 넣어 주세요.") @Size(max = 50, message = "이름은 50자까지 넣을 수 있어요.")
            String name,
            @Pattern(regexp = "[0-9-]{9,20}", message = "연락처를 다시 확인해 주세요.")
            String phone,
            @Pattern(regexp = "\\d{6}", message = "농장식별번호 6자리를 넣어 주세요.")
            String farmNo,
            @Size(max = 100, message = "농장 이름은 100자까지 넣을 수 있어요.")
            String farmName,
            String address,
            @PositiveOrZero(message = "사육 두수를 다시 확인해 주세요.")
            Integer headCount,
            @Pattern(regexp = "\\d{10}", message = "법정동 코드 10자리를 넣어 주세요.")
            String regionCode,
            Long orgId,
            @Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "아이디는 영문·숫자 3~50자로 넣어 주세요.")
            String loginId,
            String inboxId) {
    }

    /** 만든 계정. temp_password 는 접수자·운영자만, 이 응답에서 한 번만 보여준다 */
    record CreateUserResponse(Long usersId, String role, String loginId, String tempPassword) {
    }

    /**
     * ADM-04 계정 수정 (A-S03 · A-S03F). 보낸 칸만 바꾼다.
     * 농가: phone · org_id(담당 축협) · status / 접수자: org_id · inbox_id · phone · status · reset_password
     */
    record UpdateUserRequest(
            @Pattern(regexp = "active|suspended", message = "상태를 다시 골라 주세요.")
            String status,
            @Pattern(regexp = "[0-9-]{9,20}", message = "연락처를 다시 확인해 주세요.")
            String phone,
            Long orgId,
            String inboxId,
            Boolean resetPassword) {
    }

    /** temp_password 는 reset_password 를 보냈을 때만 채워지고, 이 응답에서 한 번만 보여준다 */
    record UpdateUserResponse(UserDetail user, String tempPassword) {
    }
}
