package com.courseplanner.api.model.entities.plans;

import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "plan_program_degree")
@Getter @Setter @NoArgsConstructor
public class PlanProgramDegree {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private CoursePlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pcv_id")
    private ProgramCatalogVersion programCatalogVersion;
}