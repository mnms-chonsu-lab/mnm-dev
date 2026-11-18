package com.mnm.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 공식 안내 문구·지식베이스 관리 요청·응답 (ADM-07~08, A-S05) */
final class AdminGuideDtos {

    private AdminGuideDtos() {
    }

    record GuideResponse(Long biosecurityRuleId, String kind, String disease, String stage, String text,
            String sourceDoc, String clause, LocalDate revisedAt, Integer displayOrder, String exampleImage,
            String partCode, String photoPart, List<String> yesRequiredParts, String status, String reviewer,
            int version) {
    }

    /** 편집 패널 내용 (전체 값). text 는 공식 자료 원문 그대로 */
    record GuideContent(
            @Size(max = 50, message = "질병명은 50자까지 넣을 수 있어요.")
            String disease,
            @Pattern(regexp = "suspect|after_report|regional", message = "대응 단계를 다시 골라 주세요.")
            String stage,
            @NotBlank(message = "문구를 넣어 주세요.")
            String text,
            @NotBlank(message = "원문 문서명을 넣어 주세요.") @Size(max = 200, message = "문서명은 200자까지 넣을 수 있어요.")
            String sourceDoc,
            @Size(max = 200, message = "조항은 200자까지 넣을 수 있어요.")
            String clause,
            LocalDate revisedAt,
            Integer displayOrder,
            @Size(max = 500, message = "예시 그림 주소가 너무 길어요.")
            String exampleImage,
            @Size(max = 30, message = "부위 코드는 30자까지 넣을 수 있어요.")
            String partCode,
            @Size(max = 30, message = "부위 이름은 30자까지 넣을 수 있어요.")
            String photoPart,
            List<String> yesRequiredParts) {
    }

    /** ADM-07 문구 등록. 분류를 먼저 고른다. 초안으로 시작한다 */
    record CreateGuideRequest(
            @NotBlank(message = "분류를 골라 주세요.")
            @Pattern(regexp = "question|photo_guide|action|form_field|summary|clause", message = "분류를 골라 주세요.")
            String kind,
            @NotNull(message = "문구 내용을 넣어 주세요.") @Valid
            GuideContent content) {
    }

    /**
     * ADM-08 수정 · 상태 변경. content 를 보내면 내용 전체를 바꾸고(게시된 문구면 새 버전 초안을 만든다),
     * status 를 보내면 상태를 바꾼다. 초안은 검수 중을 거쳐야 게시할 수 있다. reviewer 는 게시할 때 남긴다 (없으면 운영자 이름)
     */
    record UpdateGuideRequest(
            @Valid GuideContent content,
            @Pattern(regexp = "draft|review|published", message = "상태를 다시 골라 주세요.")
            String status,
            @Size(max = 50, message = "검수자는 50자까지 넣을 수 있어요.")
            String reviewer) {
    }
}
