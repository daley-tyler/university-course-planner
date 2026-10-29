package com.courseplanner.api.restAPIControllers;

import com.courseplanner.api.Bulletin.BulletinScraperService;
import com.courseplanner.api.Bulletin.Programs.BulletinYearService;
import com.courseplanner.api.Bulletin.Programs.ProgramScraperService;
import com.courseplanner.api.Bulletin.Requirements.ProgramRequirementScraper;
import com.courseplanner.api.Bulletin.dto.ProgramMeta;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import com.courseplanner.api.model.entities.program.ProgramCatalogPolicy;
import com.courseplanner.api.model.entities.program.Program;
import com.courseplanner.api.model.repositories.ProgramCatalogPolicyRepository;
import com.courseplanner.api.model.repositories.ProgramCatalogVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/bulletin")
@RequiredArgsConstructor
public class BulletinController {

    private final BulletinYearService service;
    private final ProgramScraperService scraper;
    private final BulletinScraperService bulletinScraperService;
    private final ProgramRequirementScraper requirementScraper;
    private final ProgramCatalogVersionRepository programCatalogVersionRepository;
    private final ProgramCatalogPolicyRepository programCatalogPolicyRepository;

    @GetMapping("/years")
    public List<String> getYears() {
        log.debug("GET /api/bulletin/years");
        return service.getAllBulletinRoots();
    }

    @GetMapping("/years/last5")
    public List<String> getLast5() {
        return service.getLastNBulletins(5);
    }

    @GetMapping("/scrapeLast5")
    public List<ProgramCatalogVersion> scrapeLast5() {
        return scraper.scrapeAndStoreForLastNBulletins(5);
    }

    @PostMapping("/runFullPipeline")
    public String runFullPipeline(@RequestParam(name = "years", defaultValue = "5") int years) {
        log.info("Running full bulletin pipeline for last {} year(s)", years);
        List<ProgramCatalogVersion> pcvs =
                bulletinScraperService.runFullPipelineForLastNBulletins(years);

        return "Full pipeline completed for " + pcvs.size()
                + " program catalog versions over last " + years + " bulletin year(s).";
    }

    @PostMapping("/refreshRequirements")
    public String refreshRequirements(
            @RequestParam(name = "programId") Long programId,
            @RequestParam(name = "catalogLabel", required = false) String catalogLabel,
            @RequestParam(name = "catalogYear", required = false) Integer catalogYear
    ) {
        ProgramCatalogVersion pcv = null;
        if (catalogLabel != null && !catalogLabel.isBlank()) {
            pcv = programCatalogVersionRepository
                    .findByProgramIdAndVersionLabel(programId, catalogLabel)
                    .orElse(null);
        }
        if (pcv == null && catalogYear != null) {
            pcv = programCatalogVersionRepository
                    .findByProgramIdAndCatalogYear(programId, catalogYear)
                    .orElse(null);
        }
        if (pcv == null) {
            return "No ProgramCatalogVersion found for programId=" + programId;
        }

        ProgramCatalogPolicy policy = programCatalogPolicyRepository
                .findByCatalogVersion_Id(pcv.getId())
                .orElse(null);
        if (policy == null || policy.getSourceUrl() == null || policy.getSourceUrl().isBlank()) {
            return "No source URL found for programId=" + programId + " catalogLabel=" + pcv.getVersionLabel();
        }

        Program program = pcv.getProgram();
        ProgramMeta meta = ProgramMeta.builder()
                .catalogLabel(pcv.getVersionLabel())
                .programName(program != null ? program.getName() : "")
                .programUrl(policy.getSourceUrl())
                .slug(program != null ? program.getCode() : "")
                .collegeName(program != null ? program.getCollege() : null)
                .build();

        requirementScraper.scrapeAndPersistRequirements(meta);
        return "Requirements refreshed for programId=" + programId + " catalogLabel=" + pcv.getVersionLabel();
    }
}
