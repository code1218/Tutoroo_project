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
    // [New] 대화 기억을 위한 Redis 템플릿 추가
    private final RedisTemplate<String, String> redisTemplate;

    // --- [1] 수업 시작 ---
    @Transactional
    public TutorDTO.ClassStartResponse startClass(Long userId, TutorDTO.ClassStartRequest request) {
        StudyPlanEntity plan = studyMapper.findById(request.planId());
        if (plan == null) throw new TutorooException(ErrorCode.STUDY_PLAN_NOT_FOUND);

        // 1. AI 멘트 생성
        String prompt = String.format("""
                상황: %d일차 수업 시작.
                학생 기분: %s
                선생님 스타일: %s
                
                오늘 배울 주제(roadmapJson 참고)를 흥미롭게 소개하고, 학생의 기분을 반영해서 오프닝 멘트를 해줘.
                형식: "주제 | 멘트"
                """, request.dayCount(), request.dailyMood(), request.personaName());

        String response = chatClientBuilder.build().prompt().user(prompt).call().content();
        String[] parts = response.split("\\|");
        String topic = parts.length > 0 ? parts[0].trim() : "오늘의 학습";
        String aiMessage = parts.length > 1 ? parts[1].trim() : response;

        // 2. TTS 생성 (URL 반환)
        String audioUrl = generateTtsAudio(aiMessage, request.personaName());

        // 3. 배경음악 및 이미지 (예시 URL)
        String bgmUrl = "/audio/bgm_calm.mp3";
        String imageUrl = "/images/classroom_default.png";

        return new TutorDTO.ClassStartResponse(
                topic, aiMessage, audioUrl, imageUrl, bgmUrl,
                10, 5 // 경험치, 스트릭 (임시 값)
        );
    }

    // --- [2] 데일리 테스트 생성 ---
    @Transactional(readOnly = true)
    public TutorDTO.DailyTestResponse generateTest(Long userId, Long planId, int dayCount) {
        // 실제로는 로그나 커리큘럼을 보고 문제를 만듦
        String question = "Java의 Garbage Collection이 발생하는 영역은?";
        String voiceUrl = generateTtsAudio(question, "TIGER"); // 기본 목소리

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
        String feedbackPrompt = "문제: Java GC 영역. 답안: " + textAnswer + ". 채점하고 피드백해줘. 형식: 점수:XX | 피드백";
        String aiResponse = chatClientBuilder.build().prompt().user(feedbackPrompt).call().content();

        int score = parseScore(aiResponse); // "점수: 80" 파싱 메서드 (하단 참조)
        String feedbackMsg = aiResponse.contains("|") ?
                aiResponse.split("\\|")[1].trim() : aiResponse;
        boolean isPassed = score >= 60;

        // 2. 학습 로그 저장
        StudyLogEntity logEntity = StudyLogEntity.builder()
                .planId(planId)
                .dayCount(1) // 임시
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

        // 4. TTS 생성 (URL)
        String audioUrl = generateTtsAudio(feedbackMsg, plan.getPersona());

        return new TutorDTO.TestFeedbackResponse(
                score,
                feedbackMsg,
                "오늘의 학습 요약 완료",
                audioUrl,
                "/images/feedback_good.png",
                "내일도 화이팅!",
                isPassed
        );
    }

    // --- [4] 커리큘럼 조정 채팅 (AI 튜터 대화 - Context 적용) ---
    // [Fix] 기존 단순 호출을 Redis 기반의 대화 기억 로직으로 전면 교체
    @Transactional
    public TutorDTO.FeedbackChatResponse adjustCurriculum(Long userId, Long planId, String message) {
        StudyPlanEntity plan = studyMapper.findById(planId);
        if (plan == null) throw new TutorooException(ErrorCode.STUDY_PLAN_NOT_FOUND);

        // 1. Redis에서 대화 내역 불러오기 (최근 10턴 = 20개 메시지)
        String historyKey = "chat:history:" + planId;
        List<Message> messages = new ArrayList<>();

        // 2. 시스템 프롬프트 설정 (페르소나 주입)
        // DB에서 페르소나 정보를 가져오거나, 없으면 기본값 사용
        String personaKey = "TEACHER_" + (plan.getPersona() != null ? plan.getPersona() : "TIGER");
        String personaDesc = commonMapper.findPromptContentByKey(personaKey);
        if (personaDesc == null) personaDesc = "당신은 친절한 AI 선생님입니다.";

        messages.add(new SystemMessage(personaDesc + "\n(이전 대화 내용을 기억하고 자연스럽게 이어가세요.)"));

        // 3. 과거 대화 내역 추가
        try {
            List<String> historyJson = redisTemplate.opsForList().range(historyKey, 0, -1);
            if (historyJson != null) {
                for (String json : historyJson) {
                    Map<String, String> msgMap = objectMapper.readValue(json, Map.class);
                    String role = msgMap.get("role");
                    String content = msgMap.get("content");

                    if ("user".equals(role)) {
                        messages.add(new UserMessage(content));
                    } else if ("assistant".equals(role)) {
                        messages.add(new AssistantMessage(content));
                    }
                }
            }
        } catch (Exception e) {
            log.error("대화 내역 로딩 실패: {}", e.getMessage());
            // 에러 나도 현재 대화는 진행
        }

        // 4. 현재 유저 메시지 추가
        messages.add(new UserMessage(message));

        // 5. AI 호출 (Context 포함)
        Prompt prompt = new Prompt(messages);
        String aiResponse = chatClientBuilder.build().prompt(prompt).call().content();

        // 6. Redis에 대화 내역 저장 (User + Assistant)
        try {
            String userJson = objectMapper.writeValueAsString(Map.of("role", "user", "content", message));
            String aiJson = objectMapper.writeValueAsString(Map.of("role", "assistant", "content", aiResponse));

            redisTemplate.opsForList().rightPush(historyKey, userJson);
            redisTemplate.opsForList().rightPush(historyKey, aiJson);

            // 메모리 관리: 최근 20개 메시지(10턴)만 유지
            if (redisTemplate.opsForList().size(historyKey) > 20) {
                redisTemplate.opsForList().trim(historyKey, -20, -1);
            }
            // TTL 설정 (24시간 동안 대화 없으면 삭제)
            redisTemplate.expire(historyKey, 24, TimeUnit.HOURS);

        } catch (JsonProcessingException e) {
            log.error("대화 내역 저장 실패: {}", e.getMessage());
        }

        // 7. TTS 생성 및 응답
        String audioUrl = generateTtsAudio(aiResponse, plan.getPersona());

        return new TutorDTO.FeedbackChatResponse(aiResponse, audioUrl);
    }

    // --- [5] 음성 인식 (STT) ---
    public String convertSpeechToText(MultipartFile audio) {
        try {
            // 임시 파일로 저장 후 처리
            File tempFile = File.createTempFile("stt_", ".mp3");
            audio.transferTo(tempFile);

            // [수정 완료] AudioTranscriptionPrompt는 이제 Core 패키지에서 가져옵니다.
            String text = transcriptionModel.call(new AudioTranscriptionPrompt(new FileSystemResource(tempFile))).getResult().getOutput();

            // 임시 파일 삭제
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

    // --- [7] 시험 생성 (주간/월간) ---
    @Transactional(readOnly = true)
    public TutorDTO.ExamGenerateResponse generateExam(Long userId, Long planId, int startDay, int endDay) {
        // logs 기반으로 AI가 문제 생성하는 로직 (생략)

        List<TutorDTO.ExamGenerateResponse.ExamQuestion> questions = new ArrayList<>();
        questions.add(new TutorDTO.ExamGenerateResponse.ExamQuestion(1, "생성된 문제 1", List.of("보기1", "보기2")));

        return new TutorDTO.ExamGenerateResponse("주간 평가", questions);
    }

    // --- [8] 시험 제출 ---
    @Transactional
    public TutorDTO.ExamResultResponse submitExam(Long userId, TutorDTO.ExamSubmitRequest request) {
        // 채점 로직
        int totalScore = 85;
        boolean isPassed = totalScore >= 70;

        return new TutorDTO.ExamResultResponse(
                totalScore,
                1, // 레벨업
                "잘했어요! 다음 단계로 넘어갑시다.",
                List.of("1번 문제 오답 노트"),
                isPassed
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

    // --- [Private] TTS 생성 및 파일 저장 (최적화) ---
    private String generateTtsAudio(String text, String personaName) {
        try {
            // 1. 캐시 확인 (DB)
            String textHash = generateHash(text + personaName);
            TtsCacheEntity cached = commonMapper.findTtsCacheByHash(textHash);
            if (cached != null) {
                return cached.getAudioPath(); // 이미 저장된 파일 URL 반환
            }

            // 2. 목소리 선택 (Spring AI 1.0.0-M6 대응: String -> Enum 변환)
            // 기본값: ALLOY
            OpenAiAudioApi.SpeechRequest.Voice voice = OpenAiAudioApi.SpeechRequest.Voice.ALLOY;
            if (personaName != null) {
                if (personaName.contains("호랑이") || personaName.contains("TIGER")) {
                    voice = OpenAiAudioApi.SpeechRequest.Voice.ONYX; // 중저음, 남성적
                } else if (personaName.contains("토끼") || personaName.contains("RABBIT")) {
                    voice = OpenAiAudioApi.SpeechRequest.Voice.NOVA; // 활기참, 여성적
                } else if (personaName.contains("거북이") || personaName.contains("TURTLE")) {
                    voice = OpenAiAudioApi.SpeechRequest.Voice.ALLOY; // 차분함, 중성적
                } else if (personaName.contains("캥거루") || personaName.contains("KANGAROO")) {
                    voice = OpenAiAudioApi.SpeechRequest.Voice.SHIMMER; // 맑음, 여성적
                } else if (personaName.contains("용") || personaName.contains("DRAGON")) {
                    voice = OpenAiAudioApi.SpeechRequest.Voice.ECHO; // 부드러움, 남성적
                }
            }

            // 3. OpenAI TTS 호출 (옵션 빌더에 Enum 전달)
            SpeechResponse res = speechModel.call(
                    new SpeechPrompt(text, OpenAiAudioSpeechOptions.builder()
                            .model("tts-1")
                            .voice(voice)
                            .build())
            );
            byte[] audioData = res.getResult().getOutput();

            // 4. [최적화] FileStore 저장
            String fileUrl = fileStore.storeFile(audioData, ".mp3");

            // 5. 캐시 저장
            commonMapper.saveTtsCache(TtsCacheEntity.builder()
                    .textHash(textHash)
                    .audioPath(fileUrl)
                    .build());

            return fileUrl;

        } catch (Exception e) {
            log.error("TTS Generation Error: {}", e.getMessage());
            return null; // 프론트엔드에서 예외 처리 가능하도록 null 반환
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