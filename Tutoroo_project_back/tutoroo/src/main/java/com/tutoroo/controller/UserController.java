package com.tutoroo.controller;

import com.tutoroo.dto.UserDTO;
import com.tutoroo.security.CustomUserDetails;
import com.tutoroo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 1. 회원 상세 정보 조회 (수정 화면 진입 시 "Before" 정보 표시용)
    @GetMapping("/profile")
    public ResponseEntity<UserDTO.ProfileInfo> getProfileInfo(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(userService.getProfileInfo(user.getUsername()));
    }

    // 2. 대시보드 조회
    @GetMapping("/dashboard")
    public ResponseEntity<UserDTO.DashboardDTO> getDashboard(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(userService.getAdvancedDashboard(user.getUsername()));
    }

    // 3. 회원 정보 수정 (이미지 포함) -> 결과로 "Before" & "After" 반환
    @PatchMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserDTO.UpdateResponse> updateUserInfo(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestPart(value = "data") UserDTO.UpdateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        UserDTO.UpdateResponse response = userService.updateUserInfo(user.getUsername(), request, image);
        return ResponseEntity.ok(response);
    }

    // 4. 라이벌 매칭 요청
    @PostMapping("/match-rival")
    public ResponseEntity<String> matchRival(@AuthenticationPrincipal CustomUserDetails user) {
        String result = userService.matchRival(user.getId());
        return ResponseEntity.ok(result);
    }

    // 5. 회원 탈퇴 요청
    @PostMapping("/withdraw")
    public ResponseEntity<String> withdraw(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody UserDTO.WithdrawRequest request) {

        userService.withdrawUser(user.getId(), request);
        return ResponseEntity.ok("회원 탈퇴 처리가 완료되었습니다. 90일 후 데이터가 영구 삭제됩니다.");
    }

    // 6. 비밀번호 검증 API (수정 화면 진입 전 보안 확인)
    @PostMapping("/verify-password")
    public ResponseEntity<String> verifyPassword(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody UserDTO.PasswordVerifyRequest request) {

        userService.verifyPassword(user.getId(), request.password());
        return ResponseEntity.ok("비밀번호 인증 성공");
    }
}