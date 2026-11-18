package com.mnm.api.auth;

import com.mnm.api.auth.AuthDtos.FarmerLoginRequest;
import com.mnm.api.auth.AuthDtos.LoginResponse;
import com.mnm.api.auth.AuthDtos.MeResponse;
import com.mnm.api.auth.AuthDtos.PasswordChangeRequest;
import com.mnm.api.auth.AuthDtos.StaffLoginRequest;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.AUTH)
@RequiredArgsConstructor
class AuthController {

    private final AuthService authService;

    @Operation(summary = "농가 로그인 (AUTH-01)", description = "농장식별번호 + 휴대폰 번호. 5번 틀리면 10분 잠금 (AUTH-LOCK)")
    @SecurityRequirements
    @PostMapping("/farmer/login")
    BaseResponse<LoginResponse> loginFarmer(@Valid @RequestBody FarmerLoginRequest request) {
        return BaseResponse.of(authService.loginFarmer(request.farmNo(), request.phone()));
    }

    @Operation(summary = "접수자·운영자 로그인 (AUTH-02)", description = "must_change_pw 가 true 면 비밀번호 변경(AUTH-04) 전까지 다른 API 는 AUTH-403")
    @SecurityRequirements
    @PostMapping("/login")
    BaseResponse<LoginResponse> loginStaff(@Valid @RequestBody StaffLoginRequest request) {
        return BaseResponse.of(authService.loginStaff(request.loginId(), request.password()));
    }

    @Operation(summary = "내 계정·역할·소속 (AUTH-03)")
    @GetMapping("/me")
    BaseResponse<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return BaseResponse.of(authService.me(userId(jwt)));
    }

    @Operation(summary = "임시 비밀번호 변경 (AUTH-04)", description = "임시 비밀번호로 로그인한 상태에서만. 새 토큰을 돌려준다")
    @PostMapping("/password")
    BaseResponse<LoginResponse> changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeRequest request) {
        return BaseResponse.of(authService.changePassword(userId(jwt), request.newPassword()));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
