package ru.universitybooking.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.universitybooking.service.RateLimiterService;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter implements Filter {

    private final RateLimiterService rateLimiterService;

    @Value("${rate-limit.max-requests:30}")
    private long maxRequests;

    @Value("${rate-limit.window-seconds:60}")
    private long windowSeconds;

    public RateLimitFilter(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        String ip = resolveClientIp(request);

        boolean allowed = rateLimiterService.isAllowed(ip, maxRequests, Duration.ofSeconds(windowSeconds));

        if (!allowed) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\": \"Превышен лимит запросов. Пожалуйста, подождите минуту.\"}");
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
