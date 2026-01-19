package com.tutoroo.controller;

import com.tutoroo.dto.RankingDTO;
import com.tutoroo.security.CustomUserDetails;
import com.tutoroo.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    // 1. 실시간 전체 랭킹 조회
    @GetMapping("/realtime")
    public ResponseEntity<RankingDTO> getRealtimeRanking(
            @AuthenticationPrincipal CustomUserDetails user) {

        Long userId = (user != null) ? user.getId() : null;
        return ResponseEntity.ok(rankingService.getRealtimeRankings(userId));
    }

    // 2. 조건별 필터링 랭킹 조회 (성별, 연령대)
    @GetMapping("/list")
    public ResponseEntity<RankingDTO> getFilteredRankings(
            @ModelAttribute RankingDTO.FilterRequest filter,
            @AuthenticationPrincipal CustomUserDetails user) {

        Long userId = (user != null) ? user.getId() : null;
        return ResponseEntity.ok(rankingService.getFilteredRankings(filter, userId));
    }
}