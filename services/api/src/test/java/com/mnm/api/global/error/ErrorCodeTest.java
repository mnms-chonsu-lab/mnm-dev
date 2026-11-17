package com.mnm.api.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 기능명세서 "문구 규칙"을 ErrorCode 기본 문구에 강제한다. 새 코드를 추가할 때 같이 확인된다. */
class ErrorCodeTest {

    /** 명세서 문구 규칙의 금지 표현 */
    private static final List<String> FORBIDDEN =
            List.of("진단", "확률", "위험도", "이상", "질병 의심", "감염", "양성", "판정", "자동 신고");

    @Test
    void 코드는_중복되지_않는다() {
        List<String> codes = Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode).toList();
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void 기본_문구는_금지_표현_코드_변수_없이_해요체로_끝난다() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            String message = errorCode.getMessage();
            assertThat(message).as(errorCode.name()).isNotBlank()
                    .doesNotContain(errorCode.getCode())
                    .doesNotContain("{")
                    .matches(".*[요?.]$");
            FORBIDDEN.forEach(word -> assertThat(message).as(errorCode.name()).doesNotContain(word));
        }
    }
}
