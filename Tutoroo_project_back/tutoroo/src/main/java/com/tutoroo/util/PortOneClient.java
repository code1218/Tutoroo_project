package com.tutoroo.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class PortOneClient {

    @Value("${portone.api-key}")
    private String apiKey;

    @Value("${portone.api-secret}")
    private String apiSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getAccessToken() {
        String url = "https://api.iamport.kr/users/getToken";
        Map<String, String> body = Map.of("imp_key", apiKey, "imp_secret", apiSecret);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody().get("response");
            return (String) responseBody.get("access_token");
        } catch (Exception e) {
            throw new RuntimeException("PortOne 토큰 발급 실패: " + e.getMessage());
        }
    }

    public void cancelPayment(String impUid, String reason) {
        String token = getAccessToken();
        String url = "https://api.iamport.kr/payments/cancel";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        Map<String, String> body = Map.of(
                "imp_uid", impUid,
                "reason", reason
        );
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("결제 취소 실패: " + response.getBody());
            }
            log.info("결제 취소 성공: imp_uid={}, reason={}", impUid, reason);
        } catch (Exception e) {
            log.error("PortOne 결제 취소 API 호출 중 오류", e);
            throw new RuntimeException("결제 취소 연동 실패");
        }
    }

    // [New] 단건 결제 조회 (웹훅 검증용)
    public Map<String, Object> getPayment(String impUid) {
        String token = getAccessToken();
        String url = "https://api.iamport.kr/payments/" + impUid;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody().get("response");
            return responseBody;
        } catch (Exception e) {
            log.error("PortOne 결제 조회 실패: {}", e.getMessage());
            throw new RuntimeException("결제 조회 실패");
        }
    }
}