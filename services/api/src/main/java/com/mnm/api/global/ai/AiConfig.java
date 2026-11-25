package com.mnm.api.global.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** AI_BASE_URL 이 있으면 mnm-ai 내부 API 서버, 없으면 가짜 AI */
@Slf4j
@Configuration
class AiConfig {

    @Bean
    AiClient aiClient(
            @Value("${mnm.ai.base-url:}") String baseUrl,
            @Value("${mnm.ai.internal-token:}") String internalToken,
            ObjectMapper objectMapper) {
        if (baseUrl.isBlank()) {
            log.warn("AI_BASE_URL 없음: 가짜 AI 를 씁니다 (음성은 늘 같은 시연 문장으로 인식됨)");
            return new MockAiClient();
        }
        if (internalToken.isBlank()) {
            throw new IllegalStateException("AI_BASE_URL 을 넣으면 AI_INTERNAL_TOKEN 도 함께 넣어 주세요");
        }
        return new HttpAiClient(baseUrl, internalToken, objectMapper);
    }
}
