package com.courseplanner.api.api.services;

import com.courseplanner.api.api.dto.ProgramListResponse;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;

public interface ProgramQueryService {
    ProgramListResponse listPrograms(Integer catalogYear, String catalogLabel);

    ProgramCatalogVersion resolveCatalogVersion(Long programId, Integer catalogYear, String catalogLabel);
}
