package com.courseplanner.api.model.entities.plans;

import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Entity
@Table(name = "course_plan")
@Getter @Setter @NoArgsConstructor
public class CoursePlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    private String name;
    private Date createdAt;
    private Date updatedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanSemester> semesters = new ArrayList<>();
}