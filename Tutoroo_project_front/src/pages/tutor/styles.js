import { css } from "@emotion/react";
import { theme } from "../../styles/theme"; 

export const container = css`
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 0 auto;
  padding-top: 60px;
  width: ${theme.layout.contentWidth};
`;

export const title = css`
  font-size: 32px;
  font-weight: 800;
  margin-bottom: 50px;
`;

export const contentWrap = css`
  display: flex;
  gap: 80px;
  justify-content: center;
  width: 100%;
`;

export const listPanel = css`
  display: flex;
  flex-direction: column;
  width: 380px;  
  gap: 17px;
`;

export const detailPanel = css`
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 70px;
  width: 500px;
  height: 550px;
  border-radius: 20px;
  background-color: #FFF9F0; 
  border: 1px solid #EADDD2;
  box-sizing: border-box;
`;

export const tutorItem = (isActive) => css`
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background-color: ${isActive ? "#FFF9F0" : "#fff"};
  border: 1px solid ${isActive ? theme.colors.primary : "#eee"};
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  }

  .profile { 
    width: 68px; 
    height: 68px; 
    object-fit: contain; 
  }
  
  .name { 
    padding-left: 24px;
    flex: 1; 
    text-align: left; 
    font-size: 20px;
    font-weight: 700; 
    color: ${isActive ? theme.colors.primary : "#333"}; 
  }

  .arrow { 
    color: #999; 
  }
`;

export const customBtn = (isActive) => css`
  justify-content: center;
  ${tutorItem(isActive)};
  height: 64px;
  margin-top: 10px;

  .name { 
    flex: unset; 
    text-align: center;
    padding-left: 0;
  }
`;

export const infoBox = css`
  text-align: center; 
  width: 100%; 
  display: flex; 
  flex-direction: column;
  align-items: center;
`;

export const detailProfileImg = css`
  width: 160px; 
  height: 160px; 
  object-fit: contain; 
  margin-bottom: 20px;
`;

export const guideText = css`
  font-size: 18px;
  line-height: 1.5;
  color: #555;
  margin-bottom: 30px;
  
  strong {
    color: ${theme.colors.primary};
  }
`;

export const descBox = css`
  background-color: #fff;
  padding: 20px;
  border-radius: 12px;
  width: 80%;
  margin-bottom: 30px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.03);

  strong {
    display: block;
    margin-bottom: 8px;
    font-size: 16px;
    color: #333;
  }

  p {
    font-size: 14px;
    color: #666;
    line-height: 1.4;
    margin: 0;
  }
`;

export const customInput = css`
  width: 80%;
  height: 120px;
  padding: 15px;
  border: 1px solid #ddd;
  border-radius: 12px;
  resize: none;
  font-size: 15px;
  margin-bottom: 30px;
  
  &:focus {
    outline: none;
    border-color: ${theme.colors.primary};
  }
`;

export const startBtn = css`
  width: 200px;
  height: 52px;
  background-color: ${theme.colors.primary};
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  border: none;
  border-radius: 100px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: ${theme.colors.primaryDark || "#e0a800"};
  }
  
  &:disabled {
    background-color: #ccc;
    cursor: not-allowed;
  }
`;

export const disabledBtn = css`
  background-color: #f5f5f5;
  border: 1px solid #ddd;
  cursor: not-allowed;
  opacity: 0.7;

  &:hover {
    transform: none;
    box-shadow: none;
  }

  .name {
    color: #999;
    font-size: 16px;
    font-weight: 500;
  }
`;