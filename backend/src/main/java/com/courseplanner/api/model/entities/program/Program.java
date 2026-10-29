package com.courseplanner.api.model.entities.program;

import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Entity
@Table(name = "program")
@Getter @Setter @NoArgsConstructor
public class Program {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String programType; // DEGREE or GENERAL_EDUCATION

    private String college;
    private String degreeType;

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramCatalogVersion> catalogVersions = new ArrayList<>();
}
