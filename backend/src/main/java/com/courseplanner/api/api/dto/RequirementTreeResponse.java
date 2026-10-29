package com.courseplanner.api.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequirementTreeResponse {
    private Long programId;
    private Integer catalogYear;
    private String catalogLabel;
    private Long catalogVersionId;
    private RequirementNodeDto root;
}
