package com.mnm.api.global.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 저장소 없이 흐름을 돌리기 위한 가짜 구현 (STORAGE_ENDPOINT 가 비었을 때, 테스트).
 * 돌려주는 주소는 실제로 열리지 않는다. 올라왔는지 확인(exists)은 항상 "있음"으로 본다.
 * shortcut: 로컬에서 실제 업로드까지 보려면 S3 호환 컨테이너(MinIO 등)를 STORAGE_ENDPOINT 로 지정한다.
 */
public class MockStorageClient implements StorageClient {

    static final String BASE_URL = "http://localhost/mock-storage/";

    @Override
    public PostUpload presignPost(String key, String contentType, long maxBytes, Duration ttl) {
        return new PostUpload(BASE_URL, Map.of("key", key, "Content-Type", contentType), Instant.now().plus(ttl));
    }

    @Override
    public String presignGet(String key, Duration ttl) {
        return BASE_URL + key;
    }

    @Override
    public void put(String key, byte[] body, String contentType) {
        // 저장하지 않는다
    }

    @Override
    public boolean exists(String key) {
        return true;
    }
}
