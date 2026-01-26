package com.tutoroo.controller;

import com.tutoroo.dto.TutorDTO;
import com.tutoroo.security.CustomUserDetails;
import com.tutoroo.service.TutorService;
import com.tutoroo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tutor")
@RequiredArgsConstructor
public class TutorController {

    private final TutorService tutorService;
    private final UserService userService;

    // 1. 수업 시작 (DTO 내부에 needsTts가 포함되어 서비스로 전달됨)
    @PostMapping("/class/start")
    public ResponseEntity<TutorDTO.ClassStartResponse> startClass(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody TutorDTO.ClassStartRequest request
    ) {
        return ResponseEntity.ok(tutorService.startClass(user.userEntity().getId(), request));
    }

    @GetMapping("/test/generate")
    public ResponseEntity<TutorDTO.DailyTestResponse> generateTest(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long planId,
            @RequestParam int dayCount
    ) {
        return ResponseEntity.ok(tutorService.generateTest(user.userEntity().getId(), planId, dayCount));
    }

    @PostMapping(value = "/test/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TutorDTO.TestFeedbackResponse> submitTest(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestPart("data") TutorDTO.TestSubmitRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.ok(tutorService.submitTest(
                user.userEntity().getId(),
                request.planId(),
                request.textAnswer(),
                image
        ));
    }

    // [수정 포인트] 채팅 시 needsTts 값 전달 추가
    @PostMapping("/feedback/chat")
    public ResponseEntity<TutorDTO.FeedbackChatResponse> chatWithTutor(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody TutorDTO.FeedbackChatRequest request
    ) {
        return ResponseEntity.ok(tutorService.adjustCurriculum(
                user.userEntity().getId(),
                request.planId(),
                request.message(),
                request.needsTts() // [New] 서비스 메서드 변경에 따라 인자 추가
        ));
    }

    @PostMapping("/review")
    public ResponseEntity<String> reviewTutor(@RequestBody TutorDTO.TutorReviewRequest request) {
        tutorService.saveStudentFeedback(request);
        return ResponseEntity.ok("피드백이 반영되었습니다.");
    }

    @GetMapping("/exam/generate")
    public ResponseEntity<TutorDTO.ExamGenerateResponse> generateExam(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long planId,
            @RequestParam int startDay,
            @RequestParam int endDay
    ) {
        return ResponseEntity.ok(tutorService.generateExam(user.userEntity().getId(), planId, startDay, endDay));
    }

    @PostMapping("/exam/submit")
    public ResponseEntity<TutorDTO.ExamResultResponse> submitExam(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody TutorDTO.ExamSubmitRequest request
    ) {
        return ResponseEntity.ok(tutorService.submitExam(user.userEntity().getId(), request));
    }

    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> convertSpeechToText(@RequestPart("audio") MultipartFile audio) {
        return ResponseEntity.ok(tutorService.convertSpeechToText(audio));
    }

    @PostMapping("/rival/match")
    public ResponseEntity<String> matchRival(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(userService.matchRival(user.userEntity().getId()));
    }

    @PatchMapping("/custom-name")
    public ResponseEntity<Void> renameCustomTutor(
            @RequestParam Long planId,
            @RequestParam String newName
    ) {
        tutorService.renameCustomTutor(planId, newName);
        return ResponseEntity.ok().build();
    }
}