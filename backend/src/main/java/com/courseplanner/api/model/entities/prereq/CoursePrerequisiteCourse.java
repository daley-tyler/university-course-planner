package com.courseplanner.api.model.entities.prereq;

import com.courseplanner.api.model.entities.catalog.Course;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "course_prerequisite_course")
@Getter @Setter @NoArgsConstructor
public class CoursePrerequisiteCourse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private CoursePrerequisiteGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_course_id")
    private Course requiredCourse;

    private String minGrade;
    private boolean concurrentAllowed;
}