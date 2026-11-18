package com.mnm.api.admin;

import com.mnm.api.admin.AdminUserDtos.CreateUserRequest;
import com.mnm.api.admin.AdminUserDtos.CreateUserResponse;
import com.mnm.api.admin.AdminUserDtos.UpdateUserRequest;
import com.mnm.api.admin.AdminUserDtos.UpdateUserResponse;
import com.mnm.api.admin.AdminUserDtos.UserDetail;
import com.mnm.api.admin.AdminUserDtos.UserRow;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import com.mnm.api.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.ADMIN + "/users")
@RequiredArgsConstructor
class AdminUserController {

    private final AdminUserService userService;

    @Operation(summary = "사용자 목록·검색 (ADM-02, A-S02)",
            description = "role · status · org_id(소속 기관 또는 담당 축협) · q(이름·아이디·연락처·농장명·농장식별번호). 연락처는 마스킹. 20개씩, next_cursor 를 cursor 로 넘긴다")
    @GetMapping
    BaseResponse<PageResponse<UserRow>> list(@RequestParam(required = false) String role,
            @RequestParam(required = false) String status, @RequestParam(name = "org_id", required = false) Long orgId,
            @RequestParam(required = false) String q, @RequestParam(required = false) String cursor) {
        return BaseResponse.of(userService.list(role, status, orgId, q, cursor));
    }

    @Operation(summary = "사용자 상세 (ADM-02, A-S03 · A-S03F)", description = "농가는 농장 현황 건수만 (기록 내용 없음)")
    @GetMapping("/{id}")
    BaseResponse<UserDetail> detail(@PathVariable Long id) {
        return BaseResponse.of(userService.detail(id));
    }

    @Operation(summary = "계정 만들기 (ADM-03, A-P01)",
            description = "농가는 농장도 함께 만든다(비밀번호 없음). 접수자·운영자는 temp_password 를 이 응답에서만 한 번 보여준다. 농장식별번호·휴대폰·아이디 중복이면 CONFLICT-409")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BaseResponse<CreateUserResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateUserRequest request) {
        return BaseResponse.of(userService.create(AdminSupport.userId(jwt), request));
    }

    @Operation(summary = "계정 수정 · 정지 · 비밀번호 초기화 (ADM-04, A-S03 · A-S03F)",
            description = "보낸 칸만 바꾼다. reset_password=true 면 새 temp_password 를 한 번 보여주고 다음 로그인 때 비밀번호를 바꾸게 한다(접수자·운영자만)")
    @PatchMapping("/{id}")
    BaseResponse<UpdateUserResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return BaseResponse.of(userService.update(AdminSupport.userId(jwt), id, request));
    }
}
