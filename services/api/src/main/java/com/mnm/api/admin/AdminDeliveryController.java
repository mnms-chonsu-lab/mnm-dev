package com.mnm.api.admin;

import com.mnm.api.admin.AdminDeliveryService.DeliveryRow;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import com.mnm.api.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.ADMIN + "/deliveries")
@RequiredArgsConstructor
class AdminDeliveryController {

    private final AdminDeliveryService deliveryService;

    @Operation(summary = "신고·전달 현황 (ADM-06, A-S04)",
            description = "route(call · coop · web · gov) · status(delivered · queued · failed) · org_id(받는 기관 또는 함께 받는 축협) · "
                    + "승인 시각 [from, to) 로 거른다. 최근순 20개씩, next_cursor 를 cursor 로 넘긴다")
    @GetMapping
    BaseResponse<PageResponse<DeliveryRow>> list(@RequestParam(required = false) String route,
            @RequestParam(required = false) String status, @RequestParam(name = "org_id", required = false) Long orgId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String cursor) {
        return BaseResponse.of(deliveryService.list(route, status, orgId, from, to, cursor));
    }

    @Operation(summary = "다시 보내기 (ADM-06, A-S04)", description = "대기·실패 건을 접수자 수신함에 넣는다. 이미 도착했으면 CONFLICT-409")
    @PostMapping("/{id}/retry")
    BaseResponse<DeliveryRow> retry(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return BaseResponse.of(deliveryService.retry(AdminSupport.userId(jwt), id));
    }
}
