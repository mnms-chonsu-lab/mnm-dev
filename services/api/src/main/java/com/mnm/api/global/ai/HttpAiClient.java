package com.mnm.api.global.ai;

import com.mnm.api.global.web.RequestIdFilter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/**
 * mnm-ai 내부 API 서버 호출 (docs/api/ai-internal.yaml). 같은 Docker 네트워크 안에서 X-Internal-Token 으로 부른다.
 * 연결 실패·시간 초과·오류 응답은 모두 {@link AiException}(AI 오류 코드)으로 바꾼다.
 * 재시도는 엔드포인트별 합의(mnm-dev#5)를 따른다. 요청 본문·서명 URL 은 로그에 남기지 않는다.
 */
@Slf4j
public class HttpAiClient implements AiClient {

    /** AI 서버 시간 제한(일반 8초 · 부위 확인 10초)보다 1초 길게 기다려 AI 서버의 504 를 먼저 받는다 */
    static final Duration TIMEOUT = Duration.ofSeconds(9);
    static final Duration PART_CHECK_TIMEOUT = Duration.ofSeconds(11);
    /** 503 이 이보다 빨리 오면 "바로 온 경우"로 보고 1회 재시도 (제안값, 실측으로 조정) */
    static final Duration FAST_FAIL = Duration.ofSeconds(2);

    /** AI 음성 인식은 확장자로 형식을 본다 (.webm · .m4a 만). 아이폰 녹음(audio/mp4)은 m4a */
    private static final Set<String> M4A_TYPES = Set.of("audio/mp4", "audio/m4a", "audio/x-m4a", "video/mp4");

    private enum Retry {
        NONE, CONNECT, CONNECT_OR_FAST_503
    }

    /** AI 서버 오류 본문 { code, message, detail } */
    private record AiError(String code, Map<String, Object> detail) {
    }

    private final String baseUrl;
    private final String token;
    private final ObjectMapper objectMapper;
    /** HTTP/2 업그레이드 요청을 uvicorn 이 지원하지 않아(경고 로그) 1.1 로 고정 */
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    HttpAiClient(String baseUrl, String token, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        this.objectMapper = objectMapper;
    }

