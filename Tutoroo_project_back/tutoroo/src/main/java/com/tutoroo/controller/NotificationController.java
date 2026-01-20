package com.tutoroo.controller;

import com.tutoroo.dto.NotificationDTO;
import com.tutoroo.security.CustomUserDetails;
import com.tutoroo.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "알림 구독 및 내역 조회")
public class NotificationController {

    private final NotificationService notificationService;

    // 1. SSE 구독 (기존 유지)
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "실시간 알림 구독 (SSE)", description = "로그인 직후 이 API를 연결해야 실시간 알림을 받을 수 있습니다.")
    public SseEmitter subscribe(@AuthenticationPrincipal CustomUserDetails user) {
        return notificationService.subscribe(user.getId());
    }

    // 2. [추가] 내 알림 목록 조회
    @GetMapping
    @Operation(summary = "알림 보관함 조회", description = "지난 알림 내역과 안 읽은 알림 개수를 반환합니다.")
    public ResponseEntity<NotificationDTO.Response> getNotifications(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(notificationService.getMyNotifications(user.getId()));
    }

    // 3. [추가] 알림 읽음 처리
    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 상태로 변경합니다.")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long notificationId) {
        notificationService.markAsRead(user.getId(), notificationId);
        return ResponseEntity.ok().build();
    }

    // 4. [추가] 전체 읽음 처리
    @PatchMapping("/read-all")
    @Operation(summary = "모두 읽음 처리", description = "사용자의 모든 알림을 읽음 처리합니다.")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal CustomUserDetails user) {
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }
}