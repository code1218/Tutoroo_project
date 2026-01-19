package com.tutoroo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {
    private Long id;
    private Long userId;
    private String title;       // 알림 제목 (예: 학습, 시스템, 결제)
    private String message;     // 알림 내용
    private String type;        // INFO, WARNING, SUCCESS
    private boolean isRead;     // 읽음 여부
    private String relatedUrl;  // 클릭 시 이동할 링크 (선택사항)
    private LocalDateTime createdAt;
}