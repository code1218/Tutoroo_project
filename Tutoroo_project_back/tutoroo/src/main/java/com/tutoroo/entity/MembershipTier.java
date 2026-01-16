package com.tutoroo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum MembershipTier {

    // 1. BASIC (무료)
    // 초기 선택: 호랑이, 토끼, 거북이 (3종)
    // 졸업 후 알: 1개 제공 (선택 불가)
    BASIC(
            1, "tts-1", "alloy", "SIMPLE",
            Set.of(PetType.TIGER, PetType.RABBIT, PetType.TURTLE),
            1
    ),

    // 2. STANDARD (9,900원)
    // 초기 선택: Basic + 벌꿀오소리, 쿼카, 캥거루, 동양용 (7종)
    // 졸업 후 알: 2개 제공 (1개 선택)
    STANDARD(
            3, "tts-1", "shimmer", "WEEKLY",
            Set.of(PetType.TIGER, PetType.RABBIT, PetType.TURTLE,
                    PetType.HONEY_BADGER, PetType.QUOKKA, PetType.KANGAROO, PetType.EASTERN_DRAGON),
            2
    ),

    // 3. PREMIUM (29,900원)
    // 초기 선택: 모든 펫 (10종)
    // 졸업 후 알: 3개 제공 (1개 선택)
    PREMIUM(
            999, "tts-1-hd", "nova", "DEEP",
            Set.of(PetType.values()), // 모든 펫
            3
    );

    private final int maxActiveGoals;       // 최대 학습 목표 개수
    private final String ttsModel;          // TTS 모델
    private final String ttsVoice;          // TTS 성우
    private final String reportDetailLevel; // 리포트 상세도

    // [New] 초기 육성 시 선택 가능한 펫 목록
    private final Set<PetType> initialSelectablePets;

    // [New] 졸업 후 제공되는 랜덤 알의 개수 (선택지 개수)
    private final int eggChoiceCount;
}