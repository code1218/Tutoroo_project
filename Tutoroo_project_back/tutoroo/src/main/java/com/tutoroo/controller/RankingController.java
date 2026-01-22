package com.tutoroo.controller;

import com.tutoroo.dto.RankingDTO;
import com.tutoroo.dto.RivalDTO;
import com.tutoroo.security.CustomUserDetails;
import com.tutoroo.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
@Tag(name = "Ranking", description = "랭킹 및 라이벌 관리 API")
public class RankingController {

    private final RankingService rankingService;

    // 1. 실시간 전체 랭킹 조회
    @GetMapping("/realtime")
    @Operation(summary = "실시간 랭킹 조회", description = "전체 유저 랭킹(TOP 100)과 내 순위를 0.01초 내에 조회합니다.")
    public ResponseEntity<RankingDTO> getRealtimeRanking(
            @AuthenticationPrincipal CustomUserDetails user) {
        Long userId = (user != null) ? user.getId() : null;
        return ResponseEntity.ok(rankingService.getRealtimeRankings(userId));
    }

    // 2. 조건별 필터링 랭킹 조회
    @GetMapping("/list")
    @Operation(summary = "랭킹 필터링 조회", description = "성별/연령대별 랭킹을 조회합니다.")
    public ResponseEntity<RankingDTO> getFilteredRankings(
            @ModelAttribute RankingDTO.FilterRequest filter,
            @AuthenticationPrincipal CustomUserDetails user) {
        Long userId = (user != null) ? user.getId() : null;
        return ResponseEntity.ok(rankingService.getFilteredRankings(filter, userId));
    }

    // 3. [New] 라이벌 비교 (Step 17)
    @GetMapping("/rival/compare")
    @Operation(summary = "라이벌 비교", description = "나의 라이벌과 점수, 순위를 비교합니다.")
    public ResponseEntity<RivalDTO.RivalComparisonResponse> compareRival(
            @AuthenticationPrincipal CustomUserDetails user) {
        // 비회원 접근 차단
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(rankingService.compareRival(user.getId()));
    }
}