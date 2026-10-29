package com.courseplanner.api.model.entities.program;

import jakarta.persistence.*;
import lombok.*;
import java.util.Date;

@Entity
@Table(name = "program_catalog_version")
@Getter
@Setter
@NoArgsConstructor
public class ProgramCatalogVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    private Integer catalogYear;
    private String versionLabel;

    @Temporal(TemporalType.DATE)   // match Flyway schema
    private Date effectiveStart;

    @Temporal(TemporalType.DATE)   // match Flyway schema
    private Date effectiveEnd;
}
