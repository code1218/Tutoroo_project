package com.tutoroo.filter;

import com.tutoroo.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Request에서 토큰 추출 (헤더 우선, SSE는 파라미터 허용)
        String token = resolveToken(request);

        // 2. 토큰 유효성 검사
        if (token != null && jwtTokenProvider.validateToken(token)) {

            // 3. Redis 블랙리스트(로그아웃 여부) 확인
            // 키 형식: "BL:" + AccessToken
            String isLogout = redisTemplate.opsForValue().get("BL:" + token);

            if (isLogout != null) {
                // 로그아웃된 토큰은 접근 차단
                log.warn("🚨 로그아웃된 토큰 접근 차단 - URI: {}", request.getRequestURI());
            } else {
                // 4. 정상 토큰: 인증 객체 생성 및 Context 저장
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("✅ Security Context 인증 저장 완료: {}", authentication.getName());
            }
        }

        // 5. 다음 필터로 진행
        filterChain.doFilter(request, response);
    }

    /**
     * [토큰 추출 로직]
     * 1. Authorization 헤더 확인 (Bearer ~)
     * 2. SSE 요청(/api/notifications/subscribe)인 경우 쿼리 파라미터 확인
     */
    private String resolveToken(HttpServletRequest request) {
        // 1. 헤더에서 추출 (표준 방식)
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // 2. SSE 연결 요청의 경우 쿼리 파라미터(?token=...) 허용
        // 이유: EventSource는 헤더 설정이 제한적이므로 파라미터 방식 지원 필수
        if (request.getRequestURI().startsWith("/api/notifications/subscribe")) {
            String queryToken = request.getParameter("token");

            if (StringUtils.hasText(queryToken)) {
                // [방어 코드] 클라이언트가 파라미터에도 'Bearer '를 붙였을 경우 제거
                if (queryToken.startsWith("Bearer ")) {
                    return queryToken.substring(7);
                }
                return queryToken;
            }
        }

        return null;
    }
}