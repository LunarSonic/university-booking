package ru.universitybooking.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import ru.universitybooking.service.RateLimiterService;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter implements Filter {

    private final RateLimiterService rateLimiterService;

    public RateLimitFilter(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    private static final long MAX_REQUEST = 10;
    private static final Duration MAX_REQUESTS = Duration.ofMinutes(1);

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String ip = request.getRemoteAddr();

        boolean allowed = rateLimiterService.isAllowed(ip, MAX_REQUEST, MAX_REQUESTS);

        if (!allowed) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\": \"Превышен лимит запросов\"}");
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }
}
