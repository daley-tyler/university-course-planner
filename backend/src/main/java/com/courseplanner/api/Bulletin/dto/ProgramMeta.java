package com.courseplanner.api.Bulletin.dto;

import com.courseplanner.api.model.entities.enums.ProgramKind;
import lombok.Builder;
import lombok.Data;

/**
 * DTO produced during Phase 2 scraping.
 * This is NOT a JPA entity — only a scraper-side metadata object.
 */
@Data
@Builder
public class ProgramMeta {

    private String catalogRoot;   // root bulletin URL (current or archive)
    private String catalogLabel;  // e.g. "2025-2026"

    private String collegeName;   // e.g. "School of Business"
    private String collegeUrl;    // absolute URL of the college page

    private String programName;   // e.g. "Marketing"
    private String programUrl;    // absolute program page URL
    private String slug;          // e.g. "marketing-bsb"

    private ProgramKind kind;     // MAJOR / MINOR / CO_MAJOR / CERTIFICATE
    private String degreeLabel;   // raw H2 heading ("Bachelor of Science in Business")
}
