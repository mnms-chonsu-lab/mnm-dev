package com.mnm.api.farmer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Map;

final class MediaDtos {

    private MediaDtos() {
    }

    /**
     * FARM-12 업로드 URL 요청. requested_part 는 사진일 때 필수.
     * content_type: 사진 image/jpeg · image/png · image/heic, 영상 video/mp4 · video/quicktime
     */
    record UploadUrlRequest(
            @NotNull(message = "소 상태 기록을 먼저 저장해 주세요.") Long symptomReportId,
            @NotBlank(message = "사진인지 영상인지 골라 주세요.")
            @Pattern(regexp = "photo|video", message = "사진인지 영상인지 골라 주세요.") String kind,
            @Pattern(regexp = "mouth|udder|hoof|flank", message = "어느 부위 사진인지 골라 주세요.") String requestedPart,
            @NotBlank(message = "사진이나 영상 파일을 골라 주세요.") String contentType) {
    }

    /**
     * 앱은 upload_url 로 multipart/form-data POST 를 보낸다. fields 를 모두 넣고 마지막에 file.
     * 사진 10MB · 영상 100MB 를 넘거나 Content-Type 이 다르면 저장소가 거절한다.
     */
    record UploadUrlResponse(Long mediaId, String uploadUrl, Map<String, String> fields, Instant expiresAt) {
    }

    /**
     * FARM-13 부위 확인. status: done | pending(3초 안에 안 끝남, 결과는 FARM-14 에서) | failed(확인 안 됨, 사진은 저장됨).
     * match·dark·blurry 는 done 일 때만 값이 있다
     */
    record PartCheckResponse(Long mediaId, String status, Boolean match, Boolean dark, Boolean blurry) {
    }

    /** FARM-21 행동 영상. ratio: stand · lie · eat (%, 참고). done 이 아니면 null */
    record BehaviorResponse(Long mediaId, String status, Map<String, Integer> ratio) {
    }
}
