package com.tutoroo.loader;

import com.tutoroo.entity.MembershipTier;
import com.tutoroo.entity.UserEntity;
import com.tutoroo.mapper.UserMapper;
import com.tutoroo.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * [기능: 초기 테스트 데이터 생성기]
 * 설명: 서버 실행 시 테스트용 유저 데이터를 DB와 Redis에 적재합니다.
 * 변경사항: 기존 데이터가 있어도 멈추지 않고, 없는 유저만 골라서 채워 넣습니다.
 */
@Slf4j
//@Component
//@Profile("local") // 개발자님 환경에 맞춰 설정 (필요 없다면 이 줄 삭제 가능)
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final UserMapper userMapper;
    private final RankingService rankingService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("🚀 초기 테스트 데이터 점검 및 생성을 시작합니다...");

        Random random = new Random();
        String commonPassword = passwordEncoder.encode("1234"); // 모든 테스트 계정 비번: 1234
        int createdCount = 0;

        // user1 ~ user100 까지 순회하며 검사
        for (int i = 1; i <= 100; i++) {
            String username = "user" + i;

            // [핵심 변경] 해당 유저가 이미 존재하는지 개별 체크
            if (userMapper.findByUsername(username) != null) {
                // 이미 있으면 건너뜀 (중복 생성 방지)
                continue;
            }

            // --- 존재하지 않으면 생성 시작 ---
            String name = "학생" + i;

            // 랜덤 데이터 생성
            int randomPoint = random.nextInt(5001); // 0 ~ 5000
            int randomAge = 1 + random.nextInt(100); // 1 ~ 100세
            String gender = (i % 2 == 0) ? "MALE" : "FEMALE";

            // 등급 랜덤 배정
            int tierRoll = random.nextInt(100);
            MembershipTier tier;
            if (tierRoll < 60) tier = MembershipTier.BASIC;
            else if (tierRoll < 90) tier = MembershipTier.STANDARD;
            else tier = MembershipTier.PREMIUM;

            // 엔티티 빌더 구성
            UserEntity user = UserEntity.builder()
                    .username(username)
                    .password(commonPassword)
                    .name(name)
                    .email(username + "@test.com")
                    .phone(String.format("010-0000-%04d", i))
                    .parentPhone(randomAge < 19 ? String.format("010-9999-%04d", i) : null)
                    .role("ROLE_USER")
                    .status("ACTIVE")
                    .membershipTier(tier)
                    .totalPoint(randomPoint)
                    .pointBalance(randomPoint)
                    .level((randomPoint / 1000) + 1)
                    .exp(randomPoint % 1000)
                    .age(randomAge)
                    .gender(gender)
                    .currentStreak(random.nextInt(10))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // DB 저장
            userMapper.save(user);

            // Redis 랭킹 동기화
            if (user.getId() != null) {
                rankingService.updateUserScore(user.getId(), user.getTotalPoint());
            }

            createdCount++;
        }

        if (createdCount > 0) {
            log.info("✅ 신규 테스트 유저 {}명 생성 완료 (총 100명 데이터 확보).", createdCount);
        } else {
            log.info("ℹ️ 모든 테스트 유저(user1~user100)가 이미 존재합니다. 추가 작업 없음.");
        }
    }
}