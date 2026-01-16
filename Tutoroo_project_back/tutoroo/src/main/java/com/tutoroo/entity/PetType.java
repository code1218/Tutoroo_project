package com.tutoroo.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [기능: 육성 가능한 다마고치 종류 (10마리)]
 * 설명: 멤버십 등급에 따라 해금되며, 유저가 포인트로 키우는 대상입니다.
 */
@Getter
@RequiredArgsConstructor
public enum PetType {

    // Basic (기본)
    TIGER("아기 호랑이", "용맹하게 자라날 호랑이"),
    RABBIT("아기 토끼", "깡충깡충 귀여운 토끼"),
    TURTLE("아기 거북이", "느리지만 튼튼한 거북이"),

    // Standard (스탠다드)
    HONEY_BADGER("벌꿀오소리", "겁 없는 파이터 오소리"),
    QUOKKA("쿼카", "웃는 얼굴의 행복 전도사"),
    KANGAROO("캥거루", "주먹이 운다! 복서 캥거루"),
    EASTERN_DRAGON("동양용", "여의주를 품은 신비한 용"),

    // Premium (프리미엄)
    ROCK_HYRAX("바위너구리", "작지만 친구가 많은 인싸 너구리"),
    PANDA("팬더", "대나무를 좋아하는 귀차니즘 팬더"),
    WESTERN_DRAGON("서양용", "황금을 지키는 강력한 드래곤");

    private final String name;
    private final String description;
}