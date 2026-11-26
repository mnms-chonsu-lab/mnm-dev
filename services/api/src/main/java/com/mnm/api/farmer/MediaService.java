package com.mnm.api.farmer;

import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.symptom.BehaviorRecord;
import com.mnm.api.domain.symptom.FieldMedia;
import com.mnm.api.domain.symptom.SymptomReport;
import com.mnm.api.farmer.MediaDtos.BehaviorResponse;
import com.mnm.api.farmer.MediaDtos.PartCheckResponse;
import com.mnm.api.farmer.MediaDtos.UploadUrlRequest;
import com.mnm.api.farmer.MediaDtos.UploadUrlResponse;
import com.mnm.api.farmer.SymptomDtos.VideoResult;
import com.mnm.api.global.ai.AiClient;
import com.mnm.api.global.ai.AiClient.BehaviorResult;
import com.mnm.api.global.ai.AiClient.PartCheckResult;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.storage.StorageClient;
import com.mnm.api.global.storage.StorageClient.PostUpload;
import com.mnm.api.global.storage.StorageException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사진·영상·음성 원본 (FARM-12 · 13 · 21, 일지 음성). 파일은 앱이 저장소에 바로 올리고 서버는 주소만 만든다 (#24).
 * field_media 를 먼저 저장하고 AI 결과는 나중에 채운다. AI 가 실패해도 파일 기록은 남는다 (REQ-OPS-09).
 */
@Slf4j
@Service
@RequiredArgsConstructor
class MediaService {

    /** 사진 1장 최대 크기 (REQ-OPS-07) */
    static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    /** 영상 1개 최대 크기 (제안값, #37). 앞 10초만 분석하지만 앨범 영상은 길 수 있다 */
    static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
    /** 음성 원본 보관 기간. 버킷 수명 주기 규칙 audio/ 365일 삭제와 맞춘다 (F-S15D) */
    static final int AUDIO_KEEP_DAYS = 365;
    /** 업로드 URL 만료 (제안값, 실측으로 조정) */
    static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
    /** 앱이 AI 결과를 기다리는 시간. 넘으면 pending 으로 답하고 결과는 뒤에서 저장한다 (REQ-OPS-09) */
    static final Duration APP_WAIT = Duration.ofSeconds(3);
    /** 사진 부위 확인만 더 기다린다. SigLIP 2 + OpenAI 재확인이 2.7~3.6초라 3초면 경고가 자주 빠진다 (제안값, 실측으로 조정) */
    static final Duration PHOTO_WAIT = Duration.ofSeconds(5);

    private static final Map<String, String> PHOTO_TYPES = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/heic", "heic");
    private static final Map<String, String> VIDEO_TYPES = Map.of("video/mp4", "mp4", "video/quicktime", "mov");
    private static final Map<String, String> AUDIO_TYPES = Map.of(
            "audio/webm", "webm", "video/webm", "webm", "audio/mp4", "m4a", "audio/m4a", "m4a", "audio/x-m4a", "m4a",
            "video/mp4", "m4a");
    /** AI 호출을 요청 스레드와 떼어 둔다 (앱 응답 후에도 결과를 저장하려고) */
    private static final ExecutorService AI_CALLS = Executors.newVirtualThreadPerTaskExecutor();

    private final FarmerSupport support;
    private final FarmerSymptomReportRepository reportRepository;
    private final FarmerMediaRepository mediaRepository;
    private final FarmerBehaviorRepository behaviorRepository;
    private final StorageClient storage;
    private final AiClient aiClient;
    private final TransactionTemplate transaction;

    /** FARM-12 업로드 URL. field_media 를 먼저 만들고 경로 field/{farm_id}/{report_id}/{media_id}.{확장자} 로 받는다 */
    @Transactional
    UploadUrlResponse uploadUrl(Long userId, UploadUrlRequest request) {
        FarmInfo farm = support.farm(userId);
        SymptomReport report = reportRepository.findByIdAndFarmId(request.symptomReportId(), farm.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "소 상태 기록 없음"));
        boolean photo = "photo".equals(request.kind());
        String contentType = baseType(request.contentType());
        String ext = (photo ? PHOTO_TYPES : VIDEO_TYPES).get(contentType);
        if (ext == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, photo ? "사진 파일만 올릴 수 있어요." : "영상 파일만 올릴 수 있어요.",
                    "형식 아님");
        }
        if (photo && request.requestedPart() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "어느 부위 사진인지 골라 주세요.", "부위 없음");
        }
        FieldMedia media = mediaRepository.save(FieldMedia.create(report, request.kind(), photo ? request.requestedPart() : null));
        String key = "field/%d/%d/%d.%s".formatted(farm.getId(), report.getId(), media.getId(), ext);
        media.placeAt(key);
        PostUpload post = storage.presignPost(key, contentType, photo ? MAX_PHOTO_BYTES : MAX_VIDEO_BYTES, UPLOAD_TTL);
        return new UploadUrlResponse(media.getId(), post.url(), post.fields(), post.expiresAt());
    }

    /** FARM-13 부위 확인. AI 동안 DB 트랜잭션을 잡지 않으려고 @Transactional 을 붙이지 않는다 */
    PartCheckResponse partCheck(Long userId, Long mediaId) {
        FieldMedia media = uploaded(userId, mediaId, "photo");
        String url = storage.presignGet(media.getFilePath(), StorageClient.VIEW_TTL);
        Outcome<PartCheckResult> outcome = await(() -> {
            PartCheckResult r = aiClient.partCheck(url, media.getRequestedPart());
            transaction.executeWithoutResult(s -> mediaRepository.findById(mediaId).ifPresent(m -> m.recordPartCheck(
                    r.predictedPart(), r.match(), decimal(r.score(), 4), r.modelVersion(),
                    decimal(r.quality().brightness(), 2))));
            return r;
        }, "부위 확인", PHOTO_WAIT);
        PartCheckResult r = outcome.value();
        return r == null ? new PartCheckResponse(mediaId, outcome.status(), null, null, null, null)
                : new PartCheckResponse(mediaId, "done", r.match(), r.quality().dark(), r.quality().blurry(),
                        "none".equals(r.predictedPart()));
    }

    /** FARM-21 행동 영상 분류. 이미 분류한 영상이면 저장된 비율을 돌려준다 */
    BehaviorResponse behavior(Long userId, Long mediaId) {
        FieldMedia media = uploaded(userId, mediaId, "video");
        List<BehaviorRecord> saved = behaviorRepository.findByMediaId(mediaId);
        if (!saved.isEmpty()) {
            return new BehaviorResponse(mediaId, "done", BehaviorRecord.ratio(saved));
        }
        String url = storage.presignGet(media.getFilePath(), StorageClient.VIEW_TTL);
        Outcome<Map<String, Integer>> outcome = await(() -> {
            BehaviorResult r = aiClient.behavior(url);
            return transaction.execute(s -> {
                FieldMedia m = mediaRepository.findById(mediaId).orElseThrow();
                m.recordModel(r.modelVersion());
                // shortcut: 촬영 시각을 몰라 분류한 시각 + 영상 안 시각으로 둔다. 앱이 촬영 시각을 보내게 되면 그 값으로
                Instant base = Instant.now();
                List<BehaviorRecord> records = r.frames().stream()
                        .map(f -> BehaviorRecord.of(m, m.getSymptomReport().getCow(), f.label(), decimal(f.confidence(), 4),
                                base.plusMillis(Math.round(f.t() * 1000)), r.modelVersion()))
                        .toList();
                behaviorRepository.saveAll(records);
                return BehaviorRecord.ratio(records);
            });
        }, "행동 분류", APP_WAIT);
        return new BehaviorResponse(mediaId, outcome.status(), outcome.value());
    }

    /** 일지 음성 원본 저장 (audio/{farm_id}/{media_id}.{확장자}, 수명 주기 규칙을 audio/ 하나로 걸려고). 실패해도 기록은 계속한다 (null) */
    Long storeAudio(FarmInfo farm, byte[] audio, String contentType) {
        FieldMedia media = mediaRepository.save(FieldMedia.create(null, "audio", null));
        String key = "audio/%d/%d.%s".formatted(farm.getId(), media.getId(),
                AUDIO_TYPES.getOrDefault(baseType(contentType), "audio"));
        try {
            storage.put(key, audio, contentType);
        } catch (StorageException e) {
            log.warn("음성 원본 저장 실패, 원본 없이 진행: {}", e.getMessage());
            mediaRepository.delete(media);
            return null;
        }
        media.placeAt(key);
        mediaRepository.save(media);
        return media.getId();
    }

    /** 일지 저장(FARM-04) 때 붙일 내 음성 원본 */
    FieldMedia ownAudio(FarmInfo farm, Long mediaId) {
        return mediaRepository.findById(mediaId)
                .filter(m -> "audio".equals(m.getKind()) && m.getFilePath().startsWith("audio/" + farm.getId() + "/"))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "음성 원본 없음"));
    }

    /**
     * 일지 음성 [듣기] 주소 (FARM-26). 보관 기간이 지났으면 null (F-S15D).
     * shortcut: field_media 에 수집 시각이 없어 일지 날짜로 센다 (미래 날짜는 못 넣어 업로드일 이하). 정확히 하려면 mnm-data 에 created_at 추가
     */
    String audioUrl(FieldMedia audio, LocalDate recordedOn) {
        return FarmerRules.today().isBefore(recordedOn.plusDays(AUDIO_KEEP_DAYS)) ? viewUrl(audio) : null;
    }

    /** 열람 서명 URL (만료 10분). 아직 경로가 없으면 null */
    String viewUrl(FieldMedia media) {
        return media == null || FieldMedia.PENDING_PATH.equals(media.getFilePath()) ? null
                : storage.presignGet(media.getFilePath(), StorageClient.VIEW_TTL);
    }

    /** 소 상태 기록의 마지막 영상과 행동 비율 (FARM-14). 트랜잭션 안에서 부른다 */
    VideoResult video(Long reportId) {
        List<FieldMedia> videos = mediaRepository.findBySymptomReportIdAndKindOrderByIdAsc(reportId, "video");
        if (videos.isEmpty()) {
            return null;
        }
        FieldMedia last = videos.getLast();
        return new VideoResult(last.getId(), viewUrl(last), BehaviorRecord.ratio(behaviorRepository.findByMediaId(last.getId())));
    }

    private FieldMedia uploaded(Long userId, Long mediaId, String kind) {
        FarmInfo farm = support.farm(userId);
        FieldMedia media = mediaRepository.findByIdAndSymptomReportFarmId(mediaId, farm.getId())
                .filter(m -> kind.equals(m.getKind()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "파일 없음"));
        if (!storage.exists(media.getFilePath())) {
            throw new BusinessException(ErrorCode.UPLOAD_FAIL, "저장소에 파일 없음");
        }
        return media;
    }

    /** status: done | pending(앱 대기 시간 초과, 호출은 뒤에서 끝까지 진행해 결과 저장) | failed(확인 안 됨) */
    private record Outcome<T>(String status, T value) {
    }

    private <T> Outcome<T> await(Supplier<T> call, String what, Duration wait) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(call, AI_CALLS);
        try {
            return new Outcome<>("done", future.get(wait.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            future.whenComplete((r, ex) -> {
                if (ex != null) {
                    log.warn("{} 실패 (늦은 호출), 확인 안 됨으로 둠: {}", what, ex.getMessage());
                }
            });
            return new Outcome<>("pending", null);
        } catch (ExecutionException e) {
            log.warn("{} 실패, 확인 안 됨으로 둠: {}", what, e.getCause().getMessage());
            return new Outcome<>("failed", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome<>("pending", null);
        }
    }

    private static String baseType(String contentType) {
        return contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase();
    }

    private static BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
