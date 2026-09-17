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
public class ShortenURLResponse {
    private String shortURL;
    private String shortCode;
    private String originalURL;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
