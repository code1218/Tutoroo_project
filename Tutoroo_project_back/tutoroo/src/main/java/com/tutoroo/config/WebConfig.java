package com.tutoroo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths; // [필수 추가] 경로 처리를 위해 필요
import java.util.ArrayList;
import java.util.List;

/**
 * [기능: 웹 설정 및 정적 리소스 매핑]
 * - 정적 파일 경로 매핑 (이미지, 오디오) -> 절대 경로로 개선됨
 * - JSON 메시지 컨버터 설정 (UTF-8, Octet-Stream 지원)
 * - Enum 대소문자 무시 설정 (Custom Converter 적용)
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-root:./uploads/}")
    private String uploadRoot;

    /**
     * [정적 리소스 매핑 개선]
     * 기존 상대 경로("./uploads")를 시스템 절대 경로("file:///D:/Projects/...")로 변환합니다.
     * 이렇게 해야 브라우저가 이미지를 요청했을 때 스프링이 정확한 위치를 찾아줍니다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // [핵심 수정] 상대 경로 -> 절대 경로 URI로 변환
        String rootPath = Paths.get(uploadRoot).toAbsolutePath().toUri().toString();

        // 경로 끝에 슬래시(/)가 없으면 추가 (스프링 리소스 핸들러 문법)
        if (!rootPath.endsWith("/")) {
            rootPath += "/";
        }

        // 오디오 파일 매핑
        registry.addResourceHandler("/audio/**")
                .addResourceLocations(rootPath + "audio/");

        // 이미지 파일 매핑
        registry.addResourceHandler("/images/**")
                .addResourceLocations(rootPath + "images/");
    }

    /**
     * [메시지 컨버터 확장]
     * 1. 파일 업로드 시 JSON 파트의 Content-Type이 application/octet-stream일 경우 처리 지원
     * 2. 한글 깨짐 방지를 위해 기본 인코딩을 UTF-8로 강제 설정
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                // 1. 옥텟 스트림(브라우저가 헤더 없이 보낼 때) 처리 지원
                List<MediaType> supportedTypes = new ArrayList<>(jacksonConverter.getSupportedMediaTypes());
                supportedTypes.add(MediaType.APPLICATION_OCTET_STREAM);
                jacksonConverter.setSupportedMediaTypes(supportedTypes);

                // 2. 한글 깨짐 방지 (UTF-8 강제)
                jacksonConverter.setDefaultCharset(StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * [포맷터 등록]
     * Enum 타입을 대소문자 구분 없이 처리하도록 커스텀 컨버터 공장을 등록합니다.
     * 예: "tiger" (String) -> PetType.TIGER (Enum)
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new CaseInsensitiveEnumConverterFactory());
    }

    /**
     * [내부 클래스] 소문자 String -> 대문자 Enum 자동 변환기
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static class CaseInsensitiveEnumConverterFactory implements ConverterFactory<String, Enum> {
        @Override
        public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
            return source -> {
                if (source.isEmpty()) {
                    return null;
                }
                // 입력된 문자열을 대문자로 변환 후 Enum 매핑 시도
                return (T) Enum.valueOf(targetType, source.trim().toUpperCase());
            };
        }
    }
}