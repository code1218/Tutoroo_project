package com.tutoroo.service;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TutorService {

    private final ChatClient chatClient;
    private final OpenAiAudioSpeechModel speechModel;
    private final OpenAiImageModel imageModel;
    private final OpenAiAudioTranscriptionModel transcriptionModel;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private final StudyMapper studyMapper;
    private final UserMapper userMapper;
    private final CommonMapper commonMapper;
    private final FileStore fileStore;
    private final ApplicationEventPublisher eventPublisher;
    private final PetService petService;

    public TutorService(ChatClient.Builder chatClientBuilder,
                        OpenAiAudioSpeechModel speechModel,
                        OpenAiImageModel imageModel,
                        OpenAiAudioTranscriptionModel transcriptionModel,
                        RedisTemplate<String, String> redisTemplate,
                        ObjectMapper objectMapper,
                        StudyMapper studyMapper,
                        UserMapper userMapper,
                        CommonMapper commonMapper,
                        FileStore fileStore,
                        ApplicationEventPublisher eventPublisher,
                        PetService petService) {
        this.chatClient = chatClientBuilder
                .defaultSystem("당신은 Tutoroo의 AI 튜터입니다. 학생에게 친절하고 명확하게 설명해주세요.")
                .build();
        this.speechModel = speechModel;
        this.imageModel = imageModel;
        this.transcriptionModel = transcriptionModel;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.studyMapper = studyMapper;
        this.userMapper = userMapper;
        this.commonMapper = commonMapper;
        this.fileStore = fileStore;
        this.eventPublisher = eventPublisher;
        this.petService = petService;
    }

    // --- [핵심 기능 1: 수업 시작] ---
    @Transactional
    public TutorDTO.ClassStartResponse startClass(Long userId, TutorDTO.ClassStartRequest request) {
        UserEntity user = userMapper.findById(userId);
        StudyPlanEntity plan = studyMapper.findPlanById(request.planId());
        if (plan == null) throw new TutorooException(ErrorCode.STUDY_PLAN_NOT_FOUND);

        String customName = (plan.getCustomTutorName() != null) ? plan.getCustomTutorName() : "AI 선생님";

        // [DB 연동] 선생님 페르소나 가져오기
        String promptKey = "TEACHER_" + plan.getPersona();
        String systemInstruction = commonMapper.findPromptContentByKey(promptKey);

        if (systemInstruction == null) {
            log.warn("선생님 프롬프트 없음: {}. 기본값 사용.", promptKey);
            systemInstruction = "너는 친절하고 유능한 AI 과외 선생님이야.";
        }

        String memoryContext = buildMemoryContext(plan.getId());
        String dailyMood = (request.dailyMood() != null) ? request.dailyMood() : "평범함";

        String promptText = String.format("""
                [시스템 지시]: %s
                [학생 정보]: 이름은 '%s'라고 불러줘. 현재 기분은 '%s'야.
                [이전 학습 기억]:
                %s
                
                [오늘의 목표]: %d일차 수업. 주제는 '%s'.
                위 설정을 바탕으로 수업을 시작하는 오프닝 멘트를 해줘.
                """,
                systemInstruction, customName, dailyMood, memoryContext, request.dayCount(), plan.getGoal());

        String aiMessage = chatClient.prompt().user(promptText).call().content();

        String audioUrl = generateTieredTtsWithCache(aiMessage, user.getEffectiveTier());
        String imageUrl = generateImage("Illustration for " + plan.getGoal() + " class, education style, vector art");

        return new TutorDTO.ClassStartResponse(request.dayCount() + "일차: " + plan.getGoal(), aiMessage, audioUrl, imageUrl, null, 25, 5);
    }

    // --- [핵심 기능 2: 적응형 과제 생성] ---
    @Transactional(readOnly = true)
    public TutorDTO.DailyTestResponse generateTest(Long userId, Long planId, int dayCount) {
        StudyPlanEntity plan = studyMapper.findPlanById(planId);
        List<StudyLogEntity> logs = studyMapper.findLogsByPlanId(planId);
        double avgScore = logs.stream().mapToInt(StudyLogEntity::getTestScore).average().orElse(70.0);

        String difficulty = (avgScore >= 90) ? "HARD" : (avgScore <= 60 ? "EASY" : "NORMAL");

        // [DB 연동] 선생님 페르소나 적용
        String personaPrompt = commonMapper.findPromptContentByKey("TEACHER_" + plan.getPersona());
        if (personaPrompt == null) personaPrompt = "친절한 선생님";

        // [DB 연동] 테스트 생성 프롬프트 템플릿 사용
        String template = getPromptTemplate("TEST_ADAPTIVE");
        String promptText = String.format(template, personaPrompt, (int)avgScore, difficulty, plan.getGoal(), dayCount);

        String question = chatClient.prompt().user(promptText).call().content();
        String imageUrl = generateImage("Visual aid for quiz: " + plan.getGoal() + ", minimalist style");

        return new TutorDTO.DailyTestResponse("MISSION [" + difficulty + "]", question, imageUrl, null, 300);
    }

    // --- [핵심 기능 3: 시험 제출 및 Vision 피드백] ---
    @Transactional
    public TutorDTO.TestFeedbackResponse submitTest(Long userId, Long planId, String textAnswer, MultipartFile image) {
        UserEntity user = userMapper.findById(userId);
        StudyPlanEntity plan = studyMapper.findPlanById(planId);

        String aiResponse;
        if (image != null && !image.isEmpty()) {
            aiResponse = analyzeUniversalImage(image, plan.getGoal());
        } else {
            String template = getPromptTemplate("TEST_FEEDBACK");
            aiResponse = chatClient.prompt()
                    .user(String.format(template, plan.getGoal(), textAnswer))
                    .call().content();
        }

        int score = parseScore(aiResponse);
        String summary = aiResponse.contains("점수") ? aiResponse.substring(aiResponse.indexOf("점수")) : "학습 내용 정리";

        // 1. 기본 포인트 계산 (태도 점수 포함)
        int attitudePoint = evaluateAttitude(textAnswer);
        int basePointChange = ((score >= 80) ? 100 : (score <= 40 ? 10 : 50)) + attitudePoint;
        int finalPointChange = basePointChange;

        // 2. [펫 버프 적용]
        if (basePointChange > 0) {
            double multiplier = petService.getPointMultiplier(userId);
            if (multiplier > 1.0) {
                finalPointChange = (int) (basePointChange * multiplier);
                log.info("펫 버프 적용: {} -> {}", basePointChange, finalPointChange);
            }
        }

        if (finalPointChange > 0) userMapper.updateUserPointByPlan(planId, finalPointChange);

        boolean isPassed = score >= 60;
        int currentDay = getCurrentDayCount(planId);

        studyMapper.saveLog(StudyLogEntity.builder()
                .planId(planId)
                .dayCount(currentDay)
                .testScore(score)
                .aiFeedback(aiResponse)
                .dailySummary(summary)
                .pointChange(finalPointChange)
                .isCompleted(isPassed)
                .build());

        updateStreak(user);

        // [펫 경험치] 60점 이상 시 이벤트 발생
        if (isPassed) {
            try {
                eventPublisher.publishEvent(new StudyCompletedEvent(userId, score));
            } catch (Exception e) {
                log.warn("펫 경험치 이벤트 실패: {}", e.getMessage());
            }
        }

        if (currentDay == 1 && plan.getCustomTutorName() == null) {
            plan.setCustomTutorName("AI 선생님");
            studyMapper.updatePlan(plan);
        }

        String audioUrl = generateTieredTtsWithCache(aiResponse.substring(0, Math.min(aiResponse.length(), 200)), user.getEffectiveTier());
        String expImage = (score < 100) ? generateImage("Explanation diagram for: " + plan.getGoal()) : null;

        return new TutorDTO.TestFeedbackResponse(score, aiResponse, summary, audioUrl, expImage, null, isPassed);
    }

    // --- [핵심 기능 4: 대화형 피드백] ---
    @Transactional(readOnly = true)
    public TutorDTO.FeedbackChatResponse adjustCurriculum(Long userId, Long planId, String message) {
        UserEntity user = userMapper.findById(userId);
        StudyPlanEntity plan = studyMapper.findPlanById(planId);
        String conversationKey = "chat:" + planId + ":" + userId;

        List<Message> history = loadChatHistory(conversationKey);

        // 선생님 페르소나 적용
        String systemInstruction = commonMapper.findPromptContentByKey("TEACHER_" + plan.getPersona());
        if (systemInstruction == null) systemInstruction = "친절한 튜터";

        String aiResponse = chatClient.prompt()
                .system(systemInstruction)
                .messages(history)
                .user(message)
                .call()
                .content();

        saveChatMessage(conversationKey, new UserMessage(message));
        saveChatMessage(conversationKey, new AssistantMessage(aiResponse));

        String audioUrl = generateTieredTtsWithCache(aiResponse, user.getEffectiveTier());

        return new TutorDTO.FeedbackChatResponse(aiResponse, audioUrl);
    }

    // --- [핵심 기능 5: 시험 문제 생성] ---
    @Transactional(readOnly = true)
    public TutorDTO.ExamGenerateResponse generateExam(Long userId, Long planId, int startDay, int endDay) {
        StudyPlanEntity plan = studyMapper.findPlanById(planId);
        List<StudyLogEntity> logs = studyMapper.findLogsBetweenDays(planId, startDay, endDay);

        if (logs.isEmpty()) throw new TutorooException("시험 범위에 해당하는 학습 기록이 없습니다.", ErrorCode.INVALID_INPUT_VALUE);

        String combinedSummary = logs.stream()
                .map(log -> log.getDayCount() + "일차: " + log.getDailySummary())
                .collect(Collectors.joining("\n"));

        String promptText = String.format("""
                분야: %s
                학습 자료: %s
                지시: 위 내용을 바탕으로 5개의 객관식 문제를 출제하세요.
                반드시 JSON 형식으로 반환하세요.
                """, plan.getGoal(), combinedSummary);

        return chatClient.prompt()
                .user(promptText)
                .call()
                .entity(TutorDTO.ExamGenerateResponse.class);
    }

    // --- [핵심 기능 6: 시험 결과 제출] ---
    @Transactional
    public TutorDTO.ExamResultResponse submitExam(Long userId, TutorDTO.ExamSubmitRequest request) {
        String prompt = "학생 답안: " + request.answers() + ". 5문제 채점 및 해설.";
        String aiFeedback = chatClient.prompt().user(prompt).call().content();

        int score = parseScore(aiFeedback);

        // 1. 기본 포인트
        int basePoint = score * 5;
        int finalPoint = basePoint;

        // 2. [펫 버프 적용]
        if (basePoint > 0) {
            double multiplier = petService.getPointMultiplier(userId);
            if (multiplier > 1.0) {
                finalPoint = (int) (basePoint * multiplier);
            }
        }

        userMapper.updateUserPointByPlan(request.planId(), finalPoint);

        // [펫 경험치] 60점 이상
        if (score >= 60) {
            try {
                eventPublisher.publishEvent(new StudyCompletedEvent(userId, score));
            } catch (Exception e) {
                log.warn("펫 경험치 지급 실패: {}", e.getMessage());
            }
        }

        return new TutorDTO.ExamResultResponse(score, (finalPoint > 200 ? 5 : 1), aiFeedback, new ArrayList<>(), score >= 60);
    }

    // --- [기타 기능] ---
    @Transactional
    public void renameCustomTutor(Long planId, String newName) {
        StudyPlanEntity plan = studyMapper.findPlanById(planId);
        if (plan != null) {
            plan.setCustomTutorName(newName);
            studyMapper.updatePlan(plan);
        }
    }

    @Transactional(readOnly = true)
    public String convertSpeechToText(MultipartFile audioFile) {
        try {
            return transcriptionModel.call(new AudioTranscriptionPrompt(new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() { return "speech.mp3"; }
            })).getResult().getOutput();
        } catch (Exception e) {
            log.error("STT Error: {}", e.getMessage());
            throw new TutorooException("STT Error", ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void saveStudentFeedback(TutorDTO.TutorReviewRequest request) {
        studyMapper.updateStudentFeedback(request.planId(), request.dayCount(), request.feedback());
    }

    @Transactional
    protected void updateStreak(UserEntity user) {
        LocalDate today = LocalDate.now();
        LocalDate last = user.getLastStudyDate();
        if (last == null) user.setCurrentStreak(1);
        else if (last.equals(today.minusDays(1))) user.setCurrentStreak(user.getCurrentStreak() + 1);
        else if (last.isBefore(today.minusDays(1))) user.setCurrentStreak(1);
        user.setLastStudyDate(today);
        userMapper.update(user);
    }

    // --- [Private Helpers] ---

    private int getCurrentDayCount(Long planId) {
        List<StudyLogEntity> logs = studyMapper.findLogsByPlanId(planId);
        if (logs == null || logs.isEmpty()) return 1;
        return logs.stream().mapToInt(StudyLogEntity::getDayCount).max().orElse(0) + 1;
    }

    private List<Message> loadChatHistory(String key) {
        List<Message> messages = new ArrayList<>();
        try {
            List<String> jsonHistory = redisTemplate.opsForList().range(key, 0, -1);
            if (jsonHistory != null) {
                for (String json : jsonHistory) {
                    ChatMessageDto dto = objectMapper.readValue(json, ChatMessageDto.class);
                    if ("USER".equals(dto.role)) messages.add(new UserMessage(dto.content));
                    else messages.add(new AssistantMessage(dto.content));
                }
            }
        } catch (Exception e) {
            log.error("채팅 기록 로드 실패", e);
        }
        return messages;
    }

    private void saveChatMessage(String key, Message message) {
        try {
            String role;
            String content = message.getText();

            if (message instanceof UserMessage) {
                role = "USER";
            } else if (message instanceof AssistantMessage) {
                role = "ASSISTANT";
            } else {
                return;
            }

            String json = objectMapper.writeValueAsString(new ChatMessageDto(role, content));

            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.expire(key, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("채팅 기록 저장 실패", e);
        }
    }

    private record ChatMessageDto(String role, String content) {}

    private String analyzeUniversalImage(MultipartFile imageFile, String goal) {
        try {
            String template = getPromptTemplate("VISION_FEEDBACK");
            if (template.startsWith("Prompt:")) template = "주제: %s. 이 이미지를 분석해서 피드백해줘.";

            String promptText = String.format(template, goal);
            byte[] imageBytes = imageFile.getBytes();

            return chatClient.prompt()
                    .user(u -> u.text(promptText).media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(imageBytes)))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Vision Analysis Error: {}", e.getMessage());
            return "이미지 분석 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    private String buildMemoryContext(Long planId) {
        List<StudyLogEntity> recentLogs = studyMapper.findLogsByPlanId(planId);
        String context = recentLogs.stream()
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .limit(5)
                .map(log -> String.format("- %d일차(%d점): %s", log.getDayCount(), log.getTestScore(), log.getStudentFeedback()))
                .collect(Collectors.joining("\n"));
        return context.isEmpty() ? "첫 만남입니다." : context;
    }

    private String getPromptTemplate(String k) {
        String c = commonMapper.findPromptContentByKey(k);
        return c != null ? c : "Prompt: " + k;
    }

    private String generateImage(String p) {
        try {
            return imageModel.call(new ImagePrompt(p, OpenAiImageOptions.builder().withModel("dall-e-3").withHeight(1024).withWidth(1024).build())).getResult().getOutput().getUrl();
        } catch (Exception e) { return null; }
    }

    private String generateTieredTtsWithCache(String text, MembershipTier tier) {
        if (text == null || text.isEmpty()) return null;

        String voiceName = (tier.getTtsVoice() != null) ? tier.getTtsVoice() : "alloy";
        String hashKey = generateHash(text + ":" + voiceName);

        TtsCacheEntity cached = commonMapper.findTtsCacheByHash(hashKey);
        if (cached != null) {
            return cached.getAudioPath();
        }

        byte[] audioBytes = callOpenAiTts(text, tier);
        if (audioBytes == null) return null;

        String audioUrl = fileStore.storeFile(audioBytes, ".mp3");

        commonMapper.saveTtsCache(TtsCacheEntity.builder()
                .textHash(hashKey)
                .audioPath(audioUrl)
                .build());

        return audioUrl;
    }

    private byte[] callOpenAiTts(String text, MembershipTier tier) {
        try {
            OpenAiAudioApi.SpeechRequest.Voice voice = OpenAiAudioApi.SpeechRequest.Voice.ALLOY;
            try {
                if (tier.getTtsVoice() != null) voice = OpenAiAudioApi.SpeechRequest.Voice.valueOf(tier.getTtsVoice().toUpperCase());
            } catch (Exception e) {}

            SpeechResponse res = speechModel.call(
                    new SpeechPrompt(text, OpenAiAudioSpeechOptions.builder()
                            .model("tts-1")
                            .voice(voice)
                            .build())
            );
            return res.getResult().getOutput();
        } catch (Exception e) {
            log.error("TTS Generation Error: {}", e.getMessage());
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

    private int evaluateAttitude(String t) {
        try {
            String res = chatClient.prompt().user("답안: \"" + t + "\". 성실성과 태도를 0~10점 사이의 숫자로만 평가해.").call().content();
            return Integer.parseInt(res.replaceAll("[^0-9]", ""));
        } catch (Exception e) { return 0; }
    }
}