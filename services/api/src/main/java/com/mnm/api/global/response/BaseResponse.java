package com.mnm.api.global.response;

import com.mnm.api.global.web.RequestIdFilter;

/** 성공 응답의 형식: { data, request_id }. 본문이 없으면 BaseResponse 대신 204 를 쓴다. */
public record BaseResponse<T>(T data, String requestId) {

    public static <T> BaseResponse<T> of(T data) {
        return new BaseResponse<>(data, RequestIdFilter.current());
    }
}
