package com.courseplanner.api.api.controllers;

import com.courseplanner.api.api.dto.ProgramListResponse;
import com.courseplanner.api.api.dto.RequirementTreeResponse;
import com.courseplanner.api.api.services.ProgramQueryService;
import com.courseplanner.api.api.services.RequirementQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/programs")
@RequiredArgsConstructor
public class ProgramQueryController {

    private final ProgramQueryService programQueryService;
    private final RequirementQueryService requirementQueryService;

    @GetMapping
    public ResponseEntity<ProgramListResponse> listPrograms(
            @RequestParam(required = false) Integer catalogYear,
            @RequestParam(required = false) String catalogLabel
    ) {
        log.debug("GET /api/programs catalogYear={} catalogLabel={}", catalogYear, catalogLabel);
        ProgramListResponse response = programQueryService.listPrograms(catalogYear, catalogLabel);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(response);
    }

    @GetMapping("/{programId}/requirements")
    public ResponseEntity<RequirementTreeResponse> requirements(
            @PathVariable Long programId,
            @RequestParam(required = false) Integer catalogYear,
            @RequestParam(required = false) String catalogLabel
    ) {
        RequirementTreeResponse response =
                requirementQueryService.getRequirementTree(programId, catalogYear, catalogLabel);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(response);
    }
}
