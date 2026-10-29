package com.courseplanner.api.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseSearchRequest {
    private String q;
    private List<String> departments;
    private List<Integer> levels;
    private List<String> attributes;
    private BigDecimal minCredits;
    private BigDecimal maxCredits;
    private Long requirementId;
    private Integer catalogYear;
    private String catalogLabel;
    private Integer page;
    private Integer pageSize;
}
