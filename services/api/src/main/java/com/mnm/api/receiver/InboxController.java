package com.mnm.api.receiver;

import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import com.mnm.api.receiver.InboxDtos.Form6Response;
import com.mnm.api.receiver.InboxDtos.InboxDetail;
import com.mnm.api.receiver.InboxDtos.InboxResponse;
import com.mnm.api.receiver.InboxDtos.MemoRequest;
import com.mnm.api.receiver.InboxDtos.ProxyRequest;
import com.mnm.api.receiver.InboxDtos.ProxyResponse;
import com.mnm.api.receiver.InboxDtos.StatusRequest;
import com.mnm.api.receiver.InboxDtos.StatusResponse;
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
@RequestMapping(ApiPaths.RECEIVER)
@RequiredArgsConstructor
class InboxController {

    private final InboxService inboxService;

    @Operation(summary = "관할 접수 목록 (RCV-01)",
            description = "status: all(기본) | new | checking | done. 최신순 20건, next_cursor 를 cursor 로 넘긴다")
    @GetMapping("/inbox")
    BaseResponse<InboxResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status, @RequestParam(required = false) String cursor) {
        return BaseResponse.of(inboxService.list(ReceiverSupport.userId(jwt), status, cursor));
    }

    @Operation(summary = "한 장 요약 (RCV-02)", description = "열람 감사 기록을 남긴다. 관할 밖이면 AUTH-403")
    @GetMapping("/inbox/{id}")
    BaseResponse<InboxDetail> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return BaseResponse.of(inboxService.detail(ReceiverSupport.userId(jwt), id));
    }

    @Operation(summary = "처리 상태 변경 (RCV-03)",
            description = "new→checking→done, done→checking(다시 열기). 보던 상태가 바뀌었으면 CONFLICT-409. 농가 알림 생성")
    @PatchMapping("/inbox/{id}/status")
    BaseResponse<StatusResponse> changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody StatusRequest request) {
        return BaseResponse.of(inboxService.changeStatus(ReceiverSupport.userId(jwt), id, request));
    }

    @Operation(summary = "통화·처리 메모 (RCV-04)", description = "새로 도착한 기록이면 확인 중으로 바뀐다")
    @PostMapping("/inbox/{id}/memos")
    @ResponseStatus(HttpStatus.CREATED)
    BaseResponse<StatusResponse> addMemo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody MemoRequest request) {
        return BaseResponse.of(inboxService.addMemo(ReceiverSupport.userId(jwt), id, request));
    }

    @Operation(summary = "신고서 6개 항목 (RCV-05)", description = "시행규칙 제13조. 소 상태 기록만 (이력 신고서는 NF-404)")
    @GetMapping("/inbox/{id}/form6")
    BaseResponse<Form6Response> form6(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return BaseResponse.of(inboxService.form6(ReceiverSupport.userId(jwt), id));
    }

    @Operation(summary = "이력 신고 초안 빈칸 채우기 · 대신 신고 마침 (RCV-10)",
            description = "id 는 history_report_id. done=true 면 완료 + 농가 알림 + 출생은 귀표 30일 할 일")
    @PatchMapping("/history-reports/{id}/proxy-done")
    BaseResponse<ProxyResponse> proxyDone(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody ProxyRequest request) {
        return BaseResponse.of(inboxService.proxyDone(ReceiverSupport.userId(jwt), id, request));
    }
}
