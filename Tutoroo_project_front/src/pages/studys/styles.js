import { css } from "@emotion/react";
import { theme } from "../../styles/theme"; 

// 전체 페이지 컨테이너
export const pageContainer = css`
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px); 
  background-color: #f9f9f9;
  position: relative;
`;

// 채팅 로그 영역
export const chatArea = css`
  flex: 1;
  padding: 40px 20px 120px; 
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
  width: ${theme.layout.contentWidth};
  margin: 0 auto;

  /* 스크롤바 커스텀 */
  &::-webkit-scrollbar {
    width: 8px;
  }
  &::-webkit-scrollbar-thumb {
    background-color: #ddd;
    border-radius: 4px;
  }
`;

// 메시지 한 줄 (좌/우 배치)
export const messageRow = (isUser) => css`
  display: flex;
  justify-content: ${isUser ? "flex-end" : "flex-start"};
  align-items: flex-start;
  gap: 12px;
  width: 100%;
`;

// AI 프로필 아이콘
export const aiProfileIcon = css`
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background-color: #eee;
  border: 1px solid #ddd;
  background-image: url('/path/to/ai-icon.png'); 
  background-size: cover;
  flex-shrink: 0;
`;

// 말풍선 스타일
export const bubble = (isUser) => css`
  max-width: 60%;
  padding: 16px 22px;
  border-radius: 20px;
  font-size: 16px;
  line-height: 1.6;
  white-space: pre-wrap; 
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  
  ${isUser
    ? css`
        background-color: ${theme.colors.primary};
        color: #fff;
        border-top-right-radius: 4px;
      `
    : css`
        background-color: #fff;
        color: #333;
        border: 1px solid #e0e0e0;
        border-top-left-radius: 4px;
      `}
`;

export const placeholder = css`
  text-align: center;
  margin-top: 100px;
  color: #999;
  font-size: 16px;
`;

/* 하단 고정 영역 (입력창 + 타이머) */
export const bottomArea = css`
  position: fixed;
  bottom: 0;
  left: 0;
  width: 100%;
  background-color: #fff;
  border-top: 1px solid #eee;
  padding: 20px 0;
  z-index: 100;
  box-shadow: 0 -4px 20px rgba(0,0,0,0.03);
`;

export const bottomInner = css`
  width: ${theme.layout.contentWidth};
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 16px;
`;

export const inputWrapper = css`
  flex: 1;
  position: relative;
`;

export const inputBox = css`
  width: 100%;
  height: 56px;
  padding: 0 24px;
  border-radius: 100px;
  border: 1px solid #ddd;
  font-size: 16px;
  background-color: #f8f8f8;
  transition: all 0.2s;

  &:focus {
    outline: none;
    border-color: ${theme.colors.primary};
    background-color: #fff;
    box-shadow: 0 0 0 4px rgba(255, 165, 0, 0.1); 
  }
`;

export const sendBtn = css`
  width: 80px;
  height: 56px;
  border-radius: 100px;
  background-color: ${theme.colors.primary};
  color: #fff;
  font-weight: 700;
  border: none;
  cursor: pointer;
  transition: background-color 0.2s;
  font-size: 16px;

  &:hover {
    background-color: ${theme.colors.primaryDark || "#e0a800"};
  }

  &:disabled {
    background-color: #ddd;
    cursor: not-allowed;
  }
`;