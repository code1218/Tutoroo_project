package com.tutoroo.controller;

import com.tutoroo.dto.PaymentDTO;
import com.tutoroo.security.CustomUserDetails;
import com.tutoroo.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "결제 검증 및 내역 조회 API")
public class PaymentController {

    private final PaymentService paymentService;

    // 1. 결제 검증 및 멤버십 업그레이드
    @PostMapping("/verify")
    @Operation(summary = "결제 검증", description = "포트원 결제 후 서버 검증 및 등급 업그레이드를 수행합니다.")
    public ResponseEntity<PaymentDTO.VerificationResponse> verifyPayment(
            @RequestBody PaymentDTO.VerificationRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        PaymentDTO.VerificationResponse response = paymentService.verifyAndUpgrade(request, user.getUsername());
        return ResponseEntity.ok(response);
    }

    // 2. 내 결제 내역 조회
    @GetMapping("/history")
    @Operation(summary = "결제 내역 조회", description = "나의 과거 결제 이력을 최신순으로 조회합니다.")
    public ResponseEntity<PaymentDTO.HistoryResponse> getPaymentHistory(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(user.getId()));
    }

    // 3. [New] 결제 웹훅 (포트원 -> 서버)
    // 주의: SecurityConfig에서 "/api/payment/webhook"은 permitAll() 되어야 함
    @PostMapping("/webhook")
    @Operation(summary = "포트원 웹훅", description = "PG사로부터 결제 결과를 비동기로 수신합니다. (인증 없음)")
    public ResponseEntity<String> paymentWebhook(@RequestBody PaymentDTO.VerificationRequest request) {
        log.info("WEBHOOK RECEIVED: {}", request);
        try {
            paymentService.processWebhook(request);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Webhook Error", e);
            // 웹훅은 200이 아니면 재발송하므로, 비즈니스 에러라도 일단 200 반환 후 내부 로깅 추천
            return ResponseEntity.ok("Received but processed with error");
        }
    }
}