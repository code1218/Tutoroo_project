package com.tutoroo.dto;

import lombok.Builder;
import java.util.List;

/**
 * [기능: 랭킹 정보 전송 객체 (Record)]
 * 설명: 전체 랭킹 리스트, 상위 3명, 그리고 내 랭킹 정보를 포함합니다.
 */
public record RankingDTO(
        // 1~3등 (메달 표시 및 상단 강조용)
        List<RankEntry> topRankers,

        // 전체 랭킹 리스트 (최대 100명)
        List<RankEntry> allRankers,

        // [New] 내 랭킹 정보 (로그인 시에만 존재)
        RankEntry myRank
) {

    /**
     * [내부 레코드: 개별 랭킹 정보]
     */
    @Builder
    public record RankEntry(
            int rank,             // 등수
            String maskedName,    // 마스킹된 이름 (예: 김*수)
            int totalPoint,       // 총점
            String profileImage,  // 프로필 이미지 URL
            String ageGroup       // 연령대 (예: 10대, 20대)
    ) {}

    /**
     * [내부 레코드: 랭킹 필터 요청]
     * 설명: 성별, 연령대별 랭킹 조회 시 사용 (@ModelAttribute 바인딩 호환)
     */
    public record FilterRequest(
            String gender,    // MALE, FEMALE
            Integer ageGroup  // 10, 20, 30...
    ) {}
}