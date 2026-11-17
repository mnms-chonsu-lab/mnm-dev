package com.mnm.api.global.config;

import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.util.Assert;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 로그인 토큰(JWT, Bearer)으로 역할별 접근을 나눈다.
 * 토큰 claim: sub = users_id, role = farmer|receiver|admin, must_change_pw.
 * 토큰 없음·만료·위조는 AUTH-401, 역할이 맞지 않으면 AUTH-403 (ErrorResponse 형식).
 */
@Configuration
public class SecurityConfig {

    public static final String FARMER_LOGIN = ApiPaths.AUTH + "/farmer/login";
    public static final String STAFF_LOGIN = ApiPaths.AUTH + "/login";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_MUST_CHANGE_PW = "must_change_pw";

    private final SecretKey secretKey;

    SecurityConfig(@Value("${mnm.auth.jwt-secret}") String jwtSecret) {
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        Assert.isTrue(bytes.length >= 32, "JWT_SECRET 은 32바이트 이상이어야 한다 (HS256)");
        this.secretKey = new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
        // 필터 단계 오류도 GlobalExceptionHandler 가 같은 ErrorResponse 로 만든다
        AuthenticationEntryPoint unauthorized = (req, res, e) ->
                resolver.resolveException(req, res, null, new BusinessException(ErrorCode.AUTH_EXPIRED, "토큰 없음·만료·위조"));
        AccessDeniedHandler forbidden = (req, res, e) ->
                resolver.resolveException(req, res, null, new BusinessException(ErrorCode.AUTH_FORBIDDEN, "역할 권한 없음"));
        return http
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, FARMER_LOGIN, STAFF_LOGIN).permitAll()
                        .requestMatchers(ApiPaths.FARMER + "/**").hasRole("FARMER")
                        .requestMatchers(ApiPaths.RECEIVER + "/**").hasRole("RECEIVER")
                        .requestMatchers(ApiPaths.ADMIN + "/**").hasRole("ADMIN")
                        .requestMatchers(ApiPaths.AUTH + "/**").authenticated()
                        // 스웨거 · 헬스 체크 · /error. 그 밖의 경로는 nginx 가 막는다
                        .anyRequest().permitAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .bearerTokenResolver(ignoreTokenOnLogin())
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .build();
    }

    /** 비밀번호를 아직 안 바꾼 계정(임시 비밀번호)은 역할 권한 없이 /api/auth/** 만 쓸 수 있다 (R-S06) */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::authorities);
        return converter;
    }

    private static List<GrantedAuthority> authorities(Jwt jwt) {
        if (Boolean.TRUE.equals(jwt.getClaimAsBoolean(CLAIM_MUST_CHANGE_PW))) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(CLAIM_ROLE).toUpperCase()));
    }

    /** 만료된 토큰을 붙인 채 로그인해도 401 이 아니라 로그인이 되도록, 로그인 요청에서는 토큰을 읽지 않는다 */
    private static BearerTokenResolver ignoreTokenOnLogin() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        Set<String> loginPaths = Set.of(FARMER_LOGIN, STAFF_LOGIN);
        return request -> loginPaths.contains(request.getRequestURI()) ? null : resolver.resolve(request);
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
