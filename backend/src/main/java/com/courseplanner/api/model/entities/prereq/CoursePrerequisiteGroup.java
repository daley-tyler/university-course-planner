package com.courseplanner.api.model.entities.prereq;

import com.courseplanner.api.model.entities.catalog.Course;
import com.courseplanner.api.model.entities.enums.PrereqKind;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "course_prerequisite_group")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoursePrerequisiteGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrereqKind kind;

    @Column(nullable = false)
    private String operator;

    @Column
    private String description;
}
