package com.tutoroo.service;

import com.tutoroo.dto.PetDTO;
import com.tutoroo.entity.*;
import com.tutoroo.exception.ErrorCode;
import com.tutoroo.exception.TutorooException;
import com.tutoroo.mapper.PetMapper;
import com.tutoroo.mapper.StudyMapper;
import com.tutoroo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetService {

    private final PetMapper petMapper;
    private final UserMapper userMapper;
    private final StudyMapper studyMapper;
    private final ChatClient.Builder chatClientBuilder;
    private final RedisTemplate<String, String> redisTemplate;

    private static final int FULLNESS_DECAY_PER_HOUR = 5;
    private static final int INTIMACY_DECAY_PER_HOUR = 3;
    private static final int COST_FEED = 10;
    private static final int MAX_STAGE = 5;

    // --- 1. 초기 입양 ---
    @Transactional(readOnly = true)
    public PetDTO.AdoptableListResponse getAdoptablePets(Long userId) {
        UserEntity user = userMapper.findById(userId);
        MembershipTier tier = user.getEffectiveTier();

        List<PetDTO.PetSummary> list = tier.getInitialSelectablePets().stream()
                .map(type -> new PetDTO.PetSummary(type.name(), type.getName(), type.getDescription()))
                .toList();

        return PetDTO.AdoptableListResponse.builder()
                .availablePets(list)
                .message(String.format("%s 등급 회원은 총 %d마리 중 선택 가능합니다.", tier.name(), list.size()))
                .build();
    }

    @Transactional
    public void adoptInitialPet(Long userId, String petTypeStr) {
        if (petMapper.findByUserId(userId) != null) {
            throw new TutorooException("이미 육성 중인 펫이 있습니다.", ErrorCode.INVALID_INPUT_VALUE);
        }
        UserEntity user = userMapper.findById(userId);
        PetType selectedType = PetType.valueOf(petTypeStr);
        if (!user.getEffectiveTier().getInitialSelectablePets().contains(selectedType)) {
            throw new TutorooException("현재 멤버십 등급에서는 선택할 수 없는 펫입니다.", ErrorCode.UNAUTHORIZED_ACCESS);
        }
        createPet(userId, selectedType);
    }

    // --- 2. 졸업 및 랜덤 알 ---
    @Transactional
    public PetDTO.RandomEggResponse getGraduationEggs(Long userId) {
        if (petMapper.findByUserId(userId) != null) {
            throw new TutorooException("먼저 현재 펫을 졸업시켜야 합니다.", ErrorCode.INVALID_INPUT_VALUE);
        }
        UserEntity user = userMapper.findById(userId);
        MembershipTier tier = user.getEffectiveTier();

        List<PetInfoEntity> myPets = petMapper.findAllByUserId(userId);
        Set<String> ownedTypes = myPets.stream().map(PetInfoEntity::getPetType).collect(Collectors.toSet());

        List<PetType> candidates = new ArrayList<>(Arrays.asList(PetType.values()));
        if (ownedTypes.size() < PetType.values().length) {
            candidates.removeIf(type -> ownedTypes.contains(type.name()));
        }

        Collections.shuffle(candidates);
        List<PetType> selectedCandidates = candidates.stream()
                .limit(tier.getEggChoiceCount())
                .toList();

        String key = "egg_candidates:" + userId;
        String candidatesStr = selectedCandidates.stream().map(Enum::name).collect(Collectors.joining(","));
        redisTemplate.opsForValue().set(key, candidatesStr, 10, TimeUnit.MINUTES);

        List<PetDTO.PetSummary> summaryList = selectedCandidates.stream()
                .map(type -> new PetDTO.PetSummary(type.name(), type.getName(), "알에서 무엇이 나올까요?"))
                .toList();

        return PetDTO.RandomEggResponse.builder()
                .candidates(summaryList)
                .choiceCount(1)
                .build();
    }

    @Transactional
    public void hatchEgg(Long userId, String selectedPetType) {
        String key = "egg_candidates:" + userId;
        String candidatesStr = redisTemplate.opsForValue().get(key);
        if (candidatesStr == null) throw new TutorooException("알 선택 시간이 만료되었습니다.", ErrorCode.INVALID_INPUT_VALUE);

        List<String> validCandidates = Arrays.asList(candidatesStr.split(","));
        if (!validCandidates.contains(selectedPetType)) throw new TutorooException("제공된 알 후보 목록에 없는 펫입니다.", ErrorCode.INVALID_INPUT_VALUE);

        createPet(userId, PetType.valueOf(selectedPetType));
        redisTemplate.delete(key);
    }

    // --- 3. 펫 상태 조회 및 디테일 ---
    @Transactional
    public PetDTO.PetStatusResponse getPetStatus(Long userId) {
        PetInfoEntity pet = petMapper.findByUserId(userId);
        if (pet == null) return null;

        if (checkRunaway(pet)) {
            return PetDTO.PetStatusResponse.builder()
                    .petName("초기화됨")
                    .statusMessage("관심 부족으로 펫이 가출했습니다... 알 상태로 돌아갑니다.")
                    .stage(1).petType(pet.getPetType()).build();
        }

        applyPassiveDecay(pet);
        petMapper.updatePet(pet);

        Integer nextExp = petMapper.findRequiredExpForNextStage(pet.getStage());
        return mapToDTO(pet, nextExp != null ? nextExp : 0);
    }

    @Transactional
    public PetDTO.PetStatusResponse interact(Long userId, String actionType) {
        PetInfoEntity pet = petMapper.findByUserId(userId);
        if (pet == null) throw new TutorooException(ErrorCode.PET_NOT_FOUND);
        UserEntity user = userMapper.findById(userId);

        applyPassiveDecay(pet);

        if (pet.isSleeping() && !"WAKE_UP".equals(actionType.toUpperCase())) {
            pet.setStress(Math.min(100, pet.getStress() + 20));
            pet.setIntimacy(Math.max(0, pet.getIntimacy() - 10));
            petMapper.updatePet(pet);
            throw new TutorooException("Zzz... 펫이 자고 있어요! 깨우면 스트레스를 받습니다.", ErrorCode.INVALID_INPUT_VALUE);
        }

        switch (actionType.toUpperCase()) {
            case "FEED" -> {
                if (user.getPointBalance() < COST_FEED) {
                    throw new TutorooException("보유 포인트가 부족합니다. (필요: " + COST_FEED + "P)", ErrorCode.INVALID_INPUT_VALUE);
                }
                userMapper.spendPoints(userId, COST_FEED);
                pet.setFullness(Math.min(100, pet.getFullness() + 20));
                pet.setLastFedAt(LocalDateTime.now());
                pet.setExp(pet.getExp() + 5);
                if (new Random().nextInt(100) < 30) pet.setCleanliness(Math.max(0, pet.getCleanliness() - 40));
            }
            case "PLAY" -> {
                if (pet.getEnergy() < 10) throw new TutorooException("펫이 너무 피곤해합니다. 좀 쉬게 해주세요.", ErrorCode.INVALID_INPUT_VALUE);
                pet.setIntimacy(Math.min(100, pet.getIntimacy() + 15));
                pet.setStress(Math.max(0, pet.getStress() - 10));
                pet.setEnergy(Math.max(0, pet.getEnergy() - 10));
                pet.setLastPlayedAt(LocalDateTime.now());
                pet.setExp(pet.getExp() + 10);
            }
            case "CLEAN" -> {
                pet.setCleanliness(100);
                pet.setStress(Math.max(0, pet.getStress() - 5));
                pet.setLastCleanedAt(LocalDateTime.now());
                pet.setExp(pet.getExp() + 5);
            }
            case "SLEEP" -> {
                pet.setSleeping(true);
                pet.setLastSleptAt(LocalDateTime.now());
            }
            case "WAKE_UP" -> pet.setSleeping(false);
        }

        checkGrowth(pet);
        petMapper.updatePet(pet);
        Integer nextExp = petMapper.findRequiredExpForNextStage(pet.getStage());
        return mapToDTO(pet, nextExp != null ? nextExp : 0);
    }

    @Transactional
    public void gainExp(Long userId, int amount) {
        PetInfoEntity pet = petMapper.findByUserId(userId);
        if (pet == null) return;
        applyPassiveDecay(pet);
        if (pet.getCleanliness() < 30) amount /= 2;
        pet.setExp(pet.getExp() + amount);
        checkGrowth(pet);
        petMapper.updatePet(pet);
    }

    public double getPointMultiplier(Long userId) {
        PetInfoEntity pet = petMapper.findByUserId(userId);
        if (pet == null) return 1.0;
        if (pet.getIntimacy() >= 70 && !pet.isSleeping() && pet.getCleanliness() >= 50) {
            Double skillEffect = petMapper.findSkillEffect(pet.getPetType(), "POINT_BOOST");
            return skillEffect != null ? skillEffect : 1.1;
        }
        return 1.0;
    }

    @Transactional
    public List<Long> processBatchRunaways() {
        List<PetInfoEntity> allPets = petMapper.findAllActivePets();
        List<Long> runawayUserIds = new ArrayList<>();
        for (PetInfoEntity pet : allPets) {
            if (pet.getStage() == 1) continue;
            long hours = Duration.between(pet.getLastPlayedAt(), LocalDateTime.now()).toHours();
            if (hours > 72 && pet.getIntimacy() == 0) {
                pet.setStage(1);
                pet.setPetName("새로운 알");
                pet.setFullness(80);
                pet.setIntimacy(50);
                pet.setExp(0);
                petMapper.updatePet(pet);
                runawayUserIds.add(pet.getUserId());
            }
        }
        return runawayUserIds;
    }

    @Transactional
    public void writeMidnightDiary(Long userId) {
        PetInfoEntity pet = petMapper.findByUserId(userId);
        if (pet == null) return;
        List<StudyLogEntity> logs = studyMapper.findLogsByUserIdAndDate(userId, LocalDate.now());
        String mood = pet.getIntimacy() > 60 ? "행복함" : "슬픔";
        String prompt = String.format("""
                너는 '%s' 캐릭터야. 이름은 '%s'.
                오늘 주인님이 공부를 %d번 했어. 네 현재 기분은 '%s'야.
                오늘 하루를 관찰한 3줄짜리 귀여운 그림일기를 써줘. 반말 사용.
                """, pet.getPetType(), pet.getPetName(), logs != null ? logs.size() : 0, mood);
        String content = chatClientBuilder.build().prompt().user(prompt).call().content();
        petMapper.saveDiary(PetDiaryEntity.builder()
                .petId(pet.getPetId())
                .date(LocalDate.now())
                .content(content)
                .mood(mood.equals("행복함") ? "HAPPY" : "SAD")
                .build());
    }

    private void createPet(Long userId, PetType type) {
        PetInfoEntity newPet = PetInfoEntity.builder()
                .userId(userId)
                .petName(type.getName())
                .petType(type.name())
                .stage(1)
                .status("ACTIVE")
                .fullness(100).intimacy(50).exp(0)
                .cleanliness(100).energy(100).stress(0)
                .build();
        petMapper.createPet(newPet);
    }

    private void checkGrowth(PetInfoEntity pet) {
        Integer requiredExp = petMapper.findRequiredExpForNextStage(pet.getStage());
        if (requiredExp != null && pet.getExp() >= requiredExp) {
            pet.setStage(pet.getStage() + 1);
            if (pet.getStage() >= MAX_STAGE) {
                pet.setStatus("GRADUATED");
                pet.setPetName("Master " + pet.getPetName());
            }
        }
    }

    private boolean checkRunaway(PetInfoEntity pet) {
        if (pet.getStage() == 1) return false;
        long hours = Duration.between(pet.getLastPlayedAt(), LocalDateTime.now()).toHours();
        if (hours > 72 && pet.getIntimacy() == 0) {
            pet.setStage(1);
            pet.setPetName("새로운 알");
            pet.setFullness(80);
            pet.setIntimacy(50);
            pet.setExp(0);
            petMapper.updatePet(pet);
            return true;
        }
        return false;
    }

    private void applyPassiveDecay(PetInfoEntity pet) {
        LocalDateTime now = LocalDateTime.now();
        if (pet.isSleeping()) {
            long hours = Duration.between(pet.getLastSleptAt(), now).toHours();
            if (hours > 0) pet.setEnergy(Math.min(100, pet.getEnergy() + (int)hours * 10));
            if (hours >= 8) pet.setSleeping(false);
            pet.setLastSleptAt(now);
            return;
        }
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
        if ("GRADUATED".equals(pet.getStatus())) return "당신 덕분에 훌륭하게 자랐어요!";
        if (pet.getStage() == 1) return "...(알이 꿈틀거린다)";
        if (pet.getFullness() < 30) return "배고파요... 밥 좀 주세요.";
        if (pet.getCleanliness() < 30) return "으... 냄새나요. 씻겨주세요.";
        return "주인님 반가워요! 공부하러 갈까요?";
    }
}