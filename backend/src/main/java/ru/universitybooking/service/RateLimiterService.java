package ru.universitybooking.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RateLimiterService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> rateLimiterScript;

    public RateLimiterService(StringRedisTemplate stringRedisTemplate, RedisScript<Long> rateLimiterScript) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rateLimiterScript = rateLimiterScript;
    }

    public boolean isAllowed(String ip, long maxRequests, Duration timeout) {
        String key = "rate_limiter:" + ip;
        Long currentCount = stringRedisTemplate.execute(rateLimiterScript, List.of(key), String.valueOf(timeout.getSeconds()));
        return currentCount != null && currentCount <= maxRequests;
    }
}
