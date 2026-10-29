package com.courseplanner.api.model.entities.program;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Entity
@Table(name = "program_catalog_policy")
@Getter
@Setter
@NoArgsConstructor
public class ProgramCatalogPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pcv_id", nullable = false, unique = true)
    private ProgramCatalogVersion catalogVersion;

    @Column(name = "grade_min_text")
    private String gradeMinText;

    @Column(name = "grade_min_courses", columnDefinition = "text")
    private String gradeMinCourses;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private String sourceUrl;

    @Temporal(TemporalType.TIMESTAMP)
    private Date scrapedAt;
}
