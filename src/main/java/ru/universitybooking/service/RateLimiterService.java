package ru.universitybooking.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {

    private final StringRedisTemplate stringRedisTemplate;

    public RateLimiterService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean isAllowed(String ip, long maxRequests, Duration timeout) {

        String key = "rate_limiter:" + ip;
        Long currentCount = stringRedisTemplate.opsForValue().increment(key);

        if (currentCount == null) {
            return false;
        }

        if (currentCount == 1) {
            stringRedisTemplate.expire(key, timeout);
        }

        return currentCount <= maxRequests;
    }
}
