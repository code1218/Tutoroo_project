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
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-root:./uploads/}")
    private String uploadRoot;

    /**
     * [정적 리소스 매핑 개선]
     * URL 패턴: /audio/** -> ./uploads/audio/
     * URL 패턴: /images/** -> ./uploads/images/
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String rootPath = "file:" + uploadRoot;
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

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                List<MediaType> supportedTypes = new ArrayList<>(jacksonConverter.getSupportedMediaTypes());
                supportedTypes.add(MediaType.APPLICATION_OCTET_STREAM);
                jacksonConverter.setSupportedMediaTypes(supportedTypes);
            }
        }
    }
}