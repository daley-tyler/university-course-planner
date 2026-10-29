package com.courseplanner.api.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgramSummaryDto {
    private Long id;
    private String code;
    private String name;
    private String programType;
    private String degreeType;
    private String college;
    private Long catalogVersionId;
}
