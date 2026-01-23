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

    // SSE 연결 객체 저장소 (Thread-Safe)
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 타임아웃: 1시간 (네트워크 끊김 방지를 위해 넉넉하게 설정)
    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60;

    /**
     * 1. SSE 구독 연결 (로그인 직후 호출)
     */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.put(userId, emitter);

        // 타임아웃/완료/에러 시 저장소에서 제거 (메모리 누수 방지)
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError((e) -> emitters.remove(userId));

        // 503 Service Unavailable 방지용 더미 데이터 전송
        // (클라이언트가 연결 성공 이벤트를 받기 위함)
        sendToClient(userId, "connect", "Connected! [UserId: " + userId + "]");

        return emitter;
    }

    /**
     * 2. [핵심] 알림 발송 (DB 저장 + 실시간 전송)
     * 설명: DB에 영구 저장하여 이력을 남기고, 접속 중인 유저에게는 즉시 팝업을 띄웁니다.
     */
    @Transactional
    public void send(Long userId, String title, String message, String type, String url) {
        // (1) 엔티티 생성 (createdAt을 자바에서 미리 설정하여 DTO 변환 시 NPE 방지)
        NotificationEntity notification = NotificationEntity.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type != null ? type : "INFO")
                .isRead(false)
                .relatedUrl(url)
                .createdAt(LocalDateTime.now()) // [중요] 자바 객체에도 시간 주입
                .build();

        // (2) DB 저장 (여기서 ID가 생성됨)
        notificationMapper.save(notification);

        // (3) 실시간 전송 (온라인 유저인 경우만)
        if (emitters.containsKey(userId)) {
            NotificationDTO.NotificationItem item = toItemDTO(notification);
            sendToClient(userId, "notification", item);
        }
    }

    // 편의 메서드 (단순 메시지 전송용)
    @Transactional
    public void send(Long userId, String message) {
        send(userId, "알림", message, "INFO", null);
    }

    /**
     * 3. 알림 목록 조회 (보관함)
     */
    @Transactional(readOnly = true)
    public NotificationDTO.Response getMyNotifications(Long userId) {
        // 최신순 50개 조회 (Mapper XML의 LIMIT 확인 필요)
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

    /**
     * 4. 읽음 처리
     */
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        notificationMapper.markAsRead(userId, notificationId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsRead(userId);
    }

    // --- 내부 메서드 ---

    // 실제 클라이언트로 데이터 전송 (예외 처리 포함)
    private void sendToClient(Long userId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(userId))
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                // 전송 실패 시 연결이 끊긴 것으로 간주하고 제거
                emitters.remove(userId);
                log.warn("SSE 전송 실패 (User: {}): {}", userId, e.getMessage());
            }
        }
    }

    // Entity -> DTO 변환
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

    // "방금 전", "10분 전" 포맷터
    private String calculateTimeAgo(LocalDateTime dateTime) {
        if (dateTime == null) return "알 수 없음"; // 방어 코드

        Duration duration = Duration.between(dateTime, LocalDateTime.now());
        long seconds = duration.getSeconds();

        if (seconds < 60) return "방금 전";
        if (seconds < 3600) return (seconds / 60) + "분 전";
        if (seconds < 86400) return (seconds / 3600) + "시간 전";
        return (seconds / 86400) + "일 전";
    }
}