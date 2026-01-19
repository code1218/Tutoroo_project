package com.tutoroo.service;

import com.tutoroo.dto.PaymentDTO;
import com.tutoroo.entity.MembershipTier;
import com.tutoroo.entity.PaymentEntity;
import com.tutoroo.entity.UserEntity;
import com.tutoroo.mapper.PaymentMapper;
import com.tutoroo.mapper.UserMapper;
import com.tutoroo.util.PortOneClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final UserMapper userMapper;
    private final PaymentMapper paymentMapper;
    private final PortOneClient portOneClient;

    /**
     * [기능: 결제 검증 및 멤버십 업그레이드]
     */
    @Transactional
    public PaymentDTO.VerificationResponse verifyAndUpgrade(PaymentDTO.VerificationRequest request, String username) {
        try {
            // 1. 사용자 확인
            UserEntity user = userMapper.findByUsername(username);
            if (user == null) throw new IllegalArgumentException("존재하지 않는 사용자입니다.");

            // 2. 가격별 등급 매핑
            MembershipTier newTier;
            if (request.amount() == 9900) {
                newTier = MembershipTier.STANDARD;
            } else if (request.amount() == 29900) {
                newTier = MembershipTier.PREMIUM;
            } else {
                throw new IllegalArgumentException("유효하지 않은 결제 금액입니다.");
            }

            if (user.getEffectiveTier() == newTier) {
                log.info("기존과 동일한 등급 결제: {}", username);
            }

            // 3. DB 반영 (멤버십 업데이트)
            user.setMembershipTier(newTier);
            userMapper.update(user);

            // 4. 결제 내역 저장
            PaymentEntity payment = PaymentEntity.builder()
                    .userId(user.getId())
                    .planId(request.planId())
                    .impUid(request.impUid())
                    .merchantUid(request.merchantUid())
                    .amount(request.amount())
                    .payMethod(request.payMethod())
                    .pgProvider(request.pgProvider())
                    .itemName(newTier.name() + " SUBSCRIPTION")
                    .status("PAID")
                    .paidAt(LocalDateTime.now())
                    .build();
            paymentMapper.save(payment);

            log.info("✅ 결제 성공 및 등급 변경: User={} Tier={}", username, newTier);

            return PaymentDTO.VerificationResponse.builder()
                    .success(true)
                    .message(String.format("멤버십이 %s 등급으로 업그레이드 되었습니다.", newTier.name()))
                    .paidAt(LocalDateTime.now().toString())
                    .nextPaymentDate(LocalDateTime.now().plusMonths(1).toString())
                    .build();

        } catch (Exception e) {
            log.error("❌ 결제 검증 실패. 자동 환불 시도. impUid={}", request.impUid(), e);
            try {
                portOneClient.cancelPayment(request.impUid(), "서버 오류로 인한 자동 취소");
            } catch (Exception cancelEx) {
                log.error("🔥 자동 환불 실패: {}", request.impUid(), cancelEx);
            }
            throw new RuntimeException("결제 처리에 실패하여 자동 취소되었습니다.");
        }
    }

    /**
     * [New] 기능: 내 결제 내역 조회
     */
    @Transactional(readOnly = true)
    public PaymentDTO.HistoryResponse getPaymentHistory(Long userId) {
        // 1. DB 조회
        List<PaymentEntity> payments = paymentMapper.findAllByUserId(userId);

        // 2. DTO 변환
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<PaymentDTO.PaymentInfo> paymentInfos = payments.stream()
                .map(p -> PaymentDTO.PaymentInfo.builder()
                        .merchantUid(p.getMerchantUid())
                        .itemName(p.getItemName())
                        .amount(p.getAmount())
                        .payMethod(p.getPayMethod())
                        .status(p.getStatus())
                        .paidAt(p.getPaidAt().format(formatter))
                        .build())
                .collect(Collectors.toList());

        // 3. 총 결제 금액 계산
        long totalSpent = payments.stream()
                .filter(p -> "PAID".equals(p.getStatus()))
                .mapToLong(PaymentEntity::getAmount)
                .sum();

        return PaymentDTO.HistoryResponse.builder()
                .payments(paymentInfos)
                .totalSpent(totalSpent)
                .build();
    }

    /**
     * [New] 기능: 웹훅 처리 (비동기 결제 반영)
     */
    @Transactional
    public void processWebhook(PaymentDTO.WebhookRequest request) {
        String impUid = request.imp_uid();
        String merchantUid = request.merchant_uid();

        log.info("🔔 웹훅 수신: imp_uid={}, merchant_uid={}, status={}", impUid, merchantUid, request.status());

        if (!"paid".equals(request.status())) {
            log.info("결제 완료 상태가 아니므로 무시합니다.");
            return;
        }

        // 1. 이미 처리된 결제인지 확인 (멱등성 보장)
        PaymentEntity existing = paymentMapper.findByImpUid(impUid);
        if (existing != null && "PAID".equals(existing.getStatus())) {
            log.info("이미 처리된 결제입니다. (Duplicate Webhook)");
            return;
        }

        // 2. 포트원 서버에서 실제 결제 정보 조회 (검증)
        Map<String, Object> paymentData = portOneClient.getPayment(impUid);
        if (paymentData == null) {
            throw new RuntimeException("유효하지 않은 결제 정보입니다.");
        }

        int amount = (int) paymentData.get("amount");
        String status = (String) paymentData.get("status");

        if (!"paid".equals(status)) {
            log.error("실제 결제 상태가 paid가 아닙니다: {}", status);
            return;
        }

        // 3. 유저 식별 (merchant_uid 포맷: order_{userId}_{timestamp} 가정)
        Long userId = extractUserIdFromMerchantUid(merchantUid);
        if (userId == null) {
            log.error("유저 식별 불가. merchant_uid 형식을 확인하세요: {}", merchantUid);
            return; // 수동 처리 필요
        }

        UserEntity user = userMapper.findById(userId);
        if (user == null) {
            log.error("존재하지 않는 유저 ID입니다: {}", userId);
            return;
        }

        // 4. 멤버십 업데이트 및 결제 저장 (verifyAndUpgrade 로직 재사용)
        // DTO를 수동으로 구성하여 처리
        PaymentDTO.VerificationRequest verifyRequest = PaymentDTO.VerificationRequest.builder()
                .impUid(impUid)
                .merchantUid(merchantUid)
                .amount(amount)
                .payMethod((String) paymentData.get("pay_method"))
                .pgProvider((String) paymentData.get("pg_provider"))
                .planId(null) // 구독형으로 가정
                .build();

        // 내부 로직 호출 (트랜잭션 전파)
        verifyAndUpgrade(verifyRequest, user.getUsername());
        log.info("🔔 웹훅을 통한 결제 처리 완료: User={}", user.getUsername());
    }

    // 헬퍼: 주문번호에서 유저 ID 추출
    private Long extractUserIdFromMerchantUid(String merchantUid) {
        try {
            // 예: order_15_1709999999 -> 15 추출
            String[] parts = merchantUid.split("_");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (NumberFormatException e) {
            log.warn("ID 파싱 실패: {}", merchantUid);
        }
        return null;
    }
}