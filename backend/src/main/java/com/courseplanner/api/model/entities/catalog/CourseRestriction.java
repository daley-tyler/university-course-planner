package com.courseplanner.api.model.entities.catalog;

import com.courseplanner.api.model.entities.enums.RestrictionKind;
import com.courseplanner.api.model.entities.enums.RestrictionMode;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "course_restriction",
        uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "restriction_type", "mode", "value"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "restriction_type", nullable = false)
    private RestrictionKind restrictionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private RestrictionMode mode;

    @Column(nullable = false)
    private String value;
}
