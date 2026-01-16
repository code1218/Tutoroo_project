package com.tutoroo.service;

import com.tutoroo.dto.AssessmentDTO;
import com.tutoroo.entity.StudyPlanEntity;
import com.tutoroo.mapper.StudyMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final OpenAiChatModel chatModel;
    private final OpenAiAudioSpeechModel speechModel;
    private final StudyMapper studyMapper;
    private final ObjectMapper objectMapper;

    // 허용된 선생님 목록 (5마리 고정)
    private static final List<String> ALLOWED_TEACHERS = List.of(
            "TIGER", "RABBIT", "TURTLE", "KANGAROO", "EASTERN_DRAGON"
    );

    private static final int MAX_QUESTIONS = 30;

    @Transactional(readOnly = true)
    public AssessmentDTO.ConsultResponse consult(AssessmentDTO.ConsultRequest request) {
        int currentCount = (request.history() != null ? request.history().size() : 0) / 2 + 1;

        if (request.isStopRequested() || currentCount > MAX_QUESTIONS) {
            return AssessmentDTO.ConsultResponse.builder()
                    .question("수준 파악이 완료되었습니다. 로드맵을 생성하시겠습니까?")
                    .audioBase64(null)
                    .questionCount(currentCount)
                    .isFinished(true)
                    .build();
        }

        String nextQuestion = "좋아요. 가장 좋아하는 과목은 무엇인가요?";
        return AssessmentDTO.ConsultResponse.builder()
                .question(nextQuestion)
                .audioBase64(generateTtsAudio(nextQuestion))
                .questionCount(currentCount)
                .isFinished(false)
                .build();
    }

    @Transactional
    public AssessmentDTO.RoadmapResponse createStudentRoadmap(Long userId, AssessmentDTO.RoadmapRequest request) {
        // 실제로는 AI에게 로드맵 생성을 요청해야 함. 여기서는 더미 응답 사용.
        String dummyJson = "{ \"summary\": \"로드맵\", \"weeklyCurriculum\": {}, \"examSchedule\": [] }";
        return saveRoadmap(userId, request, dummyJson);
    }

    @Transactional
    protected AssessmentDTO.RoadmapResponse saveRoadmap(Long userId, AssessmentDTO.RoadmapRequest request, String json) {
        try {
            if (json.startsWith("```")) {
                json = json.replaceAll("^```json", "").replaceAll("```$", "").trim();
            }

            AssessmentDTO.RoadmapResponse response = objectMapper.readValue(json, AssessmentDTO.RoadmapResponse.class);

            // [검증] 사용자가 선택한 선생님이 허용된 5마리인지 확인
            String selectedTeacher = request.teacherType();
            if (selectedTeacher == null || !ALLOWED_TEACHERS.contains(selectedTeacher.toUpperCase())) {
                log.warn("유효하지 않은 선생님 타입: {}. 기본값(TIGER)으로 설정합니다.", selectedTeacher);
                selectedTeacher = "TIGER";
            }

            StudyPlanEntity plan = StudyPlanEntity.builder()
                    .userId(userId)
                    .goal(request.goal())
                    .persona(selectedTeacher.toUpperCase()) // 검증된 문자열 저장
                    .roadmapJson(json)
                    .progressRate(0.0)
                    .status("PROCEEDING")
                    .isPaid(false)
                    .build();

            studyMapper.savePlan(plan);
            return response;
        } catch (Exception e) {
            log.error("로드맵 파싱/저장 실패: {}", e.getMessage());
            throw new RuntimeException("로드맵 생성 중 오류가 발생했습니다.");
        }
    }

    private String generateTtsAudio(String text) {
        try {
            SpeechResponse response = speechModel.call(new SpeechPrompt(text));
            return Base64.getEncoder().encodeToString(response.getResult().getOutput());
        } catch (Exception e) {
            log.error("TTS 생성 오류: {}", e.getMessage());
            return null;
        }
    }
}