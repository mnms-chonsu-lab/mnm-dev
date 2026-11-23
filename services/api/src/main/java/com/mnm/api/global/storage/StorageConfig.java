package com.mnm.api.global.storage;

import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** STORAGE_ENDPOINT 가 있으면 실제 저장소, 없으면 가짜 저장소. 로컬에서는 스테이징 버킷을 쓰지 않는다 */
@Slf4j
@Configuration
class StorageConfig {

    @Bean
    StorageClient storageClient(
            @Value("${mnm.storage.endpoint:}") String endpoint,
            @Value("${mnm.storage.region:kr-standard}") String region,
            @Value("${mnm.storage.bucket:}") String bucket,
            @Value("${mnm.storage.access-key:}") String accessKey,
            @Value("${mnm.storage.secret-key:}") String secretKey,
            ObjectMapper objectMapper) {
        if (endpoint.isBlank()) {
            log.warn("STORAGE_ENDPOINT 없음: 가짜 저장소를 씁니다 (파일이 실제로 저장되지 않음)");
            return new MockStorageClient();
        }
        if (bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("STORAGE_BUCKET · STORAGE_ACCESS_KEY · STORAGE_SECRET_KEY 를 함께 넣어 주세요");
        }
        return new S3StorageClient(endpoint, region, bucket, accessKey, secretKey, objectMapper, Clock.systemUTC());
    }
}
