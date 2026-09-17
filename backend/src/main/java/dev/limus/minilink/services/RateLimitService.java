package dev.limus.minilink.services;

import dev.limus.minilink.models.RateLimitData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${minilink.rate-limit.requests-per-minute}")
    private int allowedRequestsPerMinute;

    @Value("${minilink.rate-limit.requests-per-hour}")
    private int allowedRequestsPerHour;

    private final ConcurrentHashMap<String, RateLimitData> rateLimitData = new ConcurrentHashMap<>();

    private static final String REDIS_KEY_PREFIX = "ratelimit:";

    public boolean isAllowed(String clientIP) {
        // building the redis key
        String redisKey = REDIS_KEY_PREFIX + clientIP;

        LocalDateTime now = LocalDateTime.now();

        // checking the cache if a redisKey with the clientIP already exists
        RateLimitData data = getRateLimitDataFromRedis(redisKey);


        if (data == null) {
            // creating a new rateLimitData record for the clientIP
            // with fresh data (clientIP is new)
            data = rateLimitData.computeIfAbsent(
                    clientIP, k -> RateLimitData.builder()
                            .minuteCount(0)
                            .hourCount(0)
                            .minuteWindowStart(now)
                            .hourWindowStart(now)
                            .build());
        }

        if (isWithinMinuteWindow(data, now)) {
            // checking if the requests count that came from the clientIP
            // exceeded the allowed requests per minute
            if (data.getMinuteCount() >= allowedRequestsPerMinute) {
                log.warn("request per minute limit exceeded for {}", clientIP);
                return false;
            }
        } else {
            data.setMinuteCount(0);
            data.setMinuteWindowStart(now);
        }
        if (isWithinHourWindow(data, now)) {
            // checking if the requests count that came from the clientIP
            // exceeded the allowed requests per minute
            if (data.getHourCount() >= allowedRequestsPerHour) {
                log.warn("request per hour limit exceeded for {}", clientIP);
                return false;
            }
        } else {
            data.setHourCount(0);
            data.setHourWindowStart(now);
        }

        // increasing the count of request the client made
        data.setMinuteCount(data.getMinuteCount() + 1);
        data.setHourCount(data.getHourCount() + 1);

        // saving a rateLimitData record in redis
        saveRateLimitDataInRedis(redisKey, data);
        return true;
    }

    // checking the client is still within the hour window
    private boolean isWithinHourWindow(RateLimitData data, LocalDateTime now) {
        return data.getHourWindowStart() != null &&
                ChronoUnit.HOURS.between(data.getHourWindowStart(), now) < 1;
    }

    // checking the client is still within the minute window
    private boolean isWithinMinuteWindow(RateLimitData data, LocalDateTime now) {
        return data.getMinuteWindowStart() != null &&
                ChronoUnit.MINUTES.between(data.getMinuteWindowStart(), now) < 1;
    }

    private void saveRateLimitDataInRedis(String redisKey, RateLimitData data) {
        try {
            redisTemplate.opsForValue().set(redisKey, data, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("failed to save rate limit data from redis: {}", e.getMessage());
        }
    }

    private RateLimitData getRateLimitDataFromRedis(String redisKey) {
        try {
            return (RateLimitData) redisTemplate.opsForValue().get(redisKey);
        } catch (Exception e) {
            log.warn("failed to get rate limit data from redis: {}", e.getMessage());
            return null;
        }
    }

    public int getRemainingRequests(String clientIP) {
        String redisKey = REDIS_KEY_PREFIX + clientIP;
        RateLimitData data = getRateLimitDataFromRedis(redisKey);

        if (data == null) {
            return allowedRequestsPerMinute;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!isWithinMinuteWindow(data, now)) {
            return allowedRequestsPerMinute;
        }

        return Math.max(0, allowedRequestsPerMinute - data.getMinuteCount());
    }

    public long getTimeUntilReset(String clientIP) {
        String redisKey = REDIS_KEY_PREFIX + clientIP;
        RateLimitData data = getRateLimitDataFromRedis(redisKey);

        if (data == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        if (data.getMinuteCount() >= allowedRequestsPerMinute) {
            LocalDateTime nextMinute = data.getMinuteWindowStart()
                    .plusMinutes(1);
            return ChronoUnit.SECONDS.between(now, nextMinute);
        }

        if (data.getHourCount() >= allowedRequestsPerHour) {
            LocalDateTime nextHour = data.getHourWindowStart()
                    .plusMinutes(1);
            return ChronoUnit.SECONDS.between(now, nextHour);
        }

        return 0;
    }
}
