package com.courseplanner.api.model.entities.validation;

import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import com.courseplanner.api.model.entities.requirements.ReqNode;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "validation_unmet")
@Getter @Setter @NoArgsConstructor
public class ValidationUnmet {
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

    @Column(columnDefinition = "jsonb")
    private String details;
}