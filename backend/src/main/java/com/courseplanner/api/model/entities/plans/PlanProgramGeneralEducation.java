package com.courseplanner.api.model.entities.plans;

import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "plan_program_general_education")
@Getter @Setter @NoArgsConstructor
public class PlanProgramGeneralEducation {
    @Id
    @Column(name = "plan_id")
    private Long planId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "plan_id")
    private CoursePlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pcv_id")
    private ProgramCatalogVersion programCatalogVersion;
}