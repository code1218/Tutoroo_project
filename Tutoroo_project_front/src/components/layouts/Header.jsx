/** @jsxImportSource @emotion/react */

import * as s from "./styles";
import logoImg from "../../assets/images/mascots/logo.jpg"; // ✅ 네 경로 기준
import { useNavigate } from "react-router-dom";

function Header() {
  const navigate = useNavigate();
  
  return (
    <header css={s.header}>
      <div css={s.inner}>
        <div css={s.logoWrap}>
          <img css={s.logoImg} src={logoImg} alt="Tutoroo" />
          <span css={s.logoText}>Tutoroo</span>
        </div>

        <button css={s.profileBtn} onClick={() => navigate("/mypage/verify")}>내 정보 ▼</button>
      </div>
    </header>
  );
}

export default Header;