    @Override
    public SttResult stt(byte[] audio, String contentType) {
        String type = contentType == null ? "audio/webm" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        String filename = M4A_TYPES.contains(type) ? "voice.m4a" : "voice.webm";
        String boundary = "mnm-" + UUID.randomUUID();
        HttpRequest request = request("/ai/stt", TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, filename, type, audio)))
                .build();
        return call("STT-FAIL", request, Retry.CONNECT_OR_FAST_503, SttResult.class);
    }

    @Override
    public RouteResult route(RouteRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", request.text());
        body.put("today", request.today());
        body.put("farm_context", Map.of("cows", request.cows()));
        return post("/ai/route", body, "LLM-FAIL", TIMEOUT, Retry.CONNECT_OR_FAST_503, RouteResult.class);
    }

    @Override
    public SummaryResult summary(SummaryRequest request) {
        // AI 요청 형식: cow.tag4 는 꼭 문자열, farm_total_head 는 1 이상. 소를 안 고른 기록은 빈 문자열로 보내고 응답에서 null 로 되돌린다
        SummaryCow cow = request.cow();
        boolean noCow = cow.tag4() == null;
        Integer heads = request.farmTotalHead() != null && request.farmTotalHead() > 0 ? request.farmTotalHead() : null;
        SummaryRequest sent = new SummaryRequest(request.recordId(), request.foundDate(),
                new SummaryCow(Objects.toString(cow.tag4(), ""), cow.desc(), cow.barn()), request.symptomAnswers(),
                request.photos(), request.diary(), request.reportFields(), heads);
        SummaryResult result = post("/ai/summary", sent, "LLM-FAIL", TIMEOUT, Retry.NONE, SummaryResult.class);
        if (!noCow || !(result.card().get("cow") instanceof Map<?, ?> cardCow)) {
            return result;
        }
        Map<String, Object> restoredCow = new LinkedHashMap<>();
        cardCow.forEach((k, v) -> restoredCow.put(String.valueOf(k), v));
        restoredCow.put("tag4", null);
        Map<String, Object> card = new LinkedHashMap<>(result.card());
        card.put("cow", restoredCow);
        return new SummaryResult(card, result.form6(), result.missing(), result.form6Text());
    }

    @Override
    public PartCheckResult partCheck(String imageUrl, String requestedPart) {
        Map<String, Object> body = Map.of("image_url", imageUrl, "requested_part", requestedPart);
        // 모델이 늦으면 AI 서버가 같은 호출 안에서 대체 판정을 하므로 재시도하지 않는다
        return post("/ai/part-check", body, "PART-FAIL", PART_CHECK_TIMEOUT, Retry.NONE, PartCheckResult.class);
    }

    @Override
    public BehaviorResult behavior(String videoUrl) {
        return post("/ai/behavior", Map.of("video_url", videoUrl), "BEHAVIOR-FAIL", TIMEOUT, Retry.CONNECT,
                BehaviorResult.class);
    }

    @Override
    public GuideResult guide(String disease, String stage, String query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("disease", disease);
        body.put("stage", stage);
        body.put("query", query);
        return post("/ai/guide", body, "LLM-FAIL", TIMEOUT, Retry.CONNECT_OR_FAST_503, GuideResult.class);
    }

    private <T> T post(String path, Object body, String failCode, Duration timeout, Retry retry, Class<T> type) {
        byte[] json;
        try {
            json = objectMapper.writeValueAsBytes(body);
        } catch (JacksonException e) {
            throw new AiException(failCode, "요청 만들기 실패");
        }
        HttpRequest request = request(path, timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json))
                .build();
        return call(failCode, request, retry, type);
    }

    private HttpRequest.Builder request(String path, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("X-Internal-Token", token);
        String requestId = RequestIdFilter.current();
        if (requestId != null) {
            builder.header("X-Request-Id", requestId);
        }
        return builder;
    }

    private <T> T call(String failCode, HttpRequest request, Retry retry, Class<T> type) {
        String path = request.uri().getPath();
        for (int attempt = 1; ; attempt++) {
            long started = System.nanoTime();
            HttpResponse<byte[]> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            } catch (HttpTimeoutException e) {
                if (e instanceof HttpConnectTimeoutException && attempt == 1 && retry != Retry.NONE) {
                    log.warn("AI 서버 연결 실패, 1회 다시 시도: {}", path);
                    continue;
                }
                throw new AiException(failCode, "시간 초과: " + path);
            } catch (IOException e) {
                if (attempt == 1 && retry != Retry.NONE) {
                    log.warn("AI 서버 연결 실패, 1회 다시 시도: {} ({})", path, e.getClass().getSimpleName());
                    continue;
                }
                throw new AiException(failCode, "연결 실패: " + path);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiException(failCode, "호출 중단: " + path);
            }
            int status = response.statusCode();
            if (status == 200) {
                return read(response.body(), type, failCode, path);
            }
            boolean fast = Duration.ofNanos(System.nanoTime() - started).compareTo(FAST_FAIL) < 0;
            if (status == 503 && fast && attempt == 1 && retry == Retry.CONNECT_OR_FAST_503) {
                log.warn("AI 서버 503, 1회 다시 시도: {}", path);
                continue;
            }
            throw error(response, failCode, path);
        }
    }

    private <T> T read(byte[] body, Class<T> type, String failCode, String path) {
        try {
            return objectMapper.readerFor(type).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(body);
        } catch (JacksonException e) {
            throw new AiException(failCode, "응답 형식 오류: " + path);
        }
    }

    private AiException error(HttpResponse<byte[]> response, String failCode, String path) {
        String code = failCode;
        Object reason = null;
        try {
            AiError error = read(response.body(), AiError.class, failCode, path);
            code = error.code() != null ? error.code() : failCode;
            reason = error.detail() == null ? null : error.detail().get("reason");
        } catch (AiException e) {
            // 본문이 AI 오류 형식이 아니면(프록시 오류 페이지 등) 기능별 기본 코드로
        }
        return new AiException(code, "%s HTTP %d reason=%s".formatted(path, response.statusCode(), reason));
    }

    static byte[] multipart(String boundary, String filename, String type, byte[] file) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(file.length + 256);
        out.writeBytes(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.writeBytes(file);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
