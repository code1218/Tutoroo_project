/** @jsxImportSource @emotion/react */
import * as s from "./styles";
import useModalStore from "../../stores/modalStore";
import { useState } from "react";

function FindIdModal() {
  const closeFindId = useModalStore((state) => state.closeFindId);
  const openLogin = useModalStore((state) => state.openLogin);

  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");

  const handleSubmit = (e) => {
    e.preventDefault();

    if (!name || !phone || !email) {
      alert("모든 항목을 입력해주세요.");
      return;
    }

    // ⚠️ 임시 처리 (추후 API 연동)
    alert("입력하신 정보로 아이디를 찾고 있습니다.");
    closeFindId();
    openLogin();
  };

  return (
    <div css={s.overlay} onClick={closeFindId}>
      <div css={s.modal} onClick={(e) => e.stopPropagation()}>
        <div css={s.title}>아이디 찾기</div>

        <form css={s.form} onSubmit={handleSubmit}>
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
            아이디 찾기
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

export default FindIdModal;
