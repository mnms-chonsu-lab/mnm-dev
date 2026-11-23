package com.mnm.api.global.storage;

/** 저장소 호출 실패. 부르는 쪽이 사용자 흐름을 멈출지(업로드 실패) 넘어갈지(음성 원본) 정한다 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
