package com.courseplanner.api.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequirementCourseOptionDto {
    private String code;
    private String title;
    private BigDecimal credits;
}
