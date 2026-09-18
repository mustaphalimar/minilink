package dev.limus.minilink.services;

import dev.limus.minilink.dtos.ShortenURLRequest;
import dev.limus.minilink.dtos.ShortenURLResponse;
import dev.limus.minilink.dtos.URLAnalyticsResponse;
import dev.limus.minilink.dtos.URLStatsResponse;
import dev.limus.minilink.models.ClickEvent;
import dev.limus.minilink.models.URLData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class URLShortenerService {
    private final RedisTemplate<String, Object> redisTemplate;

    private final Map<String, URLData> urlMappings = new ConcurrentHashMap<>();

    private final Map<String, List<ClickEvent>> clickAnalytics = new ConcurrentHashMap<>();

    @Value("${minilink.base-url}")
    private String baseURL;

    @Value("${minilink.short-code.length}")
    private int shortCodeLength;

    @Value("${minilink.short-code.max-attempts}")
    private int maxGenerationAttempts;

    @Value("${minilink.cache.ttl-minutes}")
    private int cacheTTLMinutes;

    private static final String BASE_62_CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public ShortenURLResponse shortenURL(ShortenURLRequest request, String clientIP) {
        String shortCode = request.getCustomAlias();
        if (shortCode == null || shortCode.trim().isEmpty()) {
            shortCode = generateUniqueShortCode();
        } else {
            shortCode = shortCode.trim();
            if (shortCodeExists(shortCode)) {
                throw new IllegalArgumentException("Custom alias already exists: " + shortCode);
            }
        }
        URLData urlData = URLData.builder()
                .originalURL(request.getOriginalURL())
                .shortCode(shortCode)
                .expiresAt(request.getExpiresAt())
                .createdAt(LocalDateTime.now())
                .createdBy(clientIP)
                .clickCount(0)
                .isActive(true)
                .clickEvents(new ArrayList<>())
                .build();

        urlMappings.put(shortCode, urlData);
        clickAnalytics.put(shortCode, new ArrayList<>());

        // caching the shortcode for quick access next time
        cacheURL(shortCode, request.getOriginalURL());

        log.info("Created short URL: {} -> {}", shortCode, request.getOriginalURL());
        return ShortenURLResponse.builder()
                .shortURL(buildShortURL(shortCode))
                .shortCode(shortCode)
                .originalURL(request.getOriginalURL())
                .createdAt(urlData.getCreatedAt())
                .expiresAt(urlData.getExpiresAt())
                .build();
    }

    private String buildShortURL(String shortCode) {
        String normalizedBaseURL = baseURL.endsWith("/") ? baseURL.substring(0, baseURL.length() - 1) : baseURL;
        return normalizedBaseURL + "/api/" + shortCode;
    }

    private void cacheURL(String shortCode, String originURL) {
        try {
            redisTemplate.opsForValue().set("url:" + shortCode, originURL, cacheTTLMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("failed to cache url for {}:{}", shortCode, e.getMessage());
        }
    }

    private String generateUniqueShortCode() {
        for (int attempt = 0; attempt < maxGenerationAttempts; attempt++) {
            String code = generateRandomBase62();
            if (!shortCodeExists(code)) {
                return code;
            }
        }
        throw new RuntimeException("Failed to generate unique short code after " + maxGenerationAttempts + " attempts");
    }

    private boolean shortCodeExists(String code) {
        return urlMappings.containsKey(code);
    }

    private String generateRandomBase62() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shortCodeLength; i++) {
            int index = ThreadLocalRandom.current().nextInt(BASE_62_CHARS.length());
            sb.append(BASE_62_CHARS.charAt(index));
        }
        return sb.toString();
    }

    public Optional<String> getOriginalURL(String shortCode) {
        String cachedURL = getCachedURL(shortCode);
        if (cachedURL != null) {
            return Optional.of(cachedURL);
        }

        URLData urlData = urlMappings.get(shortCode);
        if (urlData != null && urlData.isActive()) {
            if (isExpired(urlData)) {
                urlData.setActive(false);
                return Optional.empty();
            }

            // caching the short url
            cacheURL(shortCode, urlData.getOriginalURL());
            return Optional.of(urlData.getOriginalURL());
        }
        return Optional.empty();
    }

    private boolean isExpired(URLData urlData) {
        return urlData.getExpiresAt() != null && urlData.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private String getCachedURL(String shortCode) {
        try {
            return (String) redisTemplate.opsForValue().get("url" + shortCode);
        } catch (Exception e) {
            log.warn("failed to read cached URL for {}:{}", shortCode, e.getMessage());
            return null;
        }
    }

    public void recordClick(String shortCode, String clientIP, String userAgent, String referrer) {
        URLData urlData = urlMappings.get(shortCode);
        if (urlData != null && urlData.isActive()) {
            urlData.setClickCount(urlData.getClickCount() + 1);
            ClickEvent clickEvent = ClickEvent.builder()
                    .timestamp(LocalDateTime.now())
                    .ipAddress(clientIP)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .build();
            clickAnalytics.get(shortCode).add(clickEvent);
            log.debug("recorded click for short code: {}", shortCode);
        }
    }

    public Optional<URLStatsResponse> getURLStats(String shortCode) {
        URLData urlData = urlMappings.get(shortCode);
        if (urlData == null) {
            return Optional.empty();
        }
        return Optional.of(
                URLStatsResponse.builder()
                        .shortCode(urlData.getShortCode())
                        .originalURL(urlData.getOriginalURL())
                        .clickCount(urlData.getClickCount())
                        .createdAt(urlData.getCreatedAt())
                        .expiresAt(urlData.getExpiresAt())
                        .isActive(urlData.isActive())
                        .createdBy(urlData.getCreatedBy())
                        .build()
        );
    }

    public Optional<URLAnalyticsResponse> getURLAnalytics(String shortCode) {
        URLData urlData = urlMappings.get(shortCode);
        if (urlData == null) {
            return Optional.empty();
        }

        List<ClickEvent> clicks = clickAnalytics.getOrDefault(shortCode, new ArrayList<>());
        Map<String, Integer> clicksByReferrer = clicks.stream()
                .filter(c -> c.getReferrer() != null)
                .collect(Collectors.groupingBy(ClickEvent::getReferrer, Collectors.summingInt(e -> 1)));

        Map<String, Integer> clicksByHour = clicks.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getTimestamp().getHour() + ":00", Collectors.summingInt(e -> 1))
                );

        Map<String, Integer> clicksByDay = clicks.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getTimestamp().toLocalDate().toString(), Collectors.summingInt(e -> 1))
                );
        List<ClickEvent> recentClicks = clicks.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(10)
                .toList();
        return Optional.of(
                URLAnalyticsResponse.builder()
                        .shortCode(shortCode)
                        .originalURL(urlData.getOriginalURL())
                        .totalClicks(urlData.getClickCount())
                        .createdAt(urlData.getCreatedAt())
                        .expiresAt(urlData.getExpiresAt())
                        .recentClicks(recentClicks)
                        .clicksByReferrer(clicksByReferrer)
                        .clicksByHour(clicksByHour)
                        .clicksByDay(clicksByDay)
                        .build()
        );
    }

    public boolean deleteURL(String shortCode) {
        URLData urlData = urlMappings.get(shortCode);
        if (urlData != null) {
            urlData.setActive(false);
            deleteCacheURL(shortCode);
            log.info("Deleted URL: {}", shortCode);
            return true;
        }
    }

    private void deleteCacheURL(String shortCode) {
        try {
            redisTemplate.delete("url:" + shortCode);
        } catch (Exception e) {
            log.warn("failed to delete cached URL for {}:{}", shortCode, e.getMessage());
        }
    }
}
