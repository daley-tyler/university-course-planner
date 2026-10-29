package com.courseplanner.api.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseFiltersResponse {
    private List<DepartmentDto> departments;
    private List<Integer> levels;
    private List<AttributeDto> attributes;
}
