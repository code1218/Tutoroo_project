package com.tutoroo.service;

import com.tutoroo.dto.NotificationDTO;
import com.tutoroo.entity.NotificationEntity;
import com.tutoroo.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    // SSE 연결 객체 저장소
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60; // 1시간

    // 1. SSE 구독 연결
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError((e) -> emitters.remove(userId));

        // 연결 확인용 더미 데이터 전송
        sendToClient(userId, "connect", "Connected! [UserId: " + userId + "]");
        return emitter;
    }

    // 2. [핵심 수정] 알림 발송 (DB 저장 -> 실시간 전송)
    @Transactional
    public void send(Long userId, String title, String message, String type, String url) {
        // (1) DB에 영구 저장 (오프라인 유저 대비)
        NotificationEntity notification = NotificationEntity.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type != null ? type : "INFO")
                .isRead(false)
                .relatedUrl(url)
                .createdAt(LocalDateTime.now())
                .build();

        notificationMapper.save(notification);

        // (2) 온라인 유저에게 실시간 전송
        if (emitters.containsKey(userId)) {
            // DTO로 변환하여 전송
            NotificationDTO.NotificationItem item = toItemDTO(notification);
            sendToClient(userId, "notification", item);
        }
    }

    // 편의 메서드 (기존 코드 호환용)
    public void send(Long userId, String message) {
        send(userId, "알림", message, "INFO", null);
    }

    // 3. [추가] 알림 목록 조회
    @Transactional(readOnly = true)
    public NotificationDTO.Response getMyNotifications(Long userId) {
        List<NotificationEntity> entities = notificationMapper.findAllByUserId(userId);
        long unreadCount = notificationMapper.countUnreadByUserId(userId);

        List<NotificationDTO.NotificationItem> items = entities.stream()
                .map(this::toItemDTO)
                .collect(Collectors.toList());

        return NotificationDTO.Response.builder()
                .notifications(items)
                .unreadCount(unreadCount)
                .build();
    }

    // 4. [추가] 읽음 처리
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        notificationMapper.markAsRead(userId, notificationId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsRead(userId);
    }

    // --- 내부 메서드 ---
    private void sendToClient(Long userId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().id(String.valueOf(userId)).name(eventName).data(data));
            } catch (IOException e) {
                emitters.remove(userId);
            }
        }
    }

    private NotificationDTO.NotificationItem toItemDTO(NotificationEntity entity) {
        return NotificationDTO.NotificationItem.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .type(entity.getType())
                .isRead(entity.isRead())
                .timeAgo(calculateTimeAgo(entity.getCreatedAt()))
                .relatedUrl(entity.getRelatedUrl())
                .build();
    }

    private String calculateTimeAgo(LocalDateTime dateTime) {
        Duration duration = Duration.between(dateTime, LocalDateTime.now());
        long seconds = duration.getSeconds();
        if (seconds < 60) return "방금 전";
        if (seconds < 3600) return (seconds / 60) + "분 전";
        if (seconds < 86400) return (seconds / 3600) + "시간 전";
        return (seconds / 86400) + "일 전";
    }
}