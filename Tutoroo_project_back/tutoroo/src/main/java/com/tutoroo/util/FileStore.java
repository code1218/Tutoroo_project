package com.tutoroo.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

/**
 * [기능: 파일 저장소 유틸리티]
 * 설명: 파일 저장(Store) 및 삭제(Delete) 기능을 제공합니다.
 */
@Slf4j
@Component
public class FileStore {

    @Value("${file.upload-root:./uploads/}")
    private String uploadRoot;

    /**
     * 1. [핵심] 바이트 배열(byte[])을 파일로 저장
     * 리턴값: 웹 접근 URL (예: /images/uuid.jpg)
     */
    public String storeFile(byte[] fileData, String extension) {
        if (fileData == null || fileData.length == 0) {
            throw new RuntimeException("파일 데이터가 비어있습니다.");
        }

        try {
            // 1. 서브 폴더 결정 (images, audio, misc)
            String subDir = determineSubDir(extension);

            // 2. 저장할 디렉토리 경로 생성
            // uploadRoot가 "./uploads"일 수도 있고 "./uploads/"일 수도 있어서 안전하게 처리
            File rootDir = new File(uploadRoot);
            File directory = new File(rootDir, subDir);

            if (!directory.exists()) {
                boolean created = directory.mkdirs();
                if (!created) {
                    log.warn("디렉토리 생성 실패 또는 이미 존재함: {}", directory.getAbsolutePath());
                }
            }

            // 3. 고유 파일명 생성 (UUID)
            String fileName = UUID.randomUUID() + extension;
            File destination = new File(directory, fileName);

            // 4. 파일 쓰기
            try (FileOutputStream fos = new FileOutputStream(destination)) {
                fos.write(fileData);
            }

            log.info("파일 저장 완료: {}", destination.getAbsolutePath());

            // 5. 웹 접근 URL 반환 (/images/uuid.jpg)
            return "/" + subDir + "/" + fileName;

        } catch (IOException e) {
            log.error("파일 저장 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("파일 저장 실패", e);
        }
    }

    /**
     * 2. [추가] 파일 삭제 기능
     * 용도: 프로필 수정 시 기존 이미지를 삭제하여 서버 용량을 확보합니다.
     * @param fileUrl 웹 접근 URL (예: /images/uuid.jpg)
     */
    public void deleteFile(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }

        try {
            // URL(/images/abc.jpg) -> 파일경로(./uploads/images/abc.jpg) 변환
            // 1. 맨 앞의 슬래시(/) 제거
            String relativePath = fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl;

            // 2. 전체 경로 조합
            // Paths.get을 사용하면 운영체제(Windows/Mac)에 맞는 구분자로 자동 처리해줌
            Path filePath = Paths.get(uploadRoot, relativePath);

            // 3. 파일 삭제
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("기존 파일 삭제 성공: {}", filePath);
            } else {
                log.debug("삭제할 파일이 존재하지 않음: {}", filePath);
            }

        } catch (Exception e) {
            // 파일 삭제 실패는 서비스 핵심 로직(회원수정)을 막으면 안 되므로 로그만 남김
            log.warn("파일 삭제 중 오류 발생 (무시됨): {} - {}", fileUrl, e.getMessage());
        }
    }

    /**
     * 3. [호환성] Base64 문자열을 디코딩하여 저장 (오디오용)
     */
    public String saveAudio(String base64Audio) {
        if (!StringUtils.hasText(base64Audio)) {
            throw new RuntimeException("Base64 오디오 데이터가 없습니다.");
        }
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Audio);
            return storeFile(decodedBytes, ".mp3");
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("잘못된 Base64 형식입니다.", e);
        }
    }

    // --- Helper ---
    private String determineSubDir(String extension) {
        if (extension == null) return "misc";
        String ext = extension.toLowerCase();

        if (ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".png") || ext.endsWith(".gif")) {
            return "images";
        } else if (ext.endsWith(".mp3") || ext.endsWith(".wav") || ext.endsWith(".m4a")) {
            return "audio";
        }
        return "misc";
    }
}