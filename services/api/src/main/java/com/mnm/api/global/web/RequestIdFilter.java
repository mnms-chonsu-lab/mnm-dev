package com.mnm.api.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 request_id 를 정해 로그(MDC) · 응답 헤더 · 응답 본문에 같은 값으로 남긴다.
 * 요청에 X-Request-Id 가 있으면(nginx 등) 그 값을 쓰고, 없으면 새로 만든다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String KEY = "request_id";

    // 밖에서 온 값은 로그에 그대로 찍히므로 형식을 제한한다 (줄바꿈 등 로그 위조 방지)
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9-]{8,64}");

    /** 현재 요청의 request_id. 요청 밖(배치 등)에서는 null */
    public static String current() {
        return MDC.get(KEY);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request);
        request.setAttribute(KEY, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(KEY);
        }
    }

    /** 필터에서 난 오류는 /error 로 다시 들어오므로, 그때도 같은 값을 쓰도록 다시 실행한다 */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private static String resolve(HttpServletRequest request) {
        if (request.getAttribute(KEY) instanceof String existing) {
            return existing;
        }
        String header = request.getHeader(HEADER);
        return header != null && ALLOWED.matcher(header).matches() ? header : UUID.randomUUID().toString();
    }
}
