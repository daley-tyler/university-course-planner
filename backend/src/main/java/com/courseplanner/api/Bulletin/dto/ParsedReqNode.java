package com.courseplanner.api.Bulletin.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class ParsedReqNode {
    private String name;
    private String kind;           // "ALL_OF","ANY_OF","N_OF","CREDIT_AT_LEAST","LEAF_COURSES","LEAF_ATTRIBUTES","LEAF_QUERY"
    private Integer nRequired;     // for N_OF
    private BigDecimal creditMin;  // for CREDIT_AT_LEAST or ranges
    private BigDecimal creditMax;
    private String notes;
    private Integer levelMin;
    private BigDecimal levelCap100;
    private BigDecimal levelCap200;
    private BigDecimal levelCap300;
    private BigDecimal levelCap400;
    private Integer levelCap100Courses;
    private Integer levelCap200Courses;
    private Integer levelCap300Courses;
    private Integer levelCap400Courses;
    private String gradeMin;
    private BigDecimal residencyCredits;
    @Builder.Default
    private Boolean doubleCountAllowed = Boolean.TRUE;
    private BigDecimal doubleCountCap;
    private String footnoteText;
    private String sourceUrl;
    private String sourceRef;

    @Builder.Default
    private List<ParsedReqNode> children = new ArrayList<>();

    @Builder.Default
    private List<ParsedReqNodeCourseOption> courseOptions = new ArrayList<>();
}
