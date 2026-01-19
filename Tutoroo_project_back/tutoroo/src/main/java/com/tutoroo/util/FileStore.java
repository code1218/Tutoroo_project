package com.tutoroo.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

/**
 * [기능: 파일 저장소 유틸리티]
 * 설명: 바이트 배열(byte[]) 또는 Base64 문자열을 받아 서버 디스크에 저장하고, 웹 접근 경로를 반환합니다.
 */
@Slf4j
@Component
public class FileStore {

    // 기본 업로드 루트 경로 (예: ./uploads/)
    @Value("${file.upload-root:./uploads/}")
    private String uploadRoot;

    /**
     * 1. [핵심] 바이트 배열(byte[])을 파일로 저장
     * [개선] 확장자에 따라 폴더를 분리하여 저장합니다.
     */
    public String storeFile(byte[] fileData, String extension) {
        if (fileData == null || fileData.length == 0) {
            throw new RuntimeException("파일 데이터가 비어있습니다.");
        }

        try {
            // 1. 파일 타입별 서브 폴더 결정
            String subDir = "misc";
            if (extension.equalsIgnoreCase(".mp3") || extension.equalsIgnoreCase(".wav")) {
                subDir = "audio";
            } else if (extension.equalsIgnoreCase(".jpg") || extension.equalsIgnoreCase(".png") || extension.equalsIgnoreCase(".jpeg")) {
                subDir = "images";
            }

            // 2. 전체 경로 생성 (./uploads/audio/ 등)
            File directory = new File(uploadRoot + subDir);
            if (!directory.exists()) {
                boolean created = directory.mkdirs();
                if (!created) {
                    log.warn("디렉토리 생성 실패 또는 이미 존재함: {}", directory.getAbsolutePath());
                }
            }

            // 3. 랜덤 파일명 생성 (UUID 사용)
            String fileName = UUID.randomUUID() + extension;
            File destination = new File(directory, fileName);

            // 4. 파일 쓰기
            try (FileOutputStream fos = new FileOutputStream(destination)) {
                fos.write(fileData);
            }

            log.info("파일 저장 완료: {}", destination.getAbsolutePath());

            // 5. 웹 접근 경로 반환 (예: /uploads/audio/uuid.mp3)
            // WebConfig에서 addResourceHandlers 설정 필요 (예: /uploads/** -> file:./uploads/)
            return "/" + subDir + "/" + fileName;

        } catch (IOException e) {
            log.error("파일 저장 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("파일 저장 실패", e);
        }
    }

    /**
     * 2. [호환성] Base64 문자열을 디코딩하여 저장
     */
    public String saveAudio(String base64Audio) {
        if (base64Audio == null || base64Audio.isEmpty()) {
            throw new RuntimeException("Base64 오디오 데이터가 없습니다.");
        }
        byte[] decodedBytes = Base64.getDecoder().decode(base64Audio);
        return storeFile(decodedBytes, ".mp3");
    }
}