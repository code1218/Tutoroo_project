package com.tutoroo.controller;

import com.tutoroo.dto.AssessmentDTO;
import com.tutoroo.dto.StudyDTO;
import com.tutoroo.security.CustomUserDetails;
import com.tutoroo.service.AssessmentService;
import com.tutoroo.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/study")
@RequiredArgsConstructor
@Tag(name = "Study Management", description = "학습 관리 및 로드맵 API")
public class StudyController {

    private final StudyService studyService;
    private final AssessmentService assessmentService;

    @GetMapping("/status")
    @Operation(summary = "현재 학습 상태 조회")
    public ResponseEntity<StudyDTO.StudyStatusResponse> getStudyStatus(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(studyService.getCurrentStudyStatus(user.getId()));
    }

    // [Fix] AssessmentService의 createStudentRoadmap 호출 (인자 3개 전달)
    @PostMapping("/plans")
    @Operation(summary = "학습 플랜 생성 (간편 생성)")
    public ResponseEntity<AssessmentDTO.RoadmapResponse> createStudyPlan(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody StudyDTO.CreatePlanRequest request
    ) {
        AssessmentDTO.RoadmapRequest roadmapRequest = new AssessmentDTO.RoadmapRequest(
                request.goal(),
                request.teacherType(),
                null // 레벨 정보 없음 (기초로 간주)
        );
        return ResponseEntity.ok(assessmentService.createStudentRoadmap(user.getId(), roadmapRequest));
    }

    @DeleteMapping("/plans/{planId}")
    @Operation(summary = "학습 플랜 삭제")
    public ResponseEntity<Void> deleteStudyPlan(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long planId) {
        studyService.deleteStudyPlan(user.getId(), planId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logs")
    @Operation(summary = "학습 로그 저장")
    public ResponseEntity<String> saveStudyLog(@AuthenticationPrincipal CustomUserDetails user, @RequestBody StudyDTO.StudyLogRequest request) {
        studyService.saveSimpleLog(user.getId(), request);
        return ResponseEntity.ok("저장되었습니다.");
    }

    @PostMapping("/chat")
    @Operation(summary = "AI 튜터 채팅")
    public ResponseEntity<StudyDTO.ChatResponse> sendChatMessage(@AuthenticationPrincipal CustomUserDetails user, @RequestBody StudyDTO.SimpleChatRequest request) {
        return ResponseEntity.ok(studyService.handleSimpleChat(user.getId(), request.message()));
    }

    @GetMapping("/list")
    @Operation(summary = "내 학습 목록 조회")
    public ResponseEntity<List<StudyDTO.StudySimpleInfo>> getStudyList(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(studyService.getActiveStudyList(user.getId()));
    }

    @GetMapping("/current")
    @Operation(summary = "현재 플랜 상세 조회")
    public ResponseEntity<StudyDTO.PlanDetailResponse> getCurrentPlan(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(studyService.getCurrentPlanDetail(user.getId()));
    }

    @GetMapping("/check-limit/{userId}")
    public ResponseEntity<Boolean> checkLimit(@PathVariable Long userId) {
        return ResponseEntity.ok(studyService.canCreateNewGoal(userId));
    }

    @PatchMapping("/progress/{planId}")
    public ResponseEntity<String> updateProgress(@PathVariable Long planId, @RequestParam double rate) {
        studyService.updateProgress(planId, (int) rate);
        return ResponseEntity.ok("업데이트 완료");
    }

    @GetMapping("/calendar")
    public ResponseEntity<StudyDTO.CalendarResponse> getMonthlyCalendar(@AuthenticationPrincipal CustomUserDetails user, @RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(studyService.getMonthlyCalendar(user.getId(), year, month));
    }
}