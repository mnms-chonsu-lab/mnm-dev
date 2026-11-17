package com.mnm.api.global.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.mnm.api.global.error.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 스웨거를 농가 앱 · 접수자 웹 · 어드민 · 인증으로 나눈다. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("촌수의사 API").version("v0"))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** 스웨거 스키마도 실제 응답처럼 snake_case 로 보이게 한다 (스웨거는 스프링 Jackson 설정을 따르지 않음) */
    @Bean
    ModelResolver snakeCaseModelResolver() {
        return new ModelResolver(Json.mapper().copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE));
    }

    /** 모든 API 에 공통 오류 응답(4XX · 5XX → ErrorResponse)을 표시한다 */
    @Bean
    GlobalOpenApiCustomizer commonErrorResponses(ModelResolver snakeCaseModelResolver) {
        ModelConverters converters = new ModelConverters(true);
        converters.addConverter(snakeCaseModelResolver);
        return openApi -> {
            converters.readAll(ErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
            Content error = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")));
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(op -> op.getResponses()
                    .addApiResponse("4XX", new ApiResponse().description("요청 오류 (code 로 구분)").content(error))
                    .addApiResponse("5XX", new ApiResponse().description("서버 오류 (SRV-5XX)").content(error))));
        };
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
