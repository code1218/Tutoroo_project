package com.tutoroo.dto;

import lombok.Builder;

public class RivalDTO {

    // 라이벌 비교 응답
    @Builder
    public record RivalComparisonResponse(
            boolean hasRival,           // 라이벌 존재 여부
            RivalProfile myProfile,     // 내 정보
            RivalProfile rivalProfile,  // 라이벌 정보
            String message,             // 상태 메시지 (예: "라이벌보다 150점 앞서고 있어요!")
            int pointGap                // 점수 차이 (절대값)
    ) {}

    @Builder
    public record RivalProfile(
            Long userId,
            String name,
            String profileImage,
            int totalPoint,
            int rank,           // 전체 순위 (없으면 0)
            int level,
            String tier         // BASIC, STANDARD...
    ) {}
}