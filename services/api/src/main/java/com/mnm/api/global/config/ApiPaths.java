package com.mnm.api.global.config;

/**
 * 플랫폼별 API 경로 접두사.
 * nginx(infra/docker/nginx/locations.conf)와 스웨거 그룹이 같은 값을 기준으로 요청을 나눈다.
 */
public final class ApiPaths {

    public static final String FARMER = "/api/farmer";
    public static final String RECEIVER = "/api/receiver";
    public static final String ADMIN = "/api/admin";
    public static final String AUTH = "/api/auth";

    private ApiPaths() {
    }
}
