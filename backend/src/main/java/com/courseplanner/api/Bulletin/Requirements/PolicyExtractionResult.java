package com.courseplanner.api.Bulletin.Requirements;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PolicyExtractionResult {
    String gradeMinText;
    List<String> gradeMinCourses;
    String notes;
    String sourceUrl;
}
