package com.mnm.api.admin;

import com.mnm.api.admin.AdminAuditLogService.AuditLogRow;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import com.mnm.api.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.ADMIN + "/audit-logs")
@RequiredArgsConstructor
class AdminAuditLogController {

    private final AdminAuditLogService auditLogService;

    @Operation(summary = "감사 로그 (ADM-12, A-S08)",
            description = "users_id · action · target_type · 기간 [from, to) 로 거른다. 최근순 20개씩, next_cursor 를 cursor 로 넘긴다")
    @GetMapping
    BaseResponse<PageResponse<AuditLogRow>> list(@RequestParam(name = "users_id", required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(name = "target_type", required = false) String targetType,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String cursor) {
        return BaseResponse.of(auditLogService.list(userId, action, targetType, from, to, cursor));
    }
}
