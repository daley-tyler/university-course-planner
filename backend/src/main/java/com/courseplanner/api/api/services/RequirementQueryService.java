package com.courseplanner.api.api.services;

import com.courseplanner.api.api.dto.RequirementTreeResponse;

public interface RequirementQueryService {
    RequirementTreeResponse getRequirementTree(Long programId, Integer catalogYear, String catalogLabel);
}
