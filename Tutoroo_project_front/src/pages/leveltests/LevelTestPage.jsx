/** @jsxImportSource @emotion/react */
import { useEffect, useState } from "react";
import Header from "../../components/layouts/Header";
import * as s from "./styles";
import { sendBtn } from "./styles";

const QUESTIONS = [
  "학습할 과목을 입력해주세요. (예: Java, Python)",
  "이 과목을 얼마나 공부해보셨나요?",
  "간단한 문제를 풀어볼게요.\nJava에서 변수 선언 방법은?",
];

function LevelTestPage() {
  const [showMenu, setShowMenu] = useState(false);
  const [isCompleted, setIsCompleted] = useState(false);
  const [messages, setMessages] = useState([
    { role: "ai", content: "수준 파악을 시작해볼게요 🙂" },
  ]);
  const [step, setStep] = useState(0);
  const [input, setInput] = useState("");

  // AI 질문 출력
  useEffect(() => {
    if (step < QUESTIONS.length) {
      setMessages((prev) => [
        ...prev,
        { role: "ai", content: QUESTIONS[step] },
      ]);
    }
  }, [step]);

  const handleSubmit = () => {
    if (!input.trim()) return;

    setMessages((prev) => [...prev, { role: "user", content: input }]);

    setInput("");

    // 마지막 질문
    if (step === QUESTIONS.length - 1) {
      setMessages((prev) => [
        ...prev,
        {
          role: "ai",
          content:
            "레벨 테스트가 완료되었습니다 \n대시보드에서 튜터를 선택하고 학습을 시작해보세요.",
        },
      ]);
      setIsCompleted(true);
      return;
    }

    setStep((prev) => prev + 1);
  };

  return (
    <>
      <Header />
      <div css={s.pageContainer}>
        {/* 채팅 영역 */}
        <main css={s.chatArea}>
          {messages.map((msg, idx) => (
            <div key={idx} css={msg.role === "ai" ? s.aiBubble : s.userBubble}>
              {msg.content}
            </div>
          ))}
        </main>

        {/* 하단 입력 영역 */}
        <footer css={s.bottomArea}>
          <div css={s.bottomInner}>
            <button css={s.plusBtn} onClick={() => setShowMenu(!showMenu)}>
              ＋
            </button>
            {showMenu && (
              <div css={s.plusMenu}>
                <label>
                  📷 사진 업로드
                  <input type="file" accept="image/*" hidden />
                </label>
                <label>
                  📎 파일 업로드
                  <input type="file" hidden />
                </label>
              </div>
            )}
            <div css={s.inputWrapper}>
              <input
                css={s.inputBox}
                value={input}
                placeholder="답변을 입력하세요."
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    handleSubmit();
                  }
                }}
              />
            </div>

            <button css={s.sendBtn} onClick={handleSubmit}>
              전송
            </button>
          </div>
        </footer>
      </div>
    </>
  );
}

export default LevelTestPage;
