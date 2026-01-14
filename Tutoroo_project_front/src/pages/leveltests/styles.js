import { css } from "@emotion/react";
import { theme } from "../../styles/theme";

export const pageContainer = css`
  width: 100%;
  height: calc(100vh - 80px);
  display: flex;
  flex-direction: column;
`;

export const chatArea = css`
  flex: 1;
  width: ${theme.layout.contentWidth};
  margin: 0 auto;
  overflow-y: auto;
  padding: 40px 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

export const bottomArea = css`
  width: 100%;
  border-top: 1px solid #eee;
  padding: 20px 0;
  background: #fff;
`;

export const bottomInner = css`
  position: relative;
  width: ${theme.layout.contentWidth};
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 20px;
`;

export const inputWrapper = css`
  flex: 1;
`;

export const inputBox = css`
  width: 100%;
  height: 50px;
  padding: 0 20px;
  border-radius: 25px;
  border: 1px solid #ccc;
  font-size: 16px;
  outline: none;

  &:focus {
    border-color: ${theme.colors.primary};
  }
`;

export const sendBtn = css`
  width: 80px;
  height: 50px;
  border-radius: 25px;
  background-color: ${theme.colors.primary};
  color: white;
  font-weight: 700;

  &:hover {
    background-color: ${theme.colors.accent};
  }
`;

export const aiBubble = css`
  align-self: flex-start;
  max-width: 70%;
  padding: 14px 18px;
  border-radius: 14px;
  background-color: #f5f5f5;
  border: 1px solid #ddd;
  white-space: pre-wrap;
  word-break: break-word;
`;

export const userBubble = css`
  align-self: flex-end;
  max-width: 70%;
  padding: 14px 18px;
  border-radius: 14px;
  background-color: ${theme.colors.primary};
  color: white;
  white-space: pre-wrap;
  word-break: break-word;
`;

export const plusMenu = css`
  position: absolute;
  bottom: 80px;
  left: 50%;
  transform: translateX(-50%);
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 12px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);

  label {
    cursor: pointer;
    font-size: 14px;
  }
`;
