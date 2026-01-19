package com.tutoroo.dto;

import lombok.Builder;

import java.util.List;

/**
 * [기능: 사용자 관련 데이터 전송 객체 (Record)]
 * 설명: 회원 정보 수정, 대시보드 조회, 탈퇴 요청 등에 사용됩니다.
 */
public class UserDTO {

    // 1. 회원 정보 수정 요청 (화면 스펙 반영)
    @Builder
    public record UpdateRequest(
            String currentPassword, // 보안 검증용 (필수)
            String newUsername,     // 변경할 아이디 (선택)
            String newPassword,     // 변경할 비밀번호 (선택)
            String name,            // [New] 변경할 이름 (선택)
            Integer age,            // [New] 변경할 나이 (선택)
            String email,           // 변경할 이메일 (선택)
            String phone            // 변경할 전화번호 (선택)
    ) {}

    // 1-1. 회원 정보 프로필 스냅샷 (변경 전/후 비교 및 초기 화면용)
    @Builder
    public record ProfileInfo(
            String username,
            String name,
            Integer age,            // [New] 나이 추가
            String email,
            String phone,
            String profileImage,
            String membershipTier
    ) {}

    // 1-2. 회원 정보 수정 응답 (Before & After)
    @Builder
    public record UpdateResponse(
            ProfileInfo before, // 변경 전 정보
            ProfileInfo after,  // 변경 후 정보
            String message
    ) {}

    // 2. 대시보드 응답 DTO
    @Builder
    public record DashboardDTO(
            String name,                // 사용자 이름
            String currentGoal,         // 현재 진행 중인 목표
            double progressRate,        // 진행률 (%)
            int currentPoint,           // 보유 포인트
            int rank,                   // 현재 랭킹
            String aiAnalysisReport,    // AI 분석 리포트
            String aiSuggestion,        // AI 추천 활동
            List<Integer> weeklyScores  // 최근 7일 성적 그래프용 데이터
    ) {}

    // 3. 회원 탈퇴 요청
    @Builder
    public record WithdrawRequest(
            String password,    // 본인 확인용 비밀번호
            String reason       // 탈퇴 사유
    ) {}

    // 4. 비밀번호 검증 요청 (마이페이지 진입용)
    @Builder
    public record PasswordVerifyRequest(
            String password
    ) {}
}