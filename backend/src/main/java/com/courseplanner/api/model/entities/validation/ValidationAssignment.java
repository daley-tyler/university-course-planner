package com.courseplanner.api.model.entities.validation;

import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import com.courseplanner.api.model.entities.requirements.ReqNode;
import com.courseplanner.api.model.entities.plans.PlanCourse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "validation_assignment")
@Getter @Setter @NoArgsConstructor
public class ValidationAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private ValidationRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pcv_id")
    private ProgramCatalogVersion programCatalogVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "req_node_id")
    private ReqNode reqNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_course_id")
    private PlanCourse planCourse;

    private BigDecimal creditsApplied;
}