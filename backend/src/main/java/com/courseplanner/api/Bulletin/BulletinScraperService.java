// java
package com.courseplanner.api.Bulletin;

import com.courseplanner.api.Bulletin.Programs.BulletinYearService;
import com.courseplanner.api.Bulletin.Programs.CollegeDiscoveryService;
import com.courseplanner.api.Bulletin.Programs.CollegePageParser;
import com.courseplanner.api.Bulletin.Programs.ProgramPersistenceService;
import com.courseplanner.api.Bulletin.Requirements.ProgramRequirementScraper;
import com.courseplanner.api.Bulletin.dto.CollegeLink;
import com.courseplanner.api.Bulletin.dto.ProgramMeta;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import com.courseplanner.api.model.entities.scrapeLog.BulletinScrapeLog;
import com.courseplanner.api.model.repositories.BulletinScrapeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulletinScraperService {

    private final BulletinYearService bulletinYearService;
    private final CollegeDiscoveryService collegeDiscoveryService;
    private final CollegePageParser collegePageParser;
    private final ProgramPersistenceService programPersistenceService;
    private final ProgramRequirementScraper requirementScraper;
    private final BulletinScrapeLogRepository bulletinScrapeLogRepository;

    /**
     * Run full pipeline (programs + requirements) for the last N bulletin years.
     * Returns all ProgramCatalogVersion rows that were upserted.
     */
    public List<ProgramCatalogVersion> runFullPipelineForLastNBulletins(int n) {
        List<ProgramCatalogVersion> saved = new ArrayList<>();

        List<String> roots = bulletinYearService.getLastNBulletins(n);
        for (String root : roots) {
            log.info("Starting full pipeline for bulletin root {}", root);
            saved.addAll(runFullPipelineForRoot(root));
        }

        log.info("Full pipeline complete: {} ProgramCatalogVersion rows processed", saved.size());
        return saved;
    }

    /**
     * Run full pipeline only for bulletin years that are not yet in the scrape log.
     * Returns all ProgramCatalogVersion rows that were upserted.
     */
    public List<ProgramCatalogVersion> runFullPipelineForNewBulletins(int n) {
        List<ProgramCatalogVersion> saved = new ArrayList<>();

        record BulletinTarget(String root, String label) {}

        List<String> roots = bulletinYearService.getLastNBulletins(n);
        List<BulletinTarget> targets = new ArrayList<>();

        for (String root : roots) {
            String label = bulletinYearService.resolveCatalogLabel(root);
            if (label == null || label.isBlank()) {
                log.warn("Unable to resolve bulletin label for {}. Skipping scrape check.", root);
                continue;
            }
            if (bulletinScrapeLogRepository.existsByCatalogLabel(label)) {
                log.info("Bulletin {} already scraped. Skipping.", label);
                continue;
            }
            targets.add(new BulletinTarget(root, label));
        }

        if (targets.isEmpty()) {
            log.info("All bulletins already scraped. Nothing to do.");
            return saved;
        }

        for (BulletinTarget target : targets) {
            log.info("🆕 New bulletin {} detected. Running full pipeline for {}", target.label(), target.root());
            saved.addAll(runFullPipelineForRoot(target.root()));
            bulletinScrapeLogRepository.save(new BulletinScrapeLog(
                    null,
                    target.label(),
                    parseCatalogYear(target.label()),
                    target.root(),
                    LocalDateTime.now()
            ));
        }

        log.info("Full pipeline complete: {} ProgramCatalogVersion rows processed", saved.size());
        return saved;
    }

    /**
     * Run full pipeline (programs + requirements) for a single bulletin root URL.
     */
    public List<ProgramCatalogVersion> runFullPipelineForRoot(String bulletinRoot) {
        List<ProgramCatalogVersion> saved = new ArrayList<>();

        List<CollegeLink> colleges = collegeDiscoveryService.getColleges(bulletinRoot);
        log.info("Found {} colleges/divisions under {}", colleges.size(), bulletinRoot);

        for (CollegeLink college : colleges) {
            try {
                // simple throttling
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }

                log.info("Fetching college page: {} ({})", college.name(), college.url());
                Document doc = Jsoup.connect(college.url())
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/121.0.0.0 Safari/537.36")
                        .timeout(10_000)
                        .get();

                List<ProgramMeta> metas = collegePageParser.parsePrograms(bulletinRoot, college, doc);
                log.info("Parsed {} programs from {}", metas.size(), college.name());

                for (ProgramMeta meta : metas) {
                    // Phase 2: upsert Program + ProgramCatalogVersion
                    ProgramCatalogVersion pcv = programPersistenceService.upsertProgram(meta);
                    if (pcv == null) {
                        continue;
                    }
                    saved.add(pcv);

                    // Phase 3: requirements for this program
                    try {
                        requirementScraper.scrapeAndPersistRequirements(meta);
                    } catch (Exception ex) {
                        log.error("Error processing program '{}' ({}), college '{}': {}",
                                meta.getProgramName(),
                                meta.getProgramUrl(),
                                college.name(),
                                ex.getMessage(),
                                ex);
                        throw ex; // rethrow so the college-level catch logs too
                    }
                }

            } catch (Exception ex) {
                log.error("Failed to run pipeline for college {} at {}: {}",
                        college.name(), college.url(), ex.getMessage());
            }
        }

        return saved;
    }

    private Integer parseCatalogYear(String label) {
        if (label == null) return null;
        String[] parts = label.split("-");
        if (parts.length == 0) return null;
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
