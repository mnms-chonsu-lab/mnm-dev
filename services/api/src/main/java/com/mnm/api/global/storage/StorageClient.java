package com.mnm.api.global.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Object Storage (사진·영상·음성 원본). 버킷은 비공개이고 앱·AI 서버는 서명 URL로만 읽고 쓴다 (REQ-OPS-07).
 * 경로 규칙: field/{farm_id}/{report_id}/{media_id}.{확장자}, 일지 음성은 field/{farm_id}/diary/{media_id}.{확장자} (REQ-DATA-09).
 * 실패하면 {@link StorageException}을 던진다.
 */
public interface StorageClient {

    /** 열람 서명 URL 만료 (REQ-OPS-07) */
    Duration VIEW_TTL = Duration.ofMinutes(10);

    /** 브라우저가 버킷에 바로 올리는 POST 양식. 크기 1~maxBytes, Content-Type 이 다르면 버킷이 거절한다 */
    PostUpload presignPost(String key, String contentType, long maxBytes, Duration ttl);

    /** 열람(GET) 서명 URL */
    String presignGet(String key, Duration ttl);

    /** 서버가 직접 올린다 (음성 원본) */
    void put(String key, byte[] body, String contentType);

    /** 파일이 올라와 있는지 */
    boolean exists(String key);

    /** url 로 multipart/form-data POST. fields 를 먼저 넣고 마지막에 file */
    record PostUpload(String url, Map<String, String> fields, Instant expiresAt) {
    }
}
