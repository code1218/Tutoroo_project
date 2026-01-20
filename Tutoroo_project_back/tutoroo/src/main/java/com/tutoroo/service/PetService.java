package com.tutoroo.service;

import com.tutoroo.dto.PetDTO;
import com.tutoroo.entity.*;
import com.tutoroo.exception.ErrorCode;
import com.tutoroo.exception.TutorooException;
import com.tutoroo.mapper.PetMapper;
import com.tutoroo.mapper.UserMapper;
import com.tutoroo.util.FileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetService {

    private final PetMapper petMapper;
    private final UserMapper userMapper;
    private final ChatClient.Builder chatClientBuilder;
    private final ImageModel imageModel;
    private final FileStore fileStore;
    private final RedisTemplate<String, String> redisTemplate;

    private static final int FULLNESS_DECAY_PER_HOUR = 5;
    private static final int INTIMACY_DECAY_PER_HOUR = 3;
    private static final int RUNAWAY_THRESHOLD = 20;

    // 상호작용 비용 및 효과 상수
    private static final int COST_FEED = 20;
    private static final int EXP_FEED = 5;
    private static final int EXP_PLAY = 10;
    private static final int EXP_CLEAN = 5;

    // --- [1] 펫 상태 조회 ---
    @Transactional(readOnly = true)
    public PetDTO.PetStatusResponse getPetStatus(Long userId) {
        PetInfoEntity pet = petMapper.findByUserId(userId);
        if (pet == null) return null;

        updatePetStats(pet);
        petMapper.updatePet(pet);

        int maxExp = petMapper.findRequiredExpForNextStage(pet.getStage());
        return mapToDTO(pet, maxExp);
    }

    // --- [2] 초기 입양 ---
    @Transactional(readOnly = true)
    public PetDTO.AdoptableListResponse getAdoptablePets(Long userId) {
        UserEntity user = userMapper.findById(userId);
        Set<PetType> allowedPets = user.getEffectiveTier().getAllowedPets();

        List<PetDTO.PetSummary> summaries = allowedPets.stream()
                .map(type -> new PetDTO.PetSummary(type.name(), type.getName(), type.getDescription()))
                .toList();

        return PetDTO.AdoptableListResponse.builder()
                .availablePets(summaries)
                .message(String.format("회원님의 %s 등급에서는 %d마리의 펫을 선택할 수 있습니다.",
                        user.getEffectiveTier().name(), summaries.size()))
                .build();
    }

    @Transactional
    public void adoptInitialPet(Long userId, String petTypeStr) {
        if (petMapper.findByUserId(userId) != null) {
            throw new TutorooException(ErrorCode.ALREADY_HAS_PET);
        }

        PetType type;
        try {
            type = PetType.valueOf(petTypeStr);
        } catch (IllegalArgumentException e) {
            throw new TutorooException(ErrorCode.INVALID_PET_TYPE);
        }

        PetInfoEntity newPet = PetInfoEntity.builder()
                .userId(userId)
                .petName(type.getName())
                .petType(type.name())
                .stage(1)
                .status("ACTIVE")
                .fullness(80)
                .intimacy(80)
                .exp(0)
                .cleanliness(100)
                .stress(0)
                .energy(100)
                .isSleeping(false)
                .createdAt(LocalDateTime.now())
                .lastFedAt(LocalDateTime.now())
                .lastPlayedAt(LocalDateTime.now())
                .lastCleanedAt(LocalDateTime.now())
                .lastSleptAt(LocalDateTime.now())
                .build();

        petMapper.createPet(newPet);
    }

    // --- [3] 상호작용 (밥주기, 놀기 등) ---
    @Transactional
    public PetDTO.PetStatusResponse interact(Long userId, String actionType) {
        PetInfoEntity pet = petMapper.findByUserId(userId);
        if (pet == null) throw new TutorooException(ErrorCode.PET_NOT_FOUND);

        // 시간 경과에 따른 스탯 감소 적용
        updatePetStats(pet);

        // 자고 있는 경우 깨우기 외에는 불가능
        if (pet.isSleeping() && !"WAKE_UP".equals(actionType)) {
            throw new TutorooException("펫이 자고 있습니다. 먼저 깨워주세요.", ErrorCode.PET_IS_SLEEPING);
        }

        UserEntity user = userMapper.findById(userId);

        switch (actionType) {
            case "FEED" -> {
                if (user.getPointBalance() < COST_FEED) {
                    throw new TutorooException(ErrorCode.INSUFFICIENT_POINTS);
                }
                userMapper.spendPoints(userId, COST_FEED);
                pet.setFullness(Math.min(100, pet.getFullness() + 30));
                pet.setExp(pet.getExp() + EXP_FEED);
                pet.setLastFedAt(LocalDateTime.now());
            }
            case "PLAY" -> {
                if (pet.getEnergy() < 10) {
                    throw new TutorooException("펫이 너무 피곤해합니다. 잠을 재워주세요.", ErrorCode.PET_TOO_TIRED);
                }
                pet.setIntimacy(Math.min(100, pet.getIntimacy() + 15));
                pet.setStress(Math.max(0, pet.getStress() - 10));
                pet.setEnergy(Math.max(0, pet.getEnergy() - 10));
                pet.setExp(pet.getExp() + EXP_PLAY);
                pet.setLastPlayedAt(LocalDateTime.now());
            }
            case "CLEAN" -> {
                pet.setCleanliness(100);
                pet.setStress(Math.max(0, pet.getStress() - 20));
                pet.setExp(pet.getExp() + EXP_CLEAN);
                pet.setLastCleanedAt(LocalDateTime.now());
            }
            case "SLEEP" -> {
                pet.setSleeping(true);
                pet.setLastSleptAt(LocalDateTime.now());
            }
            case "WAKE_UP" -> {
                pet.setSleeping(false);
                long sleptHours = Duration.between(pet.getLastSleptAt(), LocalDateTime.now()).toHours();
                int recovery = (int) (sleptHours * 10) + 20;
                pet.setEnergy(Math.min(100, pet.getEnergy() + recovery));
            }
            default -> throw new TutorooException(ErrorCode.INVALID_INPUT_VALUE);
        }

        checkLevelUp(pet);
        petMapper.updatePet(pet);

        int maxExp = petMapper.findRequiredExpForNextStage(pet.getStage());
        return mapToDTO(pet, maxExp);
    }

    // --- [4] 경험치 획득 (이벤트 등 외부 호출용) ---
    @Transactional
    public void gainExp(Long userId, int amount) {
        PetInfoEntity pet = petMapper.findByUserId(userId);
        if (pet == null || !"ACTIVE".equals(pet.getStatus())) return;

        pet.setExp(pet.getExp() + amount);
        checkLevelUp(pet);
        petMapper.updatePet(pet);
    }

    // --- [5] 가출 체크 ---
    @Transactional
    public List<Long> processBatchRunaways() {
        List<PetInfoEntity> activePets = petMapper.findAllActivePets();
        List<Long> runawayUserIds = new ArrayList<>();

        for (PetInfoEntity pet : activePets) {
            updatePetStats(pet);
            if (pet.getIntimacy() <= RUNAWAY_THRESHOLD) {
                pet.setStatus("RUNAWAY");
                runawayUserIds.add(pet.getUserId());
                log.info("🚨 펫 가출 발생! PetId: {}", pet.getPetId());
            }
            petMapper.updatePet(pet);
        }
        return runawayUserIds;
    }

    // --- [6] 미드나잇 다이어리 (AI 그림 일기) ---
    @Transactional
    public void writeMidnightDiary(Long userId) {
        PetInfoEntity pet = petMapper.findByUserId(userId);
        if (pet == null || !"ACTIVE".equals(pet.getStatus())) return;

        try {
            // 1. 일기 내용 생성
            String prompt = String.format("""
                    너는 %s야. 오늘 주인님과 함께한 하루를 짧은 일기(3문장)로 써줘.
                    현재 기분: %s (배고픔: %d, 친밀도: %d).
                    말투는 귀엽게 해.
                    """, pet.getPetName(), (pet.getIntimacy() > 80 ? "행복함" : "심심함"), pet.getFullness(), pet.getIntimacy());

            String content = chatClientBuilder.build().prompt().user(prompt).call().content();

            // 2. 일기 그림 생성 (DALL-E)
            String imagePrompt = String.format(
                    "A cute %s character looking %s, simple vector art style, pastel colors, white background",
                    pet.getPetType().toLowerCase(),
                    (pet.getIntimacy() > 80 ? "happy" : "sad")
            );

            ImageResponse imageResponse = imageModel.call(new ImagePrompt(imagePrompt,
                    OpenAiImageOptions.builder()
                            .withN(1)
                            .withHeight(1024)
                            .withWidth(1024)
                            .build()));
            String imageUrl = imageResponse.getResult().getOutput().getUrl();

            String diaryContent = content + "\n\n![그림일기](" + imageUrl + ")";

            // 3. DB 저장
            PetDiaryEntity diary = PetDiaryEntity.builder()
                    .petId(pet.getPetId())
                    .date(LocalDate.now())
                    .content(diaryContent)
                    .mood(pet.getIntimacy() > 80 ? "HAPPY" : "SAD")
                    .createdAt(LocalDateTime.now())
                    .build();

            petMapper.saveDiary(diary);
            log.info("📖 펫 일기 작성 완료: {}", pet.getPetId());

        } catch (Exception e) {
            log.error("일기 생성 실패: {}", e.getMessage());
        }
    }

    // --- [7] 졸업 후 알 관련 (구현 완료) ---
    @Transactional(readOnly = true)
    public PetDTO.RandomEggResponse getGraduationEggs(Long userId) {
        // 1. 유저 정보 및 현재 펫 상태 확인
        UserEntity user = userMapper.findById(userId);
        PetInfoEntity currentPet = petMapper.findByUserId(userId);

        // 현재 키우고 있는 펫이 없거나, 졸업 상태가 아니라면 알을 받을 수 없음
        // 단, '졸업 직후'에는 status가 GRADUATED로 바뀌어 있어 findByUserId(Active)가 null일 수 있으므로 로직 주의
        // 여기서는 "현재 Active 펫이 없어야 알 선택 가능" 정책을 따름
        if (currentPet != null) {
            throw new TutorooException("아직 육성 중인 펫이 있습니다. 펫이 졸업해야 새로운 알을 받을 수 있습니다.", ErrorCode.ALREADY_HAS_PET);
        }

        // 2. 멤버십에 따른 알 개수 및 후보군 조회
        MembershipTier tier = user.getEffectiveTier();
        int eggCount = tier.getGraduationEggCount();
        Set<PetType> allowedPets = tier.getAllowedPets();

        // 3. 랜덤으로 후보 선출
        List<PetType> allCandidates = new ArrayList<>(allowedPets);
        Collections.shuffle(allCandidates);
        List<PetDTO.PetSummary> selectedCandidates = allCandidates.stream()
                .limit(Math.min(eggCount, allCandidates.size()))
                .map(type -> new PetDTO.PetSummary(type.name(), type.getName(), type.getDescription()))
                .toList();

        return PetDTO.RandomEggResponse.builder()
                .candidates(selectedCandidates)
                .choiceCount(1) // 사용자는 제시된 알 중 1개를 선택 가능
                .build();
    }

    @Transactional
    public void hatchEgg(Long userId, String selectedPetType) {
        // 1. 이미 Active 펫이 있는지 확인 (중복 방지)
        if (petMapper.findByUserId(userId) != null) {
            throw new TutorooException(ErrorCode.ALREADY_HAS_PET);
        }

        // 2. 펫 타입 검증
        PetType type;
        try {
            type = PetType.valueOf(selectedPetType);
        } catch (IllegalArgumentException e) {
            throw new TutorooException(ErrorCode.INVALID_PET_TYPE);
        }

        // 3. 멤버십 권한 검증 (선택한 펫이 현재 등급에서 허용되는지)
        UserEntity user = userMapper.findById(userId);
        if (!user.getEffectiveTier().getAllowedPets().contains(type)) {
            throw new TutorooException(ErrorCode.MEMBERSHIP_PET_RESTRICTION);
        }

        // 4. 새로운 펫 생성 (초기화)
        PetInfoEntity newPet = PetInfoEntity.builder()
                .userId(userId)
                .petName(type.getName()) // 초기 이름은 종 이름으로 설정
                .petType(type.name())
                .stage(1) // 알 단계부터 시작
                .status("ACTIVE")
                .fullness(80)
                .intimacy(80)
                .exp(0)
                .cleanliness(100)
                .stress(0)
                .energy(100)
                .isSleeping(false)
                .createdAt(LocalDateTime.now())
                .lastFedAt(LocalDateTime.now())
                .lastPlayedAt(LocalDateTime.now())
                .lastCleanedAt(LocalDateTime.now())
                .lastSleptAt(LocalDateTime.now())
                .build();

        petMapper.createPet(newPet);
        log.info("🥚 새로운 알 부화 완료! User: {}, Pet: {}", userId, type);
    }


    // --- 내부 메서드 ---

    private void checkLevelUp(PetInfoEntity pet) {
        int required = petMapper.findRequiredExpForNextStage(pet.getStage());

        // 일반 성장 (1단계 -> 4단계)
        if (pet.getExp() >= required && pet.getStage() < 5) {
            pet.setStage(pet.getStage() + 1);
            pet.setExp(pet.getExp() - required);
            log.info("🎉 펫 진화! UserId: {}, NewStage: {}", pet.getUserId(), pet.getStage());
        }
        // 졸업 조건 달성 (5단계에서 경험치 충족)
        else if (pet.getStage() == 5 && pet.getExp() >= required) {
            pet.setStatus("GRADUATED");
            pet.setExp(pet.getExp() - required); // 경험치 정리
            log.info("🎓 펫 졸업 달성! UserId: {}, PetId: {}", pet.getUserId(), pet.getPetId());

            // 졸업 보상 지급 (예: 알 포인트 등) 로직을 여기에 추가 가능
        }
    }

    private void updatePetStats(PetInfoEntity pet) {
        LocalDateTime now = LocalDateTime.now();
        long hFed = Duration.between(pet.getLastFedAt(), now).toHours();
        if(hFed > 0) pet.setFullness(Math.max(0, pet.getFullness() - (int)hFed * FULLNESS_DECAY_PER_HOUR));

        long hPlay = Duration.between(pet.getLastPlayedAt(), now).toHours();
        if(hPlay > 0) pet.setIntimacy(Math.max(0, pet.getIntimacy() - (int)hPlay * INTIMACY_DECAY_PER_HOUR));
    }

    private PetDTO.PetStatusResponse mapToDTO(PetInfoEntity pet, int maxExp) {
        return PetDTO.PetStatusResponse.builder()
                .petId(pet.getPetId())
                .petName(pet.getPetName())
                .petType(pet.getPetType())
                .stage(pet.getStage())
                .fullness(pet.getFullness())
                .intimacy(pet.getIntimacy())
                .exp(pet.getExp())
                .maxExp(maxExp)
                .cleanliness(pet.getCleanliness())
                .stress(pet.getStress())
                .energy(pet.getEnergy())
                .isSleeping(pet.isSleeping())
                .status(pet.getStatus())
                .statusMessage(generateRandomMessage(pet))
                .build();
    }

    private String generateRandomMessage(PetInfoEntity pet) {
        if (pet.isSleeping()) return "Zzz... (세상 모르게 자고 있다)";
        if ("GRADUATED".equals(pet.getStatus())) return "당신 덕분에 훌륭하게 자랐어요! 이제 넓은 세상으로 떠날게요.";
        if (pet.getFullness() < 30) return "배가 고파요... 밥 주세요.";
        if (pet.getIntimacy() < 30) return "심심해요. 놀아주세요.";
        return "오늘도 기분 좋은 하루예요!";
    }
}