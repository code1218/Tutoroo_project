import { Route, Routes } from "react-router-dom";
import DashboardPage from "../pages/dashboards/DashboardPage";
import LoginPage from "../pages/auths/LoginPage";
import PasswordVerifyPage from "../pages/mypage/PasswordVerifyPage";
import ChangeInfoPage from "../pages/mypage/ChangeInfoPage";
import Sidebar from "../pages/mypage/Sidebar";
import ChangePasswordPage from "../pages/mypage/ChangePasswordPage";
import WithdrawalPage from "../pages/mypage/WithdrawalPage";

function Router() {
  return (
    <Routes>
      <Route path="/" element={<DashboardPage />} />

      {/* 선택: /login 진입 시 대시보드로 보내고 로그인 모달만 오픈 */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/mypage/verify" element={<PasswordVerifyPage />} />
      {/* <Route path="sidebar" element={<Sidebar />} /> */}
      <Route path="mypage/changeinfo" element={<ChangeInfoPage/>}/>
      <Route path="mypage/changepassword" element={<ChangePasswordPage />} />
      <Route path="mypage/withdrawl" element={<WithdrawalPage />} />
      
    </Routes>
  );
}

export default Router;