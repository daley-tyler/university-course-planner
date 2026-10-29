package com.courseplanner.api.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseSearchResponse {
    private List<CourseSummaryDto> items;
    private int page;
    private int pageSize;
    private long total;
    private Integer nextPage;
}
