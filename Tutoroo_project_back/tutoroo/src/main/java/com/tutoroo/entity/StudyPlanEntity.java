package com.tutoroo.entity;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyPlanEntity {
    private Long id;
    private Long userId;
    private String goal;

    // 선생님 타입 (예: "TIGER", "KANGAROO")
    // TeacherType 엔티티 없이 문자열로 관리하여 구조를 단순화
    private String persona;

    private String customTutorName; // 선생님 애칭

    private String roadmapJson;
    private LocalDate startDate;
    private LocalDate endDate;
    private double progressRate;

    private Boolean isPaid;
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long getDaysRemaining() {
        if (endDate == null) return 0;
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), endDate);
        return days < 0 ? 0 : days;
    }
}