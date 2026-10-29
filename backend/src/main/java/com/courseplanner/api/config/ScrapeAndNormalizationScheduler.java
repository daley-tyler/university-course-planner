package com.courseplanner.api.config;

import com.courseplanner.api.Courses.scraper.CourseListScraper;
import lombok.extern.slf4j.Slf4j;
import com.courseplanner.api.Courses.normalizer.CourseNormalizer;
import com.courseplanner.api.Bulletin.BulletinScraperService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@EnableAsync
@Profile("!test & !ci")
@ConditionalOnProperty(name = "app.scraping.enabled", havingValue = "true")
public class ScrapeAndNormalizationScheduler {

    private final CourseListScraper scraper;
    private final CourseNormalizer normalizer;
    private final BulletinScraperService bulletinScraperService;

    public ScrapeAndNormalizationScheduler(CourseListScraper scraper,
                                           CourseNormalizer normalizer,
                                           BulletinScraperService bulletinScraperService) {
        this.scraper = scraper;
        this.normalizer = normalizer;
        this.bulletinScraperService = bulletinScraperService;
    }

    /* ------------------------------------------------------------- */
    /* RUN ONCE AT APPLICATION STARTUP                               */
    /* ------------------------------------------------------------- */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        log.info("Starting initial scrape + normalization...");
        runFullScrapeAndNormalize();
    }

    /* ------------------------------------------------------------- */
    /* RUN NIGHTLY (2:00 AM example)                                 */
    /* ------------------------------------------------------------- */
    @Scheduled(cron = "0 0 2 * * *") // every night at 2 AM
    public void nightlyScrapeAndNormalize() {
        log.info("Running nightly scrape + normalization...");
        runFullScrapeAndNormalize();
    }

    /* ------------------------------------------------------------- */
    /* SHARED WORKFLOW                                               */
    /* ------------------------------------------------------------- */
    private void runFullScrapeAndNormalize() {
        try {
            scraper.scrapeRecentTerms(); // scrapes new courses and inserts into DB
            int normalizedCount = normalizer.normalizeAll(); // processes unnormalized courses
            log.info("Normalization complete: {} courses processed.", normalizedCount);
            bulletinScraperService.runFullPipelineForNewBulletins(5);
        } catch (Exception e) {
            log.error("Error during scrape/normalize pipeline: {}", e.getMessage(), e);
        }
    }
}
