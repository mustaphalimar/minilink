package dev.limus.minilink.config;

import dev.limus.minilink.services.URLShortenerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupScheduler {
    private final URLShortenerService urlShortenerService;

    @Scheduled(fixedRateString = "#{${minilink.cleanup.interval-minutes} * 60 * 1000}")
    public void cleanupExpiredURLs() {
        try {
            log.debug("running scheduled cleanup of expired URLs");
            urlShortenerService.cleanupExpiredURLs();
        } catch (Exception e) {
            log.error("error during scheduled cleanup");
        }
    }
}
