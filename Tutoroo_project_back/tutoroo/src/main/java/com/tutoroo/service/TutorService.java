package com.tutoroo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutoroo.dto.TutorDTO;
import com.tutoroo.entity.*;
import com.tutoroo.event.StudyCompletedEvent;
import com.tutoroo.exception.ErrorCode;
import com.tutoroo.exception.TutorooException;
import com.tutoroo.mapper.CommonMapper;
import com.tutoroo.mapper.StudyMapper;
import com.tutoroo.mapper.UserMapper;
import com.tutoroo.util.FileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TutorService {

    private final StudyMapper studyMapper;
    private final UserMapper userMapper;
    private final CommonMapper commonMapper;
    private final ChatClient.Builder chatClientBuilder;
    private final OpenAiAudioSpeechModel speechModel;
    private final OpenAiAudioTranscriptionModel transcriptionModel;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final FileStore fileStore;
    private final RedisTemplate<String, String> redisTemplate;

    // --- [1] 수업 시작 (Step 16: 하이브리드 페르소나 적용) ---
    @Transactional
    public TutorDTO.ClassStartResponse startClass(Long userId, TutorDTO.ClassStartRequest request) {
        StudyPlanEntity plan = studyMapper.findById(request.planId());
        if (plan == null) throw new TutorooException(ErrorCode.STUDY_PLAN_NOT_FOUND);

        // 1. 기본 시스템 프롬프트 가져오기 (DB Prompts 테이블)
        // 예: TEACHER_TIGER -> "너는 호랑이 선생님이야..."
        String basePersonaKey = "TEACHER_" + request.personaName();
        String baseSystemContent = commonMapper.findPromptContentByKey(basePersonaKey);
        if (baseSystemContent == null) baseSystemContent = "너는 열정적인 AI 과외 선생님이야.";

        // 2. [핵심 로직] 페르소나 믹싱 (Custom Name + Selected Style)
        String finalSystemPrompt = baseSystemContent;
        String customName = plan.getCustomTutorName();

        if (StringUtils.hasText(customName)) {
            // 커스텀 이름이 있다면 "본캐(Custom)" + "부캐(Selected)" 믹스
            finalSystemPrompt = String.format("""
                    [System Roleplay Instruction]
                    1. 너의 진짜 정체(본캐)는 '%s'라는 이름의 나만의 전담 튜터야.
                    2. 하지만 오늘 수업에서는 '%s' 스타일(부캐)로 연기를 해야 해.
                    3. 지시사항:
                       - '%s'의 기본 프롬프트 설정: "%s"
                       - 위 설정을 따르되, 호칭은 '%s'라고 스스로를 소개해.
                       - 본래의 따뜻함과 오늘의 연기 톤을 자연스럽게 섞어서 말해줘.
                    """,
                    customName,
                    request.personaName(),
                    request.personaName(),
                    baseSystemContent,
                    customName
            );
        }

        // 3. AI 오프닝 멘트 생성 요청
        String userPrompt = String.format("""
                상황: %d일차 수업 시작.
                학생 기분: %s
                오늘 배울 주제(roadmapJson 참고)를 흥미롭게 소개하고, 위 페르소나 설정에 맞춰 오프닝 멘트를 해줘.
                형식: "주제 | 멘트" (멘트는 2문장 이내)
                """, request.dayCount(), request.dailyMood());

        String response = chatClientBuilder.build()
                .prompt(new Prompt(List.of(
                        new SystemMessage(finalSystemPrompt),
                        new UserMessage(userPrompt)
                )))
                .call()
                .content();

        // 4. 응답 파싱
        String[] parts = response.split("\\|");
        String topic = parts.length > 0 ? parts[0].trim() : "오늘의 학습";
        String aiMessage = parts.length > 1 ? parts[1].trim() : response;

        // 5. TTS 생성 (목소리는 선택한 스타일의 목소리를 따라감)
        String audioUrl = generateTtsAudio(aiMessage, request.personaName());

        // 6. 리소스 매핑
        String imageUrl = "/images/tutors/" + request.personaName().toLowerCase() + ".png";
        String bgmUrl = "/audio/bgm/calm.mp3"; // 테마별 BGM 변경 가능

        return new TutorDTO.ClassStartResponse(
                topic, aiMessage, audioUrl, imageUrl, bgmUrl,
                10, 5 // 획득 경험치, 스트릭 (임시)
        );
    }

    // --- [2] 데일리 테스트 생성 ---
    @Transactional(readOnly = true)
    public TutorDTO.DailyTestResponse generateTest(Long userId, Long planId, int dayCount) {
        // 실제 구현 시: StudyLogEntity나 RoadmapJSON을 파싱하여 문제 생성
        // 여기서는 Mock 데이터 유지 (기존 코드 존중)
        String question = "Java의 Garbage Collection이 주로 발생하는 메모리 영역은?";
        String voiceUrl = generateTtsAudio(question, "TIGER");

        return new TutorDTO.DailyTestResponse(
                "QUIZ",
                question,
                "/images/quiz_bg.png",
                voiceUrl,
                30
        );
    }

    // --- [3] 테스트 제출 및 피드백 ---
    @Transactional
    public TutorDTO.TestFeedbackResponse submitTest(Long userId, Long planId, String textAnswer, MultipartFile image) {
        StudyPlanEntity plan = studyMapper.findById(planId);

        // 1. AI 채점 로직
        String feedbackPrompt = String.format(
                "문제: Java GC 영역. 답안: %s. 채점하고 피드백해줘. 형식: 점수:XX | 피드백(한 문장)",
                textAnswer
        );

        String aiResponse = chatClientBuilder.build().prompt().user(feedbackPrompt).call().content();

        int score = parseScore(aiResponse);
        String feedbackMsg = aiResponse.contains("|") ?
                aiResponse.split("\\|")[1].trim() : aiResponse;
        boolean isPassed = score >= 60;

        // 2. 학습 로그 저장
        StudyLogEntity logEntity = StudyLogEntity.builder()
                .planId(planId)
                .dayCount(1) // 실제로는 request.dayCount() 사용 필요
                .testScore(score)
                .aiFeedback(feedbackMsg)
                .isCompleted(isPassed)
                .pointChange(isPassed ? 100 : 10)
                .build();
        studyMapper.saveLog(logEntity);

        // 3. 이벤트 발행 (포인트/펫 경험치 지급)
        if (isPassed) {
            eventPublisher.publishEvent(new StudyCompletedEvent(userId, score));
        }

        // 4. TTS 생성
        String audioUrl = generateTtsAudio(feedbackMsg, plan.getPersona());

        return new TutorDTO.TestFeedbackResponse(
                score,
                feedbackMsg,
                "오늘의 학습 요약",
                audioUrl,
                "/images/feedback_good.png",
                "내일도 화이팅!",
                isPassed
        );
    }

    // --- [4] 커리큘럼 조정 채팅 (Context & Persona Mix 적용) ---
    @Transactional
    public TutorDTO.FeedbackChatResponse adjustCurriculum(Long userId, Long planId, String message) {
        StudyPlanEntity plan = studyMapper.findById(planId);
        if (plan == null) throw new TutorooException(ErrorCode.STUDY_PLAN_NOT_FOUND);

        String historyKey = "chat:history:" + planId;
        List<Message> messages = new ArrayList<>();

        // 1. 시스템 프롬프트 구성 (startClass와 동일한 믹스 로직 적용)
        String personaName = plan.getPersona() != null ? plan.getPersona() : "TIGER";
        String baseSystemContent = commonMapper.findPromptContentByKey("TEACHER_" + personaName);
        if (baseSystemContent == null) baseSystemContent = "친절한 AI 선생님입니다.";

        String finalSystemPrompt = baseSystemContent;
        String customName = plan.getCustomTutorName();

        if (StringUtils.hasText(customName)) {
            finalSystemPrompt = String.format("""
                [Identity Override]
                Name: %s
                Style: %s
                Instruction: You are %s but acting in the style of %s. 
                Keep the conversation flowing naturally based on previous context.
                """, customName, personaName, customName, personaName);
        }

        messages.add(new SystemMessage(finalSystemPrompt));

        // 2. Redis 대화 내역 로드
        try {
            List<String> historyJson = redisTemplate.opsForList().range(historyKey, 0, -1);
            if (historyJson != null) {
                for (String json : historyJson) {
                    Map<String, String> msgMap = objectMapper.readValue(json, Map.class);
                    String role = msgMap.get("role");
                    String content = msgMap.get("content");
                    if ("user".equals(role)) messages.add(new UserMessage(content));
                    else if ("assistant".equals(role)) messages.add(new AssistantMessage(content));
                }
            }
        } catch (Exception e) {
            log.error("History Load Error", e);
        }

        // 3. 현재 메시지 추가 및 AI 호출
        messages.add(new UserMessage(message));
        Prompt prompt = new Prompt(messages);
        String aiResponse = chatClientBuilder.build().prompt(prompt).call().content();

        // 4. Redis 저장 (TTL 24시간)
        try {
            String userJson = objectMapper.writeValueAsString(Map.of("role", "user", "content", message));
            String aiJson = objectMapper.writeValueAsString(Map.of("role", "assistant", "content", aiResponse));
            redisTemplate.opsForList().rightPush(historyKey, userJson);
            redisTemplate.opsForList().rightPush(historyKey, aiJson);

            if (redisTemplate.opsForList().size(historyKey) > 20) {
                redisTemplate.opsForList().trim(historyKey, -20, -1);
            }
            redisTemplate.expire(historyKey, 24, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("History Save Error", e);
        }

        // 5. TTS
        String audioUrl = generateTtsAudio(aiResponse, personaName);

        return new TutorDTO.FeedbackChatResponse(aiResponse, audioUrl);
    }

    // --- [5] 음성 인식 (STT) ---
    public String convertSpeechToText(MultipartFile audio) {
        try {
            File tempFile = File.createTempFile("stt_", ".mp3");
            audio.transferTo(tempFile);

            String text = transcriptionModel.call(new AudioTranscriptionPrompt(new FileSystemResource(tempFile))).getResult().getOutput();

            tempFile.delete();
            return text;
        } catch (Exception e) {
            log.error("STT Error: {}", e.getMessage());
            throw new TutorooException(ErrorCode.STT_PROCESSING_ERROR);
        }
    }

    // --- [6] 학생 피드백 저장 ---
    @Transactional
    public void saveStudentFeedback(TutorDTO.TutorReviewRequest request) {
        studyMapper.updateStudentFeedback(request.planId(), request.dayCount(), request.feedback());
    }

    // --- [7] 시험 생성 ---
    @Transactional(readOnly = true)
    public TutorDTO.ExamGenerateResponse generateExam(Long userId, Long planId, int startDay, int endDay) {
        List<TutorDTO.ExamGenerateResponse.ExamQuestion> questions = new ArrayList<>();
        questions.add(new TutorDTO.ExamGenerateResponse.ExamQuestion(1, "Java의 특징이 아닌 것은?", List.of("OOP", "Platform Independent", "Pointers", "Multi-threaded")));
        return new TutorDTO.ExamGenerateResponse("주간 평가", questions);
    }

    // --- [8] 시험 제출 ---
    @Transactional
    public TutorDTO.ExamResultResponse submitExam(Long userId, TutorDTO.ExamSubmitRequest request) {
        int totalScore = 90;
        return new TutorDTO.ExamResultResponse(
                totalScore, 1, "훌륭해요! 만점에 가까운 점수입니다.", List.of(), true
        );
    }

    // --- [9] 커스텀 튜터 이름 변경 ---
    @Transactional
    public void renameCustomTutor(Long planId, String newName) {
        StudyPlanEntity plan = studyMapper.findById(planId);
        if (plan != null) {
            plan.setCustomTutorName(newName);
            studyMapper.updatePlan(plan);
        }
    }

    // --- [Private] TTS 생성 및 파일 저장 ---
    private String generateTtsAudio(String text, String personaName) {
        try {
            // 캐시 확인
            String textHash = generateHash(text + (personaName != null ? personaName : "DEFAULT"));
            TtsCacheEntity cached = commonMapper.findTtsCacheByHash(textHash);
            if (cached != null) return cached.getAudioPath();

            // 목소리 매핑
            OpenAiAudioApi.SpeechRequest.Voice voice = OpenAiAudioApi.SpeechRequest.Voice.ALLOY;
            if (personaName != null) {
                String pUpper = personaName.toUpperCase();
                if (pUpper.contains("TIGER") || pUpper.contains("호랑이")) voice = OpenAiAudioApi.SpeechRequest.Voice.ONYX;
                else if (pUpper.contains("RABBIT") || pUpper.contains("토끼")) voice = OpenAiAudioApi.SpeechRequest.Voice.NOVA;
                else if (pUpper.contains("KANGAROO") || pUpper.contains("캥거루")) voice = OpenAiAudioApi.SpeechRequest.Voice.SHIMMER;
                else if (pUpper.contains("DRAGON") || pUpper.contains("용")) voice = OpenAiAudioApi.SpeechRequest.Voice.ECHO;
                else if (pUpper.contains("TURTLE") || pUpper.contains("거북이")) voice = OpenAiAudioApi.SpeechRequest.Voice.ALLOY;
            }

            // OpenAI TTS 호출
            SpeechResponse res = speechModel.call(
                    new SpeechPrompt(text, OpenAiAudioSpeechOptions.builder()
                            .model("tts-1")
                            .voice(voice)
                            .build())
            );
            byte[] audioData = res.getResult().getOutput();

            // 저장 및 캐싱
            String fileUrl = fileStore.storeFile(audioData, ".mp3");
            commonMapper.saveTtsCache(TtsCacheEntity.builder().textHash(textHash).audioPath(fileUrl).build());

            return fileUrl;
        } catch (Exception e) {
            log.error("TTS Fail", e);
            return null;
        }
    }

    private String generateHash(String i) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(i.getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder();
            for (byte b : h) s.append(String.format("%02x", b));
            return s.toString();
        } catch (Exception e) { return String.valueOf(i.hashCode()); }
    }

    private int parseScore(String t) {
        try {
            Matcher m = Pattern.compile("(점수|Score)\\s*:\\s*(\\d{1,3})").matcher(t);
            if (m.find()) return Integer.parseInt(m.group(2));
        } catch (Exception e) {}
        return 50;
    }
}