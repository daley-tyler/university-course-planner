package com.courseplanner.api.api.controllers;

import com.courseplanner.api.api.dto.CourseDetailDto;
import com.courseplanner.api.api.dto.CourseFiltersResponse;
import com.courseplanner.api.api.dto.CourseSearchRequest;
import com.courseplanner.api.api.dto.CourseSearchResponse;
import com.courseplanner.api.api.services.CourseQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseQueryController {

    private final CourseQueryService courseQueryService;

    @GetMapping
    public ResponseEntity<CourseSearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> department,
            @RequestParam(required = false) List<Integer> level,
            @RequestParam(required = false) List<String> attribute,
            @RequestParam(required = false) BigDecimal minCredits,
            @RequestParam(required = false) BigDecimal maxCredits,
            @RequestParam(required = false) Long requirementId,
            @RequestParam(required = false) Integer catalogYear,
            @RequestParam(required = false) String catalogLabel,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "25") Integer pageSize
    ) {
        CourseSearchRequest request = CourseSearchRequest.builder()
                .q(q)
                .departments(department)
                .levels(level)
                .attributes(attribute)
                .minCredits(minCredits)
                .maxCredits(maxCredits)
                .requirementId(requirementId)
                .catalogYear(catalogYear)
                .catalogLabel(catalogLabel)
                .page(page)
                .pageSize(pageSize)
                .build();

        log.debug("GET /api/courses q={} departments={} page={}", q, department, page);
        CourseSearchResponse response = courseQueryService.search(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(response);
    }

    @GetMapping("/filters")
    public ResponseEntity<CourseFiltersResponse> filters(
            @RequestParam(required = false) Integer catalogYear
    ) {
        CourseFiltersResponse response = courseQueryService.getFilters(catalogYear);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(response);
    }

    @GetMapping("/{courseCode}")
    public ResponseEntity<CourseDetailDto> getCourse(@PathVariable String courseCode) {
        CourseDetailDto detail = courseQueryService.getCourse(courseCode);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(detail);
    }
}
