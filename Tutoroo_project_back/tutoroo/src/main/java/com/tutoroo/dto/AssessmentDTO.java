package com.tutoroo.dto;

import lombok.Builder;
import java.util.List;
import java.util.Map;

public class AssessmentDTO {

    // --- [1] 상담 및 진단 관련 ---
    @Builder
    public record StudyStartRequest(
            String goal, String deadline, String availableTime, String teacherType
    ) {}

    @Builder
    public record ConsultRequest(
            StudyStartRequest studyInfo, List<Message> history, String lastUserMessage
    ) {}

    @Builder
    public record ConsultResponse(
            String aiMessage, String audioUrl, boolean isFinished
    ) {}

    @Builder
    public record AssessmentSubmitRequest(
            StudyStartRequest studyInfo, List<Message> history
    ) {}

    // [대시보드/상담결과용] 복합 응답
    @Builder
    public record AssessmentResultResponse(
            Long planId,
            String analyzedLevel,
            String analysisReport,
            RoadmapOverview overview,
            String message
    ) {}

    // --- [2] 로드맵 데이터 구조 (DB 저장용) ---
    @Builder
    public record RoadmapData(
            String summary,
            List<Chapter> tableOfContents,
            Map<String, List<DailyDetail>> detailedCurriculum,
            List<String> examSchedule
    ) {}

    public record Chapter(String week, String title, String description) {}

    @Builder
    public record RoadmapOverview(String summary, List<Chapter> chapters) {}

    public record DailyDetail(String day, String topic, String method, String material) {}

    // --- [3] 기존 StudyController 호환용 DTO ---

    // [Fix] 인자 3개로 확정 (currentLevel 포함)
    @Builder
    public record RoadmapRequest(
            String goal,
            String teacherType,
            String currentLevel // 없을 경우 null 허용
    ) {}

    // [Fix] 기존 컨트롤러 반환 타입 호환
    @Builder
    public record RoadmapResponse(
            String summary,
            Map<String, String> weeklyCurriculum, // 단순화된 커리큘럼
            List<String> examSchedule
    ) {}

    // --- 공통 ---
    @Builder public record Message(String role, String content) {}

    // --- 레벨 테스트 (기존 유지) ---
    @Builder public record LevelTestRequest(String subject) {}
    @Builder public record LevelTestResponse(String testId, String subject, List<TestQuestion> questions) {
        public record TestQuestion(int questionNo, String question, List<String> options) {}
    }
    @Builder public record TestSubmitRequest(String testId, String subject, List<Integer> answers) {}
    @Builder public record AssessmentResult(String level, int score, String analysis, String recommendedPath) {}
}