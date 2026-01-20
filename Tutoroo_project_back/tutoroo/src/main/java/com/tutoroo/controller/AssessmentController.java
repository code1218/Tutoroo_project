package com.tutoroo.controller;

import com.tutoroo.dto.AssessmentDTO;
import com.tutoroo.security.CustomUserDetails;
import com.tutoroo.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assessment")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    /**
     * [Step 2] 수준 파악 상담 진행
     * 설명: 사용자가 입력한 목표(Goal)에 대해 AI가 심층 질문을 던져 수준을 파악합니다.
     * 흐름: 프론트엔드는 isFinished=true가 올 때까지 이 API를 반복 호출해야 합니다.
     */
    @PostMapping("/consult")
    public ResponseEntity<AssessmentDTO.ConsultResponse> consult(
            @RequestBody AssessmentDTO.ConsultRequest request
    ) {
        return ResponseEntity.ok(assessmentService.proceedConsultation(request));
    }

    /**
     * [Step 3] 상담 종료 후 최종 로드맵 생성
     * 설명: 상담 내역(History)을 바탕으로 AI가 레벨을 확정하고, 목차(빙산의 일각)와 상세(진짜 빙산)를 생성합니다.
     */
    @PostMapping("/generate")
    public ResponseEntity<AssessmentDTO.AssessmentResultResponse> generateRoadmap(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody AssessmentDTO.AssessmentSubmitRequest request
    ) {
        return ResponseEntity.ok(assessmentService.analyzeAndCreateRoadmap(user.getId(), request));
    }

    // --- 기타 기능 (레벨 테스트 등) ---

    @PostMapping("/test/start")
    public ResponseEntity<AssessmentDTO.LevelTestResponse> startLevelTest(
            @RequestBody AssessmentDTO.LevelTestRequest request
    ) {
        return ResponseEntity.ok(assessmentService.generateLevelTest(request));
    }

    @PostMapping("/test/submit")
    public ResponseEntity<AssessmentDTO.AssessmentResult> submitLevelTest(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody AssessmentDTO.TestSubmitRequest request
    ) {
        return ResponseEntity.ok(assessmentService.evaluateLevelTest(user.getId(), request));
    }

    @PostMapping("/roadmap/regenerate")
    public ResponseEntity<AssessmentDTO.AssessmentResultResponse> regenerateRoadmap(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long planId,
            @RequestBody AssessmentDTO.AssessmentSubmitRequest request
    ) {
        return ResponseEntity.ok(assessmentService.regenerateRoadmap(user.getId(), planId, request));
    }
}