package com.mnm.api;

import com.mnm.api.global.config.SecurityConfig;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** 테스트에서 역할별 로그인 토큰을 만든다 (서버가 발급하는 것과 같은 형식) */
public final class TestTokens {

    private TestTokens() {
    }

    public static String bearer(JwtEncoder encoder, long userId, String role, boolean mustChangePw, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now.minus(Duration.ofHours(1))) // 만료된 토큰(ttl 음수)도 만들 수 있게
                .expiresAt(now.plus(ttl))
                .claim(SecurityConfig.CLAIM_ROLE, role)
                .claim(SecurityConfig.CLAIM_MUST_CHANGE_PW, mustChangePw)
                .build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public static String bearer(JwtEncoder encoder, String role) {
        return bearer(encoder, 1L, role, false, Duration.ofHours(1));
    }
}
