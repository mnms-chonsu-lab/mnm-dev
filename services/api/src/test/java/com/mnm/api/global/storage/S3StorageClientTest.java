package com.mnm.api.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 서명 계산 확인. 값은 AWS 문서의 SigV4 쿼리 서명 예시 (examplebucket/test.txt) */
class S3StorageClientTest {

    private static final Instant AT = Instant.parse("2013-05-24T00:00:00Z");
    private final ObjectMapper json = JsonMapper.builder().build();
    private final S3StorageClient client = new S3StorageClient("https://kr.object.ncloudstorage.com/", "us-east-1",
            "examplebucket", "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", json,
            Clock.fixed(AT, ZoneOffset.UTC));

    @Test
    void 쿼리_서명은_AWS_예시와_같다() {
        Map<String, String> query = new TreeMap<>(Map.of(
                "X-Amz-Algorithm", "AWS4-HMAC-SHA256",
                "X-Amz-Credential", "AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request",
                "X-Amz-Date", "20130524T000000Z",
                "X-Amz-Expires", "86400",
                "X-Amz-SignedHeaders", "host"));
        assertThat(client.signQuery("GET", "examplebucket.s3.amazonaws.com", "/test.txt", query, "20130524T000000Z"))
                .isEqualTo("aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404");
    }

    @Test
    void 열람_주소는_path_style_이고_경로를_인코딩한다() {
        String url = client.presignGet("field/1/7/77 사진.jpg", Duration.ofMinutes(10));
        assertThat(url).startsWith("https://kr.object.ncloudstorage.com/examplebucket/field/1/7/77%20%EC%82%AC%EC%A7%84.jpg?")
                .contains("X-Amz-Expires=600", "X-Amz-SignedHeaders=host", "X-Amz-Signature=");
    }

    @Test
    void 업로드_양식_정책에_경로_형식_크기_제한이_들어간다() {
        StorageClient.PostUpload post = client.presignPost("field/1/7/77.jpg", "image/jpeg", 10, Duration.ofMinutes(10));
        assertThat(post.url()).isEqualTo("https://kr.object.ncloudstorage.com/examplebucket");
        assertThat(post.expiresAt()).isEqualTo(AT.plus(Duration.ofMinutes(10)));
        assertThat(post.fields()).containsEntry("key", "field/1/7/77.jpg").containsEntry("Content-Type", "image/jpeg")
                .containsKeys("policy", "x-amz-signature", "x-amz-credential", "x-amz-date");
        String policy = new String(Base64.getDecoder().decode(post.fields().get("policy")), StandardCharsets.UTF_8);
        assertThat(policy).contains("\"expiration\":\"2013-05-24T00:10:00Z\"", "[\"content-length-range\",1,10]",
                "[\"eq\",\"$key\",\"field/1/7/77.jpg\"]", "{\"bucket\":\"examplebucket\"}");
    }
}
