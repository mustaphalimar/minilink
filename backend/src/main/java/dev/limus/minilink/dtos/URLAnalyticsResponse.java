package dev.limus.minilink.dtos;

import dev.limus.minilink.models.ClickEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class URLAnalyticsResponse {
    private String shortCode;
    private String originalURL;
    private int totalClicks;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private List<ClickEvent> recentClicks;
    private Map<String, Integer> clicksByReferrer;
    private Map<String, Integer> clicksByHour;
    private Map<String, Integer> clicksByDay;
}
