package com.tutoroo.event;

import com.tutoroo.mapper.UserMapper;
import com.tutoroo.service.PetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PetEventListener {

    private final UserMapper userMapper;
    private final PetService petService;

    @EventListener
    public void handleStudyCompleted(StudyCompletedEvent event) {
        // 1. 유저 포인트 획득 (랭킹 + 지갑 모두 증가)
        int earnedPoint = event.getScore();
        userMapper.earnPoints(event.getUserId(), earnedPoint);
        log.info("학습 보상: 유저 포인트 +{} (랭킹/지갑 반영)", earnedPoint);

        // 2. 펫 경험치 지급 (60점 이상일 때 보너스)
        if (event.getScore() >= 60) {
            int expGain = event.getScore() / 2;
            log.info("학습 보상: 펫 경험치 +{}", expGain);
            petService.gainExp(event.getUserId(), expGain);
        }
    }
}