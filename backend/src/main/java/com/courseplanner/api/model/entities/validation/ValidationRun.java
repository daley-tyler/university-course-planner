package com.courseplanner.api.model.entities.validation;

import com.courseplanner.api.model.entities.plans.CoursePlan;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "validation_run")
@Getter @Setter @NoArgsConstructor
public class ValidationRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private CoursePlan plan;

    private Date ranAt = new Date();
}