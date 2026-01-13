/** @jsxImportSource @emotion/react */
import * as s from "./styles";
import useModalStore from "../../stores/modalStore";
import { useState } from "react";

function FindPwModal() {
  const closeFindPw = useModalStore((state) => state.closeFindPw);
  const openLogin = useModalStore((state) => state.openLogin);

  const [username, setUsername] = useState("");
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");

  const handleSubmit = (e) => {
    e.preventDefault();

    if (!username || !name || !phone || !email) {
      alert("모든 항목을 입력해주세요.");
      return;
    }

    // ⚠️ 임시 처리 (추후 비밀번호 재설정 API 연동)
    alert("입력하신 정보로 비밀번호 재설정을 진행합니다.");
    closeFindPw();
    openLogin();
  };

  return (
    <div css={s.overlay} onClick={closeFindPw}>
      <div css={s.modal} onClick={(e) => e.stopPropagation()}>
        <div css={s.title}>비밀번호 찾기</div>

        <form css={s.form} onSubmit={handleSubmit}>
          {/* 아이디 */}
          <label css={s.formLabel}>
            <span css={s.required}>*</span>
            아이디
          </label>
          <input
            css={s.input}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="가입 시 사용한 아이디"
          />

          {/* 이름 */}
          <label css={s.formLabel}>
            <span css={s.required}>*</span>
            이름
          </label>
          <input
            css={s.input}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="이름"
          />

          {/* 전화번호 */}
          <label css={s.formLabel}>
            <span css={s.required}>*</span>
            전화번호
          </label>
          <input
            css={s.input}
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="010-0000-0000"
          />

          {/* 이메일 */}
          <label css={s.formLabel}>
            <span css={s.required}>*</span>
            이메일
          </label>
          <input
            css={s.input}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="가입 시 사용한 이메일"
          />

          <button css={s.submitBtn} type="submit">
            비밀번호 찾기
          </button>
        </form>

        <div css={s.loginRow}>
          <span css={s.loginMent}>로그인 화면으로 돌아가기</span>
          <span css={s.loginLink} onClick={openLogin}>
            로그인
          </span>
        </div>
      </div>
    </div>
  );
}

export default FindPwModal;
