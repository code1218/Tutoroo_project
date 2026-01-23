import { create } from "zustand";
import { studyApi } from "../apis/studys/studysApi";

export const SESSION_MODES = {
  CLASS: { label: "수업", defaultTime: 50 * 60, hasTimer: true },
  BREAK: { label: "쉬는 시간", defaultTime: 10 * 60, hasTimer: true },   
  TEST: { label: "테스트", defaultTime: 0, hasTimer: false }, 
  GRADING: { label: "채점 중", defaultTime: 0, hasTimer: false },
  FEEDBACK: { label: "피드백", defaultTime: 0, hasTimer: false },
  REVIEW: { label: "복습", defaultTime: 0, hasTimer: false },
};

const useStudyStore = create((set, get) => ({
  studyDay: 1,      
  planId: null,     
  isLoading: false,
  selectedTutorId: "tiger",
  
  messages: [],
  isChatLoading: false,
  isSpeakerOn: false, 

  currentMode: "CLASS",
  timeLeft: SESSION_MODES.CLASS.defaultTime,
  isTimerRunning: false,

  loadUserStatus: async (specificPlanId = null) => {
    set({ isLoading: true });
    try {
      const targetPlanId = specificPlanId || get().planId;
      
      const data = await studyApi.getStudyStatus(targetPlanId);
      
      if (!data) {
          set({ isLoading: false });
          return;
      }

      const tutorId = data.personaName ? data.personaName.toLowerCase() : "tiger";

      set({ 
        studyDay: data.currentDay || 1, 
        planId: data.planId, 
        selectedTutorId: tutorId 
      }); 
    } catch (error) {
      console.error("로드 실패:", error);
      set({ studyDay: 1 });
    } finally {
      set({ isLoading: false });
    }
  },

  initializeStudySession: async () => {
    set({ isLoading: true, isChatLoading: true });
    try {
        const currentPlanId = get().planId;
        
        await get().loadUserStatus(currentPlanId);

        // 로드 후 갱신된 정보 가져오기
        const { planId, studyDay, selectedTutorId, messages } = get();

        // 이미 메시지가 있거나 플랜이 없으면 종료
        if (!planId || messages.length > 0) {
            set({ isLoading: false, isChatLoading: false });
            return;
        }

        // 수업 시작 요청 (오프닝 멘트)
        const classRes = await studyApi.startClass({
            planId: planId, 
            dayCount: studyDay,
            personaName: selectedTutorId.toUpperCase(), 
            dailyMood: "HAPPY" 
        });

        set({
            messages: [{
                type: 'AI',
                content: classRes.aiMessage,
                audioUrl: classRes.audioUrl
            }],
            currentMode: "CLASS",
            timeLeft: SESSION_MODES.CLASS.defaultTime,
            isTimerRunning: true,
            isSpeakerOn: false 
        });

    } catch (error) {
        console.error("수업 초기화 실패:", error);
        set({ messages: [{ type: 'AI', content: "수업을 불러오는 데 실패했습니다." }] });
    } finally {
        set({ isLoading: false, isChatLoading: false });
    }
  },

  // 3. 수동 수업 시작 (튜터 선택 페이지용)
  startClassSession: async (tutorInfo, navigate) => {
    set({ isLoading: true });
    const { planId, studyDay } = get();

    if (!planId) {
        alert("학습 정보를 찾을 수 없습니다.");
        await get().loadUserStatus();
        set({ isLoading: false });
        return;
    }

    try {
      const res = await studyApi.startClass({
        planId: planId, 
        dayCount: studyDay,
        personaName: tutorInfo.id.toUpperCase(), 
        dailyMood: "HAPPY" 
      });

      set({ 
        selectedTutorId: tutorInfo.id,
        messages: [{
          type: 'AI',
          content: res.aiMessage,
          audioUrl: res.audioUrl
        }],
        currentMode: "CLASS",
        timeLeft: SESSION_MODES.CLASS.defaultTime,
        isTimerRunning: true
      });
      navigate("/study");

    } catch (error) {
      console.error("수업 시작 실패:", error);
      alert("오류가 발생했습니다.");
    } finally {
      set({ isLoading: false });
    }
  },

  sendMessage: async (text) => {
    set((state) => ({
      messages: [...state.messages, { type: 'USER', content: text }],
      isChatLoading: true
    }));

    try {
      const res = await studyApi.sendChatMessage(text);
      set((state) => ({
        messages: [...state.messages, { 
          type: 'AI', 
          content: res.aiMessage,
          audioUrl: res.audioUrl
        }],
        isChatLoading: false
      }));
    } catch (error) {
      set((state) => ({
        messages: [...state.messages, { type: 'AI', content: "오류가 발생했습니다." }],
        isChatLoading: false
      }));
    }
  },

  setSessionMode: (modeKey, customTime = null) => {
    const config = SESSION_MODES[modeKey];
    set({ 
      currentMode: modeKey, 
      timeLeft: customTime !== null ? customTime : config.defaultTime,
      isTimerRunning: config.hasTimer 
    });
  },

  updateTimeLeft: (newTime) => set({ timeLeft: newTime }),
  toggleSpeaker: () => set((state) => ({ isSpeakerOn: !state.isSpeakerOn })),

  tick: () => {
    const { timeLeft, currentMode, isTimerRunning } = get();
    if (!isTimerRunning) return;
    if (timeLeft > 0) {
      set({ timeLeft: timeLeft - 1 });
    } else {
      get().handleSessionEnd(currentMode);
    }
  },

  handleSessionEnd: (mode) => {
    if (mode === "CLASS") {
      set((state) => ({
        messages: [...state.messages, { type: 'AI', content: "수업 시간이 끝났어요! 10분간 쉬는 시간을 가질까요?" }]
      }));
      get().setSessionMode("BREAK");
    } else if (mode === "BREAK") {
        set((state) => ({
            messages: [...state.messages, { type: 'AI', content: "쉬는 시간이 끝났습니다. 다음 학습을 시작해볼까요?" }]
        }));
        set({ isTimerRunning: false });
    }
  },
}));

export default useStudyStore;