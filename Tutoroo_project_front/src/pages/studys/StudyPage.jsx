/** @jsxImportSource @emotion/react */
import { useState, useEffect, useRef } from "react";
import Header from "../../components/layouts/Header";
import SessionStatus from "../../components/studys/SessionStatus"; 
import useStudyStore from "../../stores/useStudyStore";
import * as s from "./styles";

function StudyPage() {
  const { messages, sendMessage, isChatLoading } = useStudyStore();
  const [inputText, setInputText] = useState("");
  const scrollRef = useRef(null);
  const audioRef = useRef(new Audio());

  // 스크롤 자동 이동 (메시지 추가 시)
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, isChatLoading]);

  useEffect(() => {
    if (messages.length > 0) {
      const lastMsg = messages[messages.length - 1];
      if (lastMsg.type === 'AI' && lastMsg.audioUrl) {
        audioRef.current.pause();
        audioRef.current.src = lastMsg.audioUrl;
        audioRef.current.play().catch(e => console.log("Audio play blocked:", e));
      }
    }
  }, [messages]);

  const handleSend = () => {
    if (!inputText.trim() || isChatLoading) return;
    sendMessage(inputText);
    setInputText("");
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter" && !e.nativeEvent.isComposing) {
      handleSend();
    }
  };

  return (
    <>
      <Header />
      <div css={s.pageContainer}>
        {/* 채팅 로그 영역 */}
        <main css={s.chatArea} ref={scrollRef}>
          {messages.length === 0 ? (
            <div css={s.placeholder}>
              <p>수업 준비 중입니다...</p>
            </div>
          ) : (
            messages.map((msg, index) => {
              const isUser = msg.type === "USER";
              return (
                <div key={index} css={s.messageRow(isUser)}>
                  {/* AI 프로필 아이콘 (AI일 때만 표시) */}
                  {!isUser && <div css={s.aiProfileIcon} />} 
                  
                  <div css={s.bubble(isUser)}>
                    {msg.content}
                  </div>
                </div>
              );
            })
          )}
          
          {/* AI가 생각 중일 때 표시 */}
          {isChatLoading && (
            <div css={s.messageRow(false)}>
              <div css={s.aiProfileIcon} />
              <div css={s.bubble(false)}>
                <span className="dot-flashing">...</span>
              </div>
            </div>
          )}
        </main>

        {/* 하단 입력 영역 */}
        <footer css={s.bottomArea}>
            <div css={s.bottomInner}>
                {/* [왼쪽 하단] 타이머 및 세션 상태 */}
                <SessionStatus />

                {/* 채팅 입력창 */}
                <div css={s.inputWrapper}>
                    <input 
                      type="text" 
                      placeholder="AI 튜터에게 질문해보세요." 
                      css={s.inputBox}
                      value={inputText}
                      onChange={(e) => setInputText(e.target.value)}
                      onKeyDown={handleKeyDown}
                      disabled={isChatLoading}
                      autoFocus
                    />
                </div>
                
                <button 
                  css={s.sendBtn} 
                  onClick={handleSend}
                  disabled={isChatLoading}
                >
                  전송
                </button>
            </div>
        </footer>
      </div>
    </>
  );
}

export default StudyPage;