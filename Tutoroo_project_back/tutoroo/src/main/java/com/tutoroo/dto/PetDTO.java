package com.tutoroo.dto;

import lombok.Builder;
import java.util.List;

public class PetDTO {

    // 1. 펫 상태 응답
    @Builder
    public record PetStatusResponse(
            Long petId,
            String petName,
            int fullness,
            int intimacy,
            int exp,
            int maxExp, // 다음 단계까지 필요한 경험치 (UI 게이지용)
            int cleanliness,
            int stress,
            int energy,
            boolean isSleeping,
            int stage,
            String petType,
            String status, // ACTIVE, GRADUATED
            String statusMessage
    ) {}

    // 2. 펫 상호작용 요청
    public record InteractionRequest(
            String actionType // FEED, PLAY, CLEAN, SLEEP, WAKE_UP
    ) {}

    // 3. 입양 가능한 펫 목록 응답 (초기 입양용)
    @Builder
    public record AdoptableListResponse(
            List<PetSummary> availablePets,
            String message // "회원님의 등급(BASIC)에서는 3마리 중 선택 가능합니다."
    ) {}

    @Builder
    public record PetSummary(
            String type,        // Enum Name
            String name,        // 한글 이름
            String description  // 설명
    ) {}

    // 4. 초기 입양 요청
    public record InitialAdoptRequest(
            String petType // 사용자가 선택한 종족
    ) {}

    // 5. 졸업 후 랜덤 알 후보 응답
    @Builder
    public record RandomEggResponse(
            List<PetSummary> candidates, // 알 후보 (Basic은 1개, Prem은 3개)
            int choiceCount // 선택 가능한 개수
    ) {}

    // 6. 알 선택 요청
    public record EggSelectRequest(
            String selectedPetType
    ) {}
}