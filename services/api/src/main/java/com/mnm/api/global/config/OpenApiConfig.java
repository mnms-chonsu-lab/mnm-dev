package com.mnm.api.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 스웨거를 농가 앱 · 접수자 웹 · 어드민 · 인증으로 나눈다. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("촌수의사 API").version("v0"));
    }

    @Bean
    GroupedOpenApi farmerApi() {
        return group("farmer", "농가 앱", ApiPaths.FARMER);
    }

    @Bean
    GroupedOpenApi receiverApi() {
        return group("receiver", "접수자 웹", ApiPaths.RECEIVER);
    }

    @Bean
    GroupedOpenApi adminApi() {
        return group("admin", "어드민", ApiPaths.ADMIN);
    }

    @Bean
    GroupedOpenApi authApi() {
        return group("auth", "인증", ApiPaths.AUTH);
    }

    private static GroupedOpenApi group(String name, String displayName, String prefix) {
        return GroupedOpenApi.builder()
                .group(name)
                .displayName(displayName)
                .pathsToMatch(prefix + "/**")
                .build();
    }
}
