package com.mnm.api.global.response;

import java.util.List;

/**
 * 목록 응답: BaseResponse&lt;PageResponse&lt;T&gt;&gt; 로 감싸서 쓴다.
 * 무한 스크롤 기준 커서 방식. 다음 요청에 next_cursor 를 그대로 넘긴다 (마지막이면 null).
 */
public record PageResponse<T>(List<T> items, boolean hasNext, String nextCursor) {
}
