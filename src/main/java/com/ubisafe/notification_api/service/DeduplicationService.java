package com.ubisafe.notification_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${alert.deduplication.window-seconds:5}")
    private long windowSeconds;

    private static final String PREFIX = "alert:dedup:";

    public boolean isDuplicate(String alertId) {
        String key = PREFIX + alertId;
        try {
            Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(key, "1", windowSeconds, TimeUnit.SECONDS);
            boolean duplicate = firstTime == null || !firstTime;
            if (duplicate) {
                log.debug("Duplicate detected for id={}", alertId);
            }
            return duplicate;
        } catch (Exception e) {
            log.warn("Redis error on dedup check id={}: {}", alertId, e.getMessage());
            return false;
        }
    }
}

