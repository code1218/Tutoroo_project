package com.tutoroo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [기능: AI 선생님 캐릭터 (5종)]
 * 설명: 다마고치(Pet)와 별개로, 학습을 지도하는 5가지 페르소나입니다.
 * DB 테이블(Entity)을 만들지 않고 Enum으로 가볍게 관리합니다.
 */
@Getter
@RequiredArgsConstructor
public enum TeacherType {

    TIGER("호랑이 선생님", "엄격하고 무서운 스파르타 스타일. 정신 개조 전문."),
    RABBIT("토끼 선생님", "빠르고 핵심만 짚어주는 속성 과외 스타일. 딴짓하면 혼남."),
    TURTLE("거북이 선생님", "친절하고 꼼꼼하게 기초부터 다져주는 스타일. 무한 인내심."),
    KANGAROO("캥거루 선생님", "파이팅 넘치는 열혈 체육관 관장 스타일. 지치지 않는 텐션."),
    EASTERN_DRAGON("동양용 선생님", "깊은 깨달음을 주는 현자 스타일. 하오체 사용.");

    private final String name;
    private final String description;
}