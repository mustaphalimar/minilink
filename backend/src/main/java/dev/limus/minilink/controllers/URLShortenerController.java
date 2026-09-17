package dev.limus.minilink.controllers;

import dev.limus.minilink.dtos.ShortenURLRequest;
import dev.limus.minilink.dtos.ShortenURLResponse;
import dev.limus.minilink.services.RateLimitService;
import dev.limus.minilink.services.URLShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class URLShortenerController {
    private final URLShortenerService urlShortenerService;
    private final RateLimitService rateLimitService;


    @PostMapping("/shorten")
    public ResponseEntity<?> shortenUrl(
            @Valid @RequestBody ShortenURLRequest requestBody,
            HttpServletRequest httpRequest
    ) {
        String clientIP = getClientIP(httpRequest);
        if (!rateLimitService.isAllowed(clientIP)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error", "Rate limit exceeded",
                    "remainingRequests", rateLimitService.getRemainingRequests(clientIP),
                    "timeUntilReset", rateLimitService.getTimeUntilReset(clientIP)
            ));
        }

        try {
            ShortenURLResponse response = urlShortenerService.shortenURL(requestBody, clientIP);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error"
            ));
        }
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirectToURL(
            @PathVariable String shortCode,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String clientIP = getClientIP(request);
        String userAgent = request.getHeader("User-Agent");
        String referrer = request.getHeader("Referrer");

        Optional<String> originalURL = urlShortenerService.getOriginalURL(shortCode);

        if (originalURL.isPresent()) {
            urlShortenerService.recordClick(shortCode, clientIP, userAgent, referrer);
            response.setHeader("Location", originalURL.get());
            return ResponseEntity.status(HttpStatus.FOUND).build();
        }
        return ResponseEntity.notFound().build();
    }

    // getClientIP gets the client IP
    private String getClientIP(HttpServletRequest httpRequest) {
        String xForwardedFor = httpRequest.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIP = httpRequest.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        return httpRequest.getRemoteAddr();
    }
}
