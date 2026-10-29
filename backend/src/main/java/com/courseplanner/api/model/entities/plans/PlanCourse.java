package com.courseplanner.api.model.entities.plans;

import com.courseplanner.api.model.entities.catalog.Course;
import com.courseplanner.api.model.entities.enums.PlanCourseStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "plan_course")
@Getter @Setter @NoArgsConstructor
public class PlanCourse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private CoursePlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    private String customTitle;
    private BigDecimal customCredits;

    @Enumerated(EnumType.STRING)
    private PlanCourseStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id")
    private PlanSemester semester;

    private String notes;
}