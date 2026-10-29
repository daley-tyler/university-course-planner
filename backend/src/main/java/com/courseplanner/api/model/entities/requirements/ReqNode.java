package com.courseplanner.api.model.entities.requirements;

import com.courseplanner.api.model.entities.enums.ReqNodeKind;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "req_node")
@Getter @Setter @NoArgsConstructor
public class ReqNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ----------------------------------------------- */
    /* PROGRAM CATALOG VERSION                         */
    /* ----------------------------------------------- */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pcv_id", nullable = false)
    private ProgramCatalogVersion catalogVersion;

    /* ----------------------------------------------- */
    /* PARENT                                           */
    /* ----------------------------------------------- */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ReqNode parent;

    /* ----------------------------------------------- */
    /* CHILDREN                                         */
    /* ----------------------------------------------- */
    @OneToMany(mappedBy = "parent",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<ReqNode> children = new ArrayList<>();

    /* ----------------------------------------------- */
    /* FIELDS                                           */
    /* ----------------------------------------------- */
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReqNodeKind kind;

    private Integer orderIndex;
    private Integer nRequired;
    private BigDecimal creditMin;
    private BigDecimal creditMax;
    private String notes;
    @Column(columnDefinition = "smallint")
    private Short levelMin;

    @Column(name = "level_cap100")
    private BigDecimal levelCap100;

    @Column(name = "level_cap200")
    private BigDecimal levelCap200;

    @Column(name = "level_cap300")
    private BigDecimal levelCap300;

    @Column(name = "level_cap400")
    private BigDecimal levelCap400;

    @Column(name = "level_cap100_courses", columnDefinition = "smallint")
    private Short levelCap100Courses;

    @Column(name = "level_cap200_courses", columnDefinition = "smallint")
    private Short levelCap200Courses;

    @Column(name = "level_cap300_courses", columnDefinition = "smallint")
    private Short levelCap300Courses;

    @Column(name = "level_cap400_courses", columnDefinition = "smallint")
    private Short levelCap400Courses;
    private String gradeMin;
    private BigDecimal residencyCredits;

    @Column(columnDefinition = "boolean default true")
    private Boolean doubleCountAllowed = true;

    private BigDecimal doubleCountCap;
    private String footnoteText;
    private String sourceUrl;
    private String sourceRef;

    /* ----------------------------------------------- */
    /* HELPERS: SAFE ADD/REMOVE                        */
    /* ----------------------------------------------- */

    public void addChild(ReqNode child) {
        if (child == null) return;

        // IMPORTANT: assign parent first
        child.setParent(this);

        // Avoid duplicates (critical!)
        if (!this.children.contains(child)) {
            this.children.add(child);
        }
    }

    public void removeChild(ReqNode child) {
        if (child == null) return;

        this.children.remove(child);
        child.setParent(null);
    }
}
