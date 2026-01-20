package com.tutoroo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutoroo.dto.RivalDTO;
import com.tutoroo.dto.StudyDTO;
import com.tutoroo.dto.UserDTO;
import com.tutoroo.entity.StudyLogEntity;
import com.tutoroo.entity.StudyPlanEntity;
import com.tutoroo.entity.UserEntity;
import com.tutoroo.exception.ErrorCode;
import com.tutoroo.exception.TutorooException;
import com.tutoroo.mapper.StudyMapper;
import com.tutoroo.mapper.UserMapper;
import com.tutoroo.util.FileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final StudyMapper studyMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileStore fileStore;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // --- [New] 0. 회원 상세 정보 조회 (수정 화면 초기 진입용) ---
    @Transactional(readOnly = true)
    public UserDTO.ProfileInfo getProfileInfo(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null) throw new TutorooException(ErrorCode.USER_NOT_FOUND);

        return UserDTO.ProfileInfo.builder()
                .username(user.getUsername())
                .name(user.getName())
                .age(user.getAge())
                .email(user.getEmail())
                .phone(user.getPhone())
                .profileImage(user.getProfileImage())
                .membershipTier(user.getEffectiveTier().name())
                .build();
    }

    // --- [1] 회원 정보 수정 (Before/After 반환) ---
    @Transactional
    public UserDTO.UpdateResponse updateUserInfo(String username, UserDTO.UpdateRequest request, MultipartFile image) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null) throw new TutorooException(ErrorCode.USER_NOT_FOUND);

        // 보안: 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new TutorooException(ErrorCode.INVALID_PASSWORD);
        }

        // 1. [Snapshot] 변경 전 정보 저장
        UserDTO.ProfileInfo beforeInfo = UserDTO.ProfileInfo.builder()
                .username(user.getUsername())
                .name(user.getName())
                .age(user.getAge())
                .email(user.getEmail())
                .phone(user.getPhone())
                .profileImage(user.getProfileImage())
                .membershipTier(user.getEffectiveTier().name())
                .build();

        // 2. 정보 업데이트 (화면 스펙 반영)
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.newPassword()));
        }
        if (request.name() != null && !request.name().isBlank()) user.setName(request.name());
        if (request.age() != null) user.setAge(request.age());
        if (request.email() != null && !request.email().isBlank()) user.setEmail(request.email());
        if (request.phone() != null && !request.phone().isBlank()) user.setPhone(request.phone());

        // 프로필 이미지 변경
        if (image != null && !image.isEmpty()) {
            try {
                String originalFilename = image.getOriginalFilename();
                String ext = (originalFilename != null && originalFilename.contains("."))
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".jpg";
                String imageUrl = fileStore.storeFile(image.getBytes(), ext);
                user.setProfileImage(imageUrl);
            } catch (Exception e) {
                log.error("프로필 이미지 수정 실패: {}", e.getMessage());
                throw new TutorooException(ErrorCode.FILE_UPLOAD_ERROR);
            }
        }

        // 3. DB 반영
        userMapper.update(user);
        deleteDashboardCache(username);

        // 4. [Snapshot] 변경 후 정보 생성
        UserDTO.ProfileInfo afterInfo = UserDTO.ProfileInfo.builder()
                .username(user.getUsername())
                .name(user.getName())
                .age(user.getAge())
                .email(user.getEmail())
                .phone(user.getPhone())
                .profileImage(user.getProfileImage())
                .membershipTier(user.getEffectiveTier().name())
                .build();

        return UserDTO.UpdateResponse.builder()
                .before(beforeInfo)
                .after(afterInfo)
                .message("회원 정보가 성공적으로 변경되었습니다.")
                .build();
    }

    @Transactional(readOnly = true)
    public RivalDTO.RivalComparisonResponse getRivalComparison(Long userId) {
        UserEntity me = userMapper.findById(userId);
        if (me == null) throw new TutorooException(ErrorCode.USER_NOT_FOUND);

        // 1. 라이벌이 없는 경우
        if (me.getRivalId() == null) {
            return RivalDTO.RivalComparisonResponse.builder()
                    .hasRival(false)
                    .myProfile(toRivalProfile(me))
                    .message("아직 라이벌이 없습니다. 매칭을 시작해보세요!")
                    .pointGap(0)
                    .build();
        }

        // 2. 라이벌 정보 조회
        UserEntity rival = userMapper.findById(me.getRivalId());
        // 라이벌이 탈퇴했을 경우 처리
        if (rival == null || !"ACTIVE".equals(rival.getStatus())) {
            return RivalDTO.RivalComparisonResponse.builder()
                    .hasRival(false)
                    .myProfile(toRivalProfile(me))
                    .message("라이벌이 떠났습니다. 새로운 라이벌을 찾아보세요.")
                    .pointGap(0)
                    .build();
        }

        // 3. 비교 로직
        int myScore = me.getTotalPoint();
        int rivalScore = rival.getTotalPoint();
        int gap = Math.abs(myScore - rivalScore);
        String msg;

        if (myScore > rivalScore) {
            msg = String.format("훌륭해요! 라이벌보다 %d점 앞서고 있습니다. 🏆", gap);
        } else if (myScore < rivalScore) {
            msg = String.format("분발하세요! 라이벌이 %d점 차이로 앞서갑니다. 🔥", gap);
        } else {
            msg = "막상막하! 라이벌과 점수가 같습니다. 긴장하세요!";
        }

        return RivalDTO.RivalComparisonResponse.builder()
                .hasRival(true)
                .myProfile(toRivalProfile(me))
                .rivalProfile(toRivalProfile(rival))
                .message(msg)
                .pointGap(gap)
                .build();
    }

    // DTO 변환 헬퍼
    private RivalDTO.RivalProfile toRivalProfile(UserEntity user) {
        return RivalDTO.RivalProfile.builder()
                .userId(user.getId())
                .name(user.getMaskedName()) // 이름 마스킹
                .profileImage(user.getProfileImage())
                .totalPoint(user.getTotalPoint())
                .rank(user.getDailyRank() != null ? user.getDailyRank() : 0) // 매일 갱신되는 랭킹 사용
                .level(user.getLevel())
                .tier(user.getEffectiveTier().name())
                .build();
    }

    // --- [2] 대시보드 조회 (StudyList 추가됨) ---
    @Transactional(readOnly = true)
    public UserDTO.DashboardDTO getAdvancedDashboard(String username) {
        String cacheKey = "dashboard:" + username;

        // 1. 캐시 조회
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                return objectMapper.readValue(cachedJson, UserDTO.DashboardDTO.class);
            }
        } catch (Exception e) {
            log.warn("대시보드 캐시 조회 실패: {}", e.getMessage());
        }

        UserEntity user = userMapper.findByUsername(username);
        if (user == null) throw new TutorooException(ErrorCode.USER_NOT_FOUND);

        // 2. 학습 플랜 조회 및 DTO 매핑 [UPDATE: 프론트엔드 연동용]
        List<StudyPlanEntity> plans = studyMapper.findActivePlansByUserId(user.getId());

        // [New] StudyList 매핑 로직 추가
        List<StudyDTO.StudySimpleInfo> studyList = plans.stream()
                .map(plan -> StudyDTO.StudySimpleInfo.builder()
                        .id(plan.getId())
                        .name(plan.getGoal())
                        .tutor(plan.getCustomTutorName() != null ? plan.getCustomTutorName() : plan.getPersona())
                        .build())
                .collect(Collectors.toList());

        StudyPlanEntity currentPlan = plans.isEmpty() ? null : plans.get(0);

        String currentGoal = (currentPlan != null) ? currentPlan.getGoal() : "목표를 설정해주세요";
        double progressRate = (currentPlan != null) ? currentPlan.getProgressRate() : 0.0;

        List<StudyLogEntity> logs = (currentPlan != null)
                ? studyMapper.findLogsByPlanId(currentPlan.getId())
                : new ArrayList<>();

        List<Integer> weeklyScores = logs.stream()
                .skip(Math.max(0, logs.size() - 7))
                .map(StudyLogEntity::getTestScore)
                .collect(Collectors.toList());

        String aiAnalysis = "아직 충분한 학습 데이터가 없습니다. 꾸준히 학습해보세요!";
        String aiSuggestion = "오늘의 학습을 시작해보는 건 어때요?";

        if (!logs.isEmpty()) {
            StudyLogEntity lastLog = logs.get(logs.size() - 1);
            if (lastLog.getAiFeedback() != null) aiAnalysis = lastLog.getAiFeedback();
            aiSuggestion = "지난번 점수는 " + lastLog.getTestScore() + "점이었네요. 오늘은 더 잘할 수 있어요!";
        }

        // 3. DTO 빌드 (studyList 추가)
        UserDTO.DashboardDTO dashboardDTO = UserDTO.DashboardDTO.builder()
                .name(user.getName())
                .currentGoal(currentGoal)
                .progressRate(progressRate)
                .currentPoint(user.getTotalPoint())
                .rank(user.getDailyRank() != null ? user.getDailyRank() : 0)
                .aiAnalysisReport(aiAnalysis)
                .aiSuggestion(aiSuggestion)
                .weeklyScores(weeklyScores)
                .studyList(studyList) // [New] 추가됨
                .build();

        // 4. 캐시 저장
        try {
            String json = objectMapper.writeValueAsString(dashboardDTO);
            redisTemplate.opsForValue().set(cacheKey, json, 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("대시보드 캐시 저장 실패: {}", e.getMessage());
        }

        return dashboardDTO;
    }

    // --- [3] 라이벌 매칭 ---
    @Transactional
    public String matchRival(Long userId) {
        UserEntity me = userMapper.findById(userId);
        if (me.getRivalId() != null) return "이미 라이벌이 등록되어 있습니다.";

        UserEntity rival = userMapper.findPotentialRival(me.getId(), me.getTotalPoint());
        if (rival == null) return "현재 매칭 가능한 라이벌이 없습니다.";

        me.setRivalId(rival.getId());
        userMapper.update(me);

        deleteDashboardCache(me.getUsername());
        return "매칭 성공! 라이벌: " + rival.getMaskedName();
    }

    // --- [4] 회원 탈퇴 ---
    @Transactional
    public void withdrawUser(Long userId, UserDTO.WithdrawRequest request) {
        UserEntity user = userMapper.findById(userId);
        if (user == null) throw new TutorooException(ErrorCode.USER_NOT_FOUND);

        if (user.getProvider() == null) {
            if (!passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new TutorooException("비밀번호가 일치하지 않습니다.", ErrorCode.INVALID_PASSWORD);
            }
        }

        user.setStatus("WITHDRAWN");
        user.setWithdrawalReason(request.reason());
        user.setDeletedAt(LocalDateTime.now());

        userMapper.update(user);
        deleteDashboardCache(user.getUsername());
        redisTemplate.delete("RT:" + user.getUsername());
    }

    // --- [New] 5. 비밀번호 검증 (마이페이지 진입 전) ---
    @Transactional(readOnly = true)
    public void verifyPassword(Long userId, String rawPassword) {
        UserEntity user = userMapper.findById(userId);
        if (user == null) throw new TutorooException(ErrorCode.USER_NOT_FOUND);

        if (user.getProvider() != null) {
            return; // 소셜 로그인은 통과
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new TutorooException("비밀번호가 일치하지 않습니다.", ErrorCode.INVALID_PASSWORD);
        }
    }

    private void deleteDashboardCache(String username) {
        try {
            redisTemplate.delete("dashboard:" + username);
        } catch (Exception e) {
            log.warn("캐시 삭제 실패: {}", e.getMessage());
        }
    }
}