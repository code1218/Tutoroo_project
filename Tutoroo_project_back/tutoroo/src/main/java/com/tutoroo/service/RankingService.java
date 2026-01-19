package com.tutoroo.service;

import com.tutoroo.dto.RankingDTO;
import com.tutoroo.entity.UserEntity;
import com.tutoroo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final UserMapper userMapper;
    private final RedisTemplate<String, String> redisTemplate;

    // Redis Key 상수
    private static final String LEADERBOARD_KEY = "leaderboard:total";

    /**
     * [기능: 실시간 랭킹 조회 (Redis ZSet 최적화)]
     * 설명: ZSet에서 Top 100을 조회하고, 로그인한 유저의 경우 자신의 실시간 등수도 함께 반환합니다.
     */
    @Transactional(readOnly = true)
    public RankingDTO getRealtimeRankings(Long myUserId) {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        // 1. 전체 랭킹 (1~100위) 조회 (Redis ZSet Reverse Range)
        Set<String> topUserIds = zSetOps.reverseRange(LEADERBOARD_KEY, 0, 99);

        // 랭킹 데이터가 없을 경우 빈 객체 반환
        if (topUserIds == null || topUserIds.isEmpty()) {
            return new RankingDTO(Collections.emptyList(), Collections.emptyList(), null);
        }

        // 2. 유저 상세 정보 조회 (DB) 및 리스트 변환
        List<RankingDTO.RankEntry> allRankers = new ArrayList<>();
        int currentRank = 1;

        for (String userIdStr : topUserIds) {
            try {
                Long userId = Long.parseLong(userIdStr);
                UserEntity user = userMapper.findById(userId);

                if (user != null) {
                    Double score = zSetOps.score(LEADERBOARD_KEY, userIdStr);
                    int totalPoint = (score != null) ? score.intValue() : user.getTotalPoint();

                    allRankers.add(RankingDTO.RankEntry.builder()
                            .rank(currentRank++)
                            .maskedName(user.getMaskedName())
                            .totalPoint(totalPoint)
                            .profileImage(user.getProfileImage())
                            .ageGroup(getAgeGroup(user.getAge()))
                            .build());
                }
            } catch (NumberFormatException e) {
                log.warn("랭킹 파싱 오류: {}", userIdStr);
            }
        }

        // 3. 상위 3명 추출
        List<RankingDTO.RankEntry> topRankers = allRankers.stream().limit(3).toList();

        // 4. [New] 내 랭킹 조회 로직
        RankingDTO.RankEntry myRankEntry = null;
        if (myUserId != null) {
            myRankEntry = getMyRealtimeRank(myUserId, zSetOps);
        }

        return new RankingDTO(topRankers, allRankers, myRankEntry);
    }

    /**
     * [기능: 필터링 랭킹 조회]
     * 설명: 성별/연령별 랭킹 리스트를 조회하고, 그 안에서 내 순위를 찾습니다.
     */
    @Transactional(readOnly = true)
    public RankingDTO getFilteredRankings(RankingDTO.FilterRequest filter, Long myUserId) {
        // DB에서 조건에 맞는 유저들을 점수 내림차순으로 가져옴
        List<UserEntity> users = userMapper.getRankingList(filter.gender(), filter.ageGroup());

        List<RankingDTO.RankEntry> rankEntries = new ArrayList<>();
        RankingDTO.RankEntry myRankEntry = null;

        for (int i = 0; i < users.size(); i++) {
            UserEntity u = users.get(i);
            int rank = i + 1;

            RankingDTO.RankEntry entry = RankingDTO.RankEntry.builder()
                    .rank(rank)
                    .maskedName(u.getMaskedName())
                    .totalPoint(u.getTotalPoint())
                    .profileImage(u.getProfileImage())
                    .ageGroup(getAgeGroup(u.getAge()))
                    .build();

            rankEntries.add(entry);

            // 리스트를 순회하면서 '나'를 발견하면 내 랭킹 정보 저장
            if (myUserId != null && u.getId().equals(myUserId)) {
                myRankEntry = entry;
            }
        }

        return new RankingDTO(
                rankEntries.stream().limit(3).toList(),
                rankEntries,
                myRankEntry
        );
    }

    /**
     * [기능: 랭킹 점수 업데이트]
     * 설명: 유저 점수가 변경될 때 Redis ZSet을 즉시 갱신합니다.
     */
    public void updateUserScore(Long userId, int totalPoint) {
        try {
            redisTemplate.opsForZSet().add(LEADERBOARD_KEY, String.valueOf(userId), totalPoint);
            log.info("🏆 랭킹 업데이트 완료: UserID={} Point={}", userId, totalPoint);
        } catch (Exception e) {
            log.error("랭킹 업데이트 실패: {}", e.getMessage());
        }
    }

    // --- Helper Methods ---

    // Redis에서 내 순위와 점수 직접 조회
    private RankingDTO.RankEntry getMyRealtimeRank(Long myUserId, ZSetOperations<String, String> zSetOps) {
        try {
            String userIdStr = String.valueOf(myUserId);

            // 내 순위 조회 (0부터 시작하므로 +1)
            Long rankIndex = zSetOps.reverseRank(LEADERBOARD_KEY, userIdStr);
            // 내 점수 조회
            Double score = zSetOps.score(LEADERBOARD_KEY, userIdStr);

            if (rankIndex != null && score != null) {
                UserEntity me = userMapper.findById(myUserId);
                if (me != null) {
                    return RankingDTO.RankEntry.builder()
                            .rank(rankIndex.intValue() + 1)
                            .maskedName(me.getMaskedName())
                            .totalPoint(score.intValue())
                            .profileImage(me.getProfileImage())
                            .ageGroup(getAgeGroup(me.getAge()))
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("내 랭킹 조회 실패: {}", e.getMessage());
        }
        return null; // 랭킹에 없거나 오류 발생 시
    }

    // 연령대 계산 (10대, 20대 ...)
    private String getAgeGroup(Integer age) {
        if (age == null) return "알수없음";
        int group = (age / 10) * 10;
        return group + "대";
    }
}