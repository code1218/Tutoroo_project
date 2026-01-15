package com.tutoroo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * [기능: 웹 설정 및 정적 리소스 매핑]
 * 설명: 로컬 디스크 파일 매핑 및 Multipart 요청 시 octet-stream 타입의 JSON 변환을 지원합니다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir:./uploads/audio/}")
    private String uploadDir;

    /**
     * [정적 리소스 매핑]
     * URL 패턴: /audio/** 요청이 오면 로컬의 uploadDir 폴더 내 파일을 반환합니다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourcePath = "file:" + uploadDir;

        if (!resourcePath.endsWith("/")) {
            resourcePath += "/";
        }

        registry.addResourceHandler("/audio/**")
                .addResourceLocations(resourcePath);
    }

    /**
     * [메시지 컨버터 확장]
     * Swagger UI 등에서 multipart/form-data 전송 시 JSON 파트가 application/octet-stream으로
     * 전송되는 경우를 대비하여, 해당 타입을 JSON으로 처리할 수 있도록 설정합니다.
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                // 기존 지원 타입 목록을 가져와서 octet-stream 추가
                List<MediaType> supportedTypes = new ArrayList<>(jacksonConverter.getSupportedMediaTypes());
                supportedTypes.add(MediaType.APPLICATION_OCTET_STREAM);
                jacksonConverter.setSupportedMediaTypes(supportedTypes);
            }
        }
    }
}