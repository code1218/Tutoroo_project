package com.tutoroo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetInfoEntity {
    private Long petId;
    private Long userId;
    private String petName;

    private String petType;     // PetType Enum Name (TIGER, RABBIT, CUSTOM...)

    // [New] 커스텀 펫 정보
    private String customDescription; // 유저가 입력한 외형 묘사
    private String customImageUrl;    // AI가 생성한 이미지 URL

    private int stage;          // 1~5
    private String status;      // ACTIVE, GRADUATED

    private int fullness;
    private int intimacy;
    private int exp;

    private int cleanliness;
    private int stress;
    private int energy;
    private boolean isSleeping;

    private String equippedItems;

    private LocalDateTime lastFedAt;
    private LocalDateTime lastPlayedAt;
    private LocalDateTime lastCleanedAt;
    private LocalDateTime lastSleptAt;
    private LocalDateTime birthDate;
    private LocalDateTime createdAt;
}