package com.courseplanner.api.Bulletin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParsedReqNodeCourseOption {
    private String subjectCode; // e.g., "CSE"
    private String courseNumber; // e.g., "174"
}
