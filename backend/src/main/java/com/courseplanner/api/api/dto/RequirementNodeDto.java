package com.courseplanner.api.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequirementNodeDto {
    private Long id;
    private String name;
    private String kind;
    private Integer nRequired;

    @JsonProperty("nRequired")
    public Integer getNRequired() { return nRequired; }

    @JsonProperty("nRequired")
    public void setNRequired(Integer nRequired) { this.nRequired = nRequired; }

    private BigDecimal creditMin;
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
    private Boolean doubleCountAllowed;
    private BigDecimal doubleCountCap;
    private String footnoteText;
    private RequirementSourceDto source;

    @Builder.Default
    private List<RequirementCourseOptionDto> courseOptions = new ArrayList<>();
    @Builder.Default
    private List<RequirementAttributeOptionDto> attributeOptions = new ArrayList<>();
    @Builder.Default
    private List<RequirementNodeDto> children = new ArrayList<>();
}
