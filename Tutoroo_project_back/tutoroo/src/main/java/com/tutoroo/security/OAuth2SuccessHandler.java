package com.tutoroo.security;

import com.tutoroo.entity.UserEntity;
import com.tutoroo.jwt.JwtTokenProvider;
import com.tutoroo.mapper.UserMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // 1. OAuth2User 정보 가져오기
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        log.info("OAuth2 Login Success. Attributes: {}", attributes);

        // 2. 이메일 추출 (제공자별 속성 차이 대응)
        String email = null;
        if (attributes.containsKey("email")) {
            email = (String) attributes.get("email");
        } else if (attributes.containsKey("kakao_account")) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            if (kakaoAccount != null && kakaoAccount.containsKey("email")) {
                email = (String) kakaoAccount.get("email");
            }
        } else if (attributes.containsKey("response")) { // Naver
            Map<String, Object> responseMap = (Map<String, Object>) attributes.get("response");
            if (responseMap != null && responseMap.containsKey("email")) {
                email = (String) responseMap.get("email");
            }
        }

        if (email == null) {
            log.error("Email not found in OAuth2 attributes");
            response.sendRedirect(redirectUri + "/login?error=email_not_found");
            return;
        }

        // 3. DB에서 유저 조회
        UserEntity user = userMapper.findByUsername(email);

        // 4. 토큰 생성을 위한 내부 Authentication 객체 생성
        // (중요: OAuth2AuthenticationToken을 바로 넘기면 안 됨)
        Authentication internalAuth = new UsernamePasswordAuthenticationToken(
                email,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // 5. 토큰 발급
        String accessToken = jwtTokenProvider.generateAccessToken(internalAuth);
        String refreshToken = jwtTokenProvider.generateRefreshToken(internalAuth);

        // 6. 프론트엔드로 리다이렉트
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .queryParam("isNewUser", (user == null))
                .build().toUriString();

        response.sendRedirect(targetUrl);
    }
}