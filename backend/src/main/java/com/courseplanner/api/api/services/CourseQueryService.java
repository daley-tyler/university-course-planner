package com.courseplanner.api.api.services;

import com.courseplanner.api.api.dto.CourseDetailDto;
import com.courseplanner.api.api.dto.CourseFiltersResponse;
import com.courseplanner.api.api.dto.CourseSearchRequest;
import com.courseplanner.api.api.dto.CourseSearchResponse;

public interface CourseQueryService {
    CourseSearchResponse search(CourseSearchRequest request);

    CourseDetailDto getCourse(String courseCode);

    CourseFiltersResponse getFilters(Integer catalogYear);
}
