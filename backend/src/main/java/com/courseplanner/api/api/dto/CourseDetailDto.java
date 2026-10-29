package com.courseplanner.api.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseDetailDto {
    private Long id;
    private String code;
    private String title;
    private BigDecimal credits;
    private String department;
    private Integer level;
    private List<String> attributes;
    private List<String> prereqs;
    private String description;
    private String prereqText;
    private String coreqText;
    private String restrictionText;
    private String attributeText;
}
