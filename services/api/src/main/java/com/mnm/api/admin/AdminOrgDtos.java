package com.mnm.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 접수 기관 관리 요청·응답 (ADM-05, A-S09) */
final class AdminOrgDtos {

    private AdminOrgDtos() {
    }

    /** receiver_org.inboxes 의 한 칸 (DB 에 JSON 으로 저장되는 모양 그대로) */
    record Inbox(String inboxId, String type, String name, String phone) {
    }

    /** receiver_count 가 0 인 수신함은 화면에 "받는 사람 없음" 경고 */
    record InboxResponse(String inboxId, String type, String name, String phone, long receiverCount) {
    }

    record OrgResponse(Long orgId, String name, String type, List<String> regionCodes, String phone,
            List<InboxResponse> inboxes, long receiverCount) {
    }

    /** ADM-05 기관 추가 · 편집 패널 저장 (전체 값). 관할 지역은 법정동 코드 10자리 */
    record OrgRequest(
            @NotBlank(message = "기관 이름을 넣어 주세요.") @Size(max = 100, message = "기관 이름은 100자까지 넣을 수 있어요.")
            String name,
            @NotBlank(message = "기관 종류를 골라 주세요.")
            @Pattern(regexp = "축협|방역기관|시군청", message = "기관 종류를 골라 주세요.")
            String type,
            @NotEmpty(message = "관할 지역을 하나 이상 넣어 주세요.")
            List<@Pattern(regexp = "\\d{10}", message = "법정동 코드 10자리를 넣어 주세요.") String> regionCodes,
            @Pattern(regexp = "[0-9-]{9,20}", message = "연락처를 다시 확인해 주세요.")
            String phone,
            @NotNull(message = "수신함 목록을 보내 주세요.")
            List<@Valid InboxRequest> inboxes) {
    }

    /** inbox_id 가 없으면 새 수신함 (서버가 만든다). 있으면 기존 수신함 수정 */
    record InboxRequest(
            @Pattern(regexp = "[A-Za-z0-9._-]{1,50}", message = "수신함을 다시 확인해 주세요.")
            String inboxId,
            @NotBlank(message = "수신함 종류를 골라 주세요.")
            @Pattern(regexp = "history|biosecurity", message = "수신함 종류를 골라 주세요.")
            String type,
            @NotBlank(message = "수신함 이름을 넣어 주세요.")
            String name,
            @Pattern(regexp = "[0-9-]{9,20}", message = "연락처를 다시 확인해 주세요.")
            String phone) {
    }
}
