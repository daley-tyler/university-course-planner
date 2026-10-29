package com.courseplanner.api.model.entities.plans;

import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Entity
@Table(name = "plan_semester")
@Getter @Setter @NoArgsConstructor
public class PlanSemester {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private CoursePlan plan;

    private String name;
    private Integer sortOrder;

    @OneToMany(mappedBy = "semester", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanCourse> courses = new ArrayList<>();
}