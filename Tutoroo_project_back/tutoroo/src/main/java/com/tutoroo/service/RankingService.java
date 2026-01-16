package com.tutoroo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutoroo.dto.RankingDTO;
import com.tutoroo.entity.UserEntity;
import com.tutoroo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * [기능: 랭킹 서비스 - Redis Caching 적용]
 * 설명: 복잡한 필터링 쿼리는 DB가 수행하되, 결과를 Redis에 캐싱하여 성능을 극대화합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final UserMapper userMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // 캐시 만료 시간 (10분 - 랭킹은 실시간성이 중요하지만 10분 정도의 텀은 허용됨)
    private static final long CACHE_TTL_MINUTES = 10;

    /**
     * [기능: 실시간 전체 랭킹 조회]
     * 설명: 필터 조건 없이 상위 100명을 조회합니다.
     */
    @Transactional(readOnly = true)
    public RankingDTO getRealtimeRankings() {
        return getFilteredRankings(new RankingDTO.FilterRequest(null, null));
    }

    /**
     * [기능: 필터링된 랭킹 조회 (Redis Cache 적용)]
     * 설명: Redis를 우선 조회하고, 없으면 DB에서 조회 후 캐싱합니다.
     */
    @Transactional(readOnly = true)
    public RankingDTO getFilteredRankings(RankingDTO.FilterRequest filter) {
        // 1. Redis Key 생성 (예: "ranking:filter:MALE:20" or "ranking:filter:ALL:ALL")
        String cacheKey = generateCacheKey(filter);

        // 2. Redis 캐시 조회
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cachedJson)) {
            try {
                // 캐시가 있다면 DB 조회 없이 바로 반환 (Cache Hit)
                return objectMapper.readValue(cachedJson, RankingDTO.class);
            } catch (Exception e) {
                log.error("Redis 캐시 파싱 실패 (Key: {}): {}", cacheKey, e.getMessage());
                // 파싱 실패 시 DB 조회로 넘어감 (Fallback)
            }
        }

        // 3. DB 조회 (Cache Miss)
        // UserMapper.xml의 <choose> 로직을 통해 연령대/성별 필터링 수행
        List<UserEntity> users = userMapper.getRankingList(filter.gender(), filter.ageGroup());

        // 4. Entity -> DTO 변환
        List<RankingDTO.RankEntry> rankEntries = new ArrayList<>();
        AtomicInteger rankCounter = new AtomicInteger(1);

        for (UserEntity user : users) {
            rankEntries.add(RankingDTO.RankEntry.builder()
                    .rank(rankCounter.getAndIncrement())
                    .maskedName(user.getMaskedName())
                    .totalPoint(user.getTotalPoint())
                    .profileImage(user.getProfileImage())
                    .ageGroup(convertAgeToGroupString(user.getAge()))
                    .build());
        }

        // 상위 3명 추출
        List<RankingDTO.RankEntry> topRankers = rankEntries.stream()
                .limit(3)
                .toList();

        RankingDTO result = RankingDTO.builder()
                .topRankers(topRankers)
                .allRankers(rankEntries)
                .build();

        // 5. Redis에 결과 저장 (캐싱)
        try {
            String jsonResult = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, jsonResult, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.info("🏆 랭킹 캐시 저장 완료 (Key: {})", cacheKey);
        } catch (Exception e) {
            log.error("Redis 캐시 저장 실패: {}", e.getMessage());
        }

        return result;
    }

    /**
     * [보조 기능: 유저 랭킹 점수 업데이트]
     * 설명: 점수가 변경되면 관련 캐시를 무효화(Evict)해야 데이터 정합성이 유지됩니다.
     */
    public void updateUserScore(Long userId, int totalPoint) {
        // 방법 1: 단순히 모든 랭킹 캐시를 날림 (구현 간단, 성능 비용 약간 있음)
        // 방법 2: 해당 유저의 성별/나이를 계산해서 특정 키만 날림 (복잡함)
        // 여기서는 안전하게 전체 랭킹 캐시 패턴 삭제를 권장
        try {
            // "ranking:filter:*" 패턴을 가진 모든 키 삭제
            var keys = redisTemplate.keys("ranking:filter:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("🔄 점수 변동으로 인한 랭킹 캐시 초기화 완료");
            }
        } catch (Exception e) {
            log.warn("랭킹 캐시 초기화 중 오류: {}", e.getMessage());
        }
    }

    /**
     * [내부 로직: Redis Key 생성]
     */
    private String generateCacheKey(RankingDTO.FilterRequest filter) {
        String genderKey = (filter.gender() != null) ? filter.gender() : "ALL";
        String ageKey = (filter.ageGroup() != null) ? String.valueOf(filter.ageGroup()) : "ALL";
        return "ranking:filter:" + genderKey + ":" + ageKey;
    }

    /**
     * [내부 로직: 나이를 연령대 문자열로 변환]
     */
    private String convertAgeToGroupString(Integer age) {
        if (age == null) return "알 수 없음";
        if (age < 10) return "10대 미만";
        if (age >= 60) return "60대 이상";
        return (age / 10) * 10 + "대";
    }
}