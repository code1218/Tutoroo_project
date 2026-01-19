package com.tutoroo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutoroo.dto.AssessmentDTO;
import com.tutoroo.dto.StudyDTO;
import com.tutoroo.dto.TutorDTO;
import com.tutoroo.entity.MembershipTier;
import com.tutoroo.entity.StudyLogEntity;
import com.tutoroo.entity.StudyPlanEntity;
import com.tutoroo.entity.UserEntity;
import com.tutoroo.exception.ErrorCode;
import com.tutoroo.exception.TutorooException;
import com.tutoroo.mapper.StudyMapper;
import com.tutoroo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyService {

    private final UserMapper userMapper;
    private final StudyMapper studyMapper;
    private final TutorService tutorService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // --- [1] 현재 학습 플랜 상세 조회 (대시보드용) ---
    @Transactional(readOnly = true)
    public StudyDTO.PlanDetailResponse getCurrentPlanDetail(Long userId) {
        List<StudyPlanEntity> plans = studyMapper.findActivePlansByUserId(userId);
        if (plans.isEmpty()) {
            return null;
        }
        StudyPlanEntity currentPlan = plans.get(0);

        // JSON -> RoadmapData 객체 변환
        AssessmentDTO.RoadmapData roadmapData = null;
        try {
            if (currentPlan.getRoadmapJson() != null) {
                roadmapData = objectMapper.readValue(currentPlan.getRoadmapJson(), AssessmentDTO.RoadmapData.class);
            }
        } catch (JsonProcessingException e) {
            log.error("로드맵 JSON 파싱 실패: {}", e.getMessage());
        }

        return StudyDTO.PlanDetailResponse.builder()
                .planId(currentPlan.getId())
                .goal(currentPlan.getGoal())
                .persona(currentPlan.getPersona())
                .customTutorName(currentPlan.getCustomTutorName())
                .progressRate(currentPlan.getProgressRate())
                .roadmap(roadmapData)
                .daysRemaining(currentPlan.getDaysRemaining())
                .build();
    }

    // --- [2] 현재 학습 상태 요약 (메인 화면용) ---
    @Transactional(readOnly = true)
    public StudyDTO.StudyStatusResponse getCurrentStudyStatus(Long userId) {
        List<StudyPlanEntity> plans = studyMapper.findActivePlansByUserId(userId);
        if (plans.isEmpty()) return null;

        StudyPlanEntity currentPlan = plans.get(0);
        List<StudyLogEntity> todayLogs = studyMapper.findLogsByUserIdAndDate(userId, LocalDate.now());
        boolean isResting = !todayLogs.isEmpty();

        return StudyDTO.StudyStatusResponse.builder()
                .planId(currentPlan.getId())
                .goal(currentPlan.getGoal())
                .personaName(currentPlan.getPersona())
                .currentDay(todayLogs.size() + 1)
                .progressRate(currentPlan.getProgressRate())
                .isResting(isResting)
                .lastTopic(isResting ? todayLogs.get(0).getContentSummary() : "새로운 학습 대기중")
                .build();
    }

    // --- [3] 간편 학습 로그 저장 ---
    @Transactional
    public void saveSimpleLog(Long userId, StudyDTO.StudyLogRequest request) {
        StudyPlanEntity plan = studyMapper.findById(request.planId());
        if (plan == null) throw new TutorooException(ErrorCode.STUDY_PLAN_NOT_FOUND);

        StudyLogEntity log = StudyLogEntity.builder()
                .planId(plan.getId())
                .studyDate(LocalDateTime.now())
                .dayCount(request.dayCount())
                .contentSummary(request.contentSummary())
                .testScore(request.score())
                .isCompleted(request.isCompleted())
                .pointChange(request.score() > 0 ? request.score() : 10)
                .build();
        studyMapper.saveLog(log);

        userMapper.earnPoints(userId, log.getPointChange());

        // 단순 진도율 업데이트 로직
        int newProgress = (int) ((double) request.dayCount() / 30.0 * 100);
        if (newProgress > 100) newProgress = 100;
        updateProgress(plan.getId(), newProgress);
    }

    // --- [4] 채팅 핸들링 ---
    public StudyDTO.ChatResponse handleSimpleChat(Long userId, String message) {
        List<StudyPlanEntity> plans = studyMapper.findActivePlansByUserId(userId);
        if (plans.isEmpty()) throw new TutorooException(ErrorCode.STUDY_PLAN_NOT_FOUND);

        TutorDTO.FeedbackChatResponse tutorResponse = tutorService.adjustCurriculum(userId, plans.get(0).getId(), message);
        return StudyDTO.ChatResponse.builder()
                .aiMessage(tutorResponse.aiResponse())
                .audioUrl(tutorResponse.audioUrl())
                .build();
    }

    // --- [5] 활성 학습 목록 조회 ---
    @Transactional(readOnly = true)
    public List<StudyDTO.StudySimpleInfo> getActiveStudyList(Long userId) {
        return studyMapper.findActivePlansByUserId(userId).stream()
                .map(plan -> StudyDTO.StudySimpleInfo.builder()
                        .id(plan.getId())
                        .name(plan.getGoal())
                        .tutor(plan.getCustomTutorName() != null ? plan.getCustomTutorName() : plan.getPersona())
                        .build())
                .collect(Collectors.toList());
    }

    // --- [New] 학습 플랜 삭제 (Controller 호환용 추가됨) ---
    @Transactional
    public void deleteStudyPlan(Long userId, Long planId) {
        StudyPlanEntity plan = studyMapper.findById(planId);
        if (plan == null) {
            throw new TutorooException("존재하지 않는 학습 플랜입니다.", ErrorCode.STUDY_PLAN_NOT_FOUND);
        }

        // 본인 소유 확인
        if (!plan.getUserId().equals(userId)) {
            throw new TutorooException("본인의 학습 플랜만 삭제할 수 있습니다.", ErrorCode.UNAUTHORIZED_ACCESS);
        }

        studyMapper.deletePlan(planId);
        log.info("학습 플랜 삭제 완료: userId={}, planId={}", userId, planId);
    }

    // --- [6] 기타 기능들 ---
    @Transactional(readOnly = true)
    public boolean canCreateNewGoal(Long userId) {
        try {
            validatePlanCreationLimit(userId);
            return true;
        } catch (Exception e) { return false; }
    }

    @Transactional
    public void updateProgress(Long planId, Integer rate) {
        StudyPlanEntity plan = studyMapper.findById(planId);
        if(plan != null) {
            plan.setProgressRate(rate);
            studyMapper.updateProgress(plan);
        }
    }

    @Transactional(readOnly = true)
    public void validatePlanCreationLimit(Long userId) {
        UserEntity user = userMapper.findById(userId);
        MembershipTier tier = user.getEffectiveTier();
        if (studyMapper.findActivePlansByUserId(userId).size() >= tier.getMaxActiveGoals()) {
            throw new TutorooException(ErrorCode.MULTIPLE_PLANS_REQUIRED_PAYMENT);
        }
    }

    // Redis 세션 관리
    public void saveSessionState(Long planId, String stateJson) {
        String key = "session:" + planId;
        redisTemplate.opsForValue().set(key, stateJson, 24, TimeUnit.HOURS);
    }

    public String getSessionState(Long planId) {
        return redisTemplate.opsForValue().get("session:" + planId);
    }

    public void clearSessionState(Long planId) {
        redisTemplate.delete("session:" + planId);
    }

    @Transactional(readOnly = true)
    public StudyDTO.CalendarResponse getMonthlyCalendar(Long userId, int year, int month) {
        List<StudyLogEntity> logs = studyMapper.findLogsByUserIdAndMonth(userId, year, month);
        Map<Integer, List<StudyLogEntity>> logsByDay = logs.stream()
                .collect(Collectors.groupingBy(log -> log.getStudyDate().getDayOfMonth()));

        List<StudyDTO.DailyLog> dailyLogs = new ArrayList<>();
        int studyDayCount = 0;

        for (Map.Entry<Integer, List<StudyLogEntity>> entry : logsByDay.entrySet()) {
            int day = entry.getKey();
            List<StudyLogEntity> dayLogs = entry.getValue();
            boolean isDone = dayLogs.stream().anyMatch(StudyLogEntity::getIsCompleted);
            if (isDone) studyDayCount++;

            dailyLogs.add(StudyDTO.DailyLog.builder()
                    .day(day)
                    .isDone(isDone)
                    .score(dayLogs.stream().mapToInt(l -> l.getTestScore() != null ? l.getTestScore() : 0).max().orElse(0))
                    .topic(dayLogs.isEmpty() ? "" : dayLogs.get(0).getContentSummary())
                    .build());
        }

        return StudyDTO.CalendarResponse.builder()
                .year(year).month(month).totalStudyDays(studyDayCount).logs(dailyLogs)
                .build();
    }
}