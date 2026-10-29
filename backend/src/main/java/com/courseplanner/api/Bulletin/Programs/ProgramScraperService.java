package com.courseplanner.api.Bulletin.Programs;

import com.courseplanner.api.Bulletin.dto.CollegeLink;
import com.courseplanner.api.Bulletin.dto.ProgramMeta;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramScraperService {

    private final BulletinYearService bulletinYearService;
    private final CollegeDiscoveryService collegeDiscoveryService;
    private final CollegePageParser collegePageParser;
    private final ProgramPersistenceService programPersistenceService;

    /**
     * Scrape + persist programs for the last N bulletin years.
     * Returns the list of ProgramCatalogVersion records that were created/updated.
     */
    public List<ProgramCatalogVersion> scrapeAndStoreForLastNBulletins(int n) {
        List<ProgramCatalogVersion> saved = new ArrayList<>();

        List<String> roots = bulletinYearService.getLastNBulletins(n);
        for (String root : roots) {
            log.info("Scraping programs for bulletin root {}", root);
            saved.addAll(scrapeAndStoreForRoot(root));
        }

        log.info("Phase 2 complete: {} ProgramCatalogVersion rows upserted", saved.size());
        return saved;
    }

    public List<ProgramCatalogVersion> scrapeAndStoreForRoot(String bulletinRoot) {
        List<ProgramCatalogVersion> saved = new ArrayList<>();

        List<CollegeLink> colleges = collegeDiscoveryService.getColleges(bulletinRoot);
        log.info("Found {} colleges/divisions under {}", colleges.size(), bulletinRoot);

        for (CollegeLink college : colleges) {
            try {
                // Polite throttling so we don't hammer the site
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }

                log.info("🌐 Fetching college page: {} ({})", college.name(), college.url());
                Document doc = Jsoup.connect(college.url())
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/121.0.0.0 Safari/537.36")
                        .timeout(10_000)
                        .get();

                List<ProgramMeta> metas = collegePageParser.parsePrograms(bulletinRoot, college, doc);
                log.info("   ↳ Parsed {} programs from {}", metas.size(), college.name());

                for (ProgramMeta meta : metas) {
                    try {
                        ProgramCatalogVersion pcv = programPersistenceService.upsertProgram(meta);
                        if (pcv != null) {
                            saved.add(pcv);
                        }
                    } catch (Exception ex) {
                        // Only error logging for programs
                        log.error("Error processing program '{}' ({}), college '{}': {}",
                                meta.getProgramName(),
                                meta.getProgramUrl(),
                                college.name(),
                                ex.getMessage(),
                                ex);
                        throw ex; // rethrow so scraper stops and stack trace prints
                    }
                }

            } catch (Exception ex) {
                log.error("Failed to scrape college {} at {}: {}",
                        college.name(), college.url(), ex.getMessage(), ex);
            }
        }

        return saved;
    }
}
