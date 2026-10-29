package com.courseplanner.api.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequirementAttributeOptionDto {
    private String code;
    private String name;
    private String category;
}
