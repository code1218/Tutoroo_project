package com.tutoroo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 1. 허용할 프론트엔드 도메인
        config.setAllowedOrigins(List.of("http://localhost:5173"));

        // 2. 허용할 HTTP 메서드
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // 3. 허용할 헤더
        config.setAllowedHeaders(List.of("*"));

        // 4. 자격 증명 허용 (쿠키, Authorization 헤더 등)
        config.setAllowCredentials(true);

        // 5. 노출할 헤더 (프론트에서 엑세스 토큰 접근 허용 등)
        config.setExposedHeaders(List.of("Authorization", "RefreshToken"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}