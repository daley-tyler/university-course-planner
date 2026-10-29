package com.courseplanner.api.model.entities.catalog;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "course", uniqueConstraints = @UniqueConstraint(columnNames = {"subject_id", "number"}))
@Getter
@Setter
@NoArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "prereq_text")
    private String prereqText;

    @Column(name = "coreq_text")
    private String coreqText;

    @Column(name = "attribute_text")
    private String attributeText;

    @Column(name = "restriction_text")
    private String restrictionText;

    @Column(name = "credits", nullable = false)
    private BigDecimal credits;

    @Column(name = "normalized", columnDefinition = "boolean default false", nullable = false)
    private boolean normalized;

}