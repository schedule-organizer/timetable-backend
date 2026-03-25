package com.schediflow.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sliding-window rate limiter for /api/v1/auth/** endpoints.
 * Tracks request timestamps per IP; rejects with 429 when the limit is exceeded.
 * Configurable via app.ratelimit.* properties (override in tests for fast failure).
 */
@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public AuthRateLimitInterceptor(
            @Value("${app.ratelimit.max-requests:10}") int maxRequests,
            @Value("${app.ratelimit.window-ms:60000}") long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String ip = resolveClientIp(request);
        long now = System.currentTimeMillis();
        AtomicBoolean exceeded = new AtomicBoolean(false);

        requestLog.compute(ip, (k, timestamps) -> {
            if (timestamps == null) timestamps = new ArrayDeque<>();
            long cutoff = now - windowMs;
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.pollFirst();
            }
            timestamps.addLast(now);
            if (timestamps.size() > maxRequests) {
                exceeded.set(true);
            }
            return timestamps;
        });

        if (exceeded.get()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"code\":\"RATE_LIMIT_EXCEEDED\"," +
                    "\"message\":\"Too many requests — try again later\"," +
                    "\"timestamp\":\"" + Instant.now() + "\"}");
            return false;
        }
        return true;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
