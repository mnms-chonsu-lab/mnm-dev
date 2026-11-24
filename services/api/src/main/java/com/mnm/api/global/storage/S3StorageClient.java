package com.mnm.api.global.storage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;

/**
 * S3 호환 저장소(네이버 Object Storage) 구현. 서명은 AWS Signature V4 (#24 점검과 같은 s3v4), 주소는 path-style
 * ({endpoint}/{bucket}/{key}). Java AWS SDK 는 POST 업로드 정책(content-length-range)을 만들지 못해 JDK 만으로 서명한다.
 */
public class S3StorageClient implements StorageClient {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    // 스토리지는 초 아래 9자리 expiration 을 400(MalformedPOSTRequest)으로 거절한다. AWS 문서 예시 형식만 쓴다
    private static final DateTimeFormatter POLICY_EXPIRATION = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final Duration SERVER_TTL = Duration.ofMinutes(1);

    private final URI endpoint;
    private final String region;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public S3StorageClient(String endpoint, String region, String bucket, String accessKey, String secretKey,
            ObjectMapper objectMapper, Clock clock) {
        this.endpoint = URI.create(endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint);
        this.region = region;
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public PostUpload presignPost(String key, String contentType, long maxBytes, Duration ttl) {
        Instant now = clock.instant();
        Instant expires = now.plus(ttl).truncatedTo(ChronoUnit.SECONDS);
        String amzDate = AMZ_DATE.format(now);
        String credential = accessKey + "/" + scope(amzDate);
        List<Object> conditions = List.of(
                Map.of("bucket", bucket),
                List.of("eq", "$key", key),
                List.of("eq", "$Content-Type", contentType),
                List.of("content-length-range", 1, maxBytes),
                Map.of("x-amz-algorithm", ALGORITHM),
                Map.of("x-amz-credential", credential),
                Map.of("x-amz-date", amzDate));
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("expiration", POLICY_EXPIRATION.format(expires));
        policy.put("conditions", conditions);
        String encoded = Base64.getEncoder().encodeToString(
                objectMapper.writeValueAsString(policy).getBytes(StandardCharsets.UTF_8));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", key);
        fields.put("Content-Type", contentType);
        fields.put("x-amz-algorithm", ALGORITHM);
        fields.put("x-amz-credential", credential);
        fields.put("x-amz-date", amzDate);
        fields.put("policy", encoded);
        fields.put("x-amz-signature", hex(hmac(signingKey(amzDate.substring(0, 8)), encoded)));
        return new PostUpload(endpoint + "/" + bucket, fields, expires);
    }

    @Override
    public String presignGet(String key, Duration ttl) {
        return presign("GET", key, ttl);
    }

    @Override
    public void put(String key, byte[] body, String contentType) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(presign("PUT", key, SERVER_TTL)))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        int status = send(request);
        if (status / 100 != 2) {
            throw new StorageException("업로드 실패: HTTP " + status);
        }
    }

    @Override
    public boolean exists(String key) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(presign("HEAD", key, SERVER_TTL)))
                .timeout(Duration.ofSeconds(5))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        int status = send(request);
        if (status == 200) {
            return true;
        }
        // 키에 목록 권한이 없으면 없는 파일도 403 으로 온다
        if (status == 404 || status == 403) {
            return false;
        }
        throw new StorageException("파일 확인 실패: HTTP " + status);
    }

    private int send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            throw new StorageException("저장소 연결 실패", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageException("저장소 호출 중단", e);
        }
    }

    /** 쿼리 서명 URL (서명 헤더는 host 만, 본문은 UNSIGNED-PAYLOAD) */
    String presign(String method, String key, Duration ttl) {
        String amzDate = AMZ_DATE.format(clock.instant());
        String host = endpoint.getPort() == -1 ? endpoint.getHost() : endpoint.getHost() + ":" + endpoint.getPort();
        String path = "/" + bucket + "/" + encodePath(key);
        Map<String, String> query = new TreeMap<>();
        query.put("X-Amz-Algorithm", ALGORITHM);
        query.put("X-Amz-Credential", accessKey + "/" + scope(amzDate));
        query.put("X-Amz-Date", amzDate);
        query.put("X-Amz-Expires", String.valueOf(ttl.toSeconds()));
        query.put("X-Amz-SignedHeaders", "host");
        String signature = signQuery(method, host, path, query, amzDate);
        return endpoint.getScheme() + "://" + host + path + "?" + canonicalQuery(query) + "&X-Amz-Signature=" + signature;
    }

    /** SigV4 쿼리 서명. 테스트에서 AWS 문서 예시 값으로 확인한다 */
    String signQuery(String method, String host, String path, Map<String, String> query, String amzDate) {
        String canonicalRequest = String.join("\n", method, path, canonicalQuery(query), "host:" + host, "", "host",
                "UNSIGNED-PAYLOAD");
        String stringToSign = String.join("\n", ALGORITHM, amzDate, scope(amzDate), hex(sha256(canonicalRequest)));
        return hex(hmac(signingKey(amzDate.substring(0, 8)), stringToSign));
    }

    private String scope(String amzDate) {
        return amzDate.substring(0, 8) + "/" + region + "/s3/aws4_request";
    }

    private byte[] signingKey(String date) {
        byte[] key = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        key = hmac(key, region);
        key = hmac(key, "s3");
        return hmac(key, "aws4_request");
    }

    private static String canonicalQuery(Map<String, String> query) {
        List<String> parts = new ArrayList<>();
        new TreeMap<>(query).forEach((k, v) -> parts.add(uriEncode(k) + "=" + uriEncode(v)));
        return String.join("&", parts);
    }

    private static String encodePath(String key) {
        List<String> parts = new ArrayList<>();
        for (String segment : key.split("/", -1)) {
            parts.add(uriEncode(segment));
        }
        return String.join("/", parts);
    }

    /** RFC 3986: 영문·숫자·-_.~ 외에는 모두 %XX */
    private static String uriEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(String data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
