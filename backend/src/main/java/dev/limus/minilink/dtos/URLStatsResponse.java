package dev.limus.minilink.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class URLStatsResponse {
    private String shortCode;
    private String originalURL;
    private int clickCount;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean isActive;
    private String createdBy;
}

