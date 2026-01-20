package com.tutoroo.dto;

import lombok.Builder;
import java.util.List;

public class NotificationDTO {

    // 알림 목록 조회 응답
    @Builder
    public record Response(
            List<NotificationItem> notifications,
            long unreadCount
    ) {}

    // 개별 알림 항목
    @Builder
    public record NotificationItem(
            Long id,
            String title,
            String message,
            String type,
            boolean isRead,
            String timeAgo, // "방금 전", "1시간 전" 등 가공된 시간
            String relatedUrl
    ) {}
}