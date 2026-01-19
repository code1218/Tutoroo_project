package com.tutoroo.controller;

import com.tutoroo.dto.AuthDTO;
import com.tutoroo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증(로그인/가입) API")
public class AuthController {

    private final AuthService authService;

    // 1. 일반 회원가입
    @PostMapping(value = "/join", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "회원가입", description = "일반 계정으로 회원가입을 진행합니다.")
    public ResponseEntity<String> register(
            @RequestPart("data") AuthDTO.JoinRequest request,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage
    ) {
        authService.register(request, profileImage);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    // 2. 로그인
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "아이디/비밀번호로 로그인하여 토큰을 발급받습니다.")
    public ResponseEntity<AuthDTO.LoginResponse> login(@RequestBody AuthDTO.LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // 3. [New] 로그아웃 (토큰 무효화)
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "리프레시 토큰을 삭제하고 현재 토큰을 블랙리스트 처리합니다.")
    public ResponseEntity<String> logout(
            @RequestHeader("Authorization") String accessToken,
            @RequestHeader(value = "RefreshToken", required = false) String refreshToken
    ) {
        // Bearer 제거
        String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
        authService.logout(token, refreshToken);
        return ResponseEntity.ok("성공적으로 로그아웃되었습니다.");
    }

    // 4. 토큰 재발급
    @PostMapping("/reissue")
    @Operation(summary = "토큰 재발급", description = "리프레시 토큰을 이용해 액세스 토큰을 갱신합니다.")
    public ResponseEntity<AuthDTO.LoginResponse> reissue(@RequestHeader("RefreshToken") String refreshToken) {
        return ResponseEntity.ok(authService.reissue(refreshToken));
    }

    // 5. 소셜 가입 추가 정보 입력
    @PostMapping(value = "/oauth/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthDTO.LoginResponse> completeSocialSignup(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("data") AuthDTO.SocialSignupRequest request,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage
    ) {
        return ResponseEntity.ok(authService.completeSocialSignup(userDetails.getUsername(), request, profileImage));
    }

    // 6. 아이디 중복 체크
    @GetMapping("/check-id")
    public ResponseEntity<Boolean> checkId(@RequestParam String username) {
        return ResponseEntity.ok(authService.isUsernameAvailable(username));
    }

    // 7. 아이디 찾기
    @PostMapping("/find-id")
    public ResponseEntity<AuthDTO.AccountInfoResponse> findId(@RequestBody AuthDTO.FindIdRequest request) {
        return ResponseEntity.ok(authService.findUsername(request));
    }

    // 8. 비밀번호 찾기 (임시 비번 발송)
    @PostMapping("/find-password")
    public ResponseEntity<String> findPassword(@RequestBody AuthDTO.ResetPasswordRequest request) {
        authService.sendTemporaryPassword(request);
        return ResponseEntity.ok("가입된 이메일로 임시 비밀번호를 발송했습니다.");
    }

    // 9. 이메일 인증번호 발송
    @PostMapping("/email/send-verification")
    public ResponseEntity<String> sendEmailVerification(@RequestParam String email) {
        authService.requestEmailVerification(email);
        return ResponseEntity.ok("인증번호가 메일로 발송되었습니다.");
    }

    // 10. 이메일 인증 확인
    @PostMapping("/email/verify")
    public ResponseEntity<Boolean> verifyEmail(@RequestParam String email, @RequestParam String code) {
        return ResponseEntity.ok(authService.verifyEmailCode(email, code));
    }
}