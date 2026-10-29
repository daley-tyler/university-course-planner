package com.courseplanner.api.restAPIControllers;

import com.courseplanner.api.Courses.normalizer.CourseNormalizer;
import com.courseplanner.api.Courses.scraper.CourseListScraper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseListScraper courseListScraper;
    private final CourseNormalizer courseNormalizer;

    public CourseController(CourseListScraper courseListScraper, CourseNormalizer courseNormalizer) {
        this.courseListScraper = courseListScraper;
        this.courseNormalizer = courseNormalizer;
    }

    @PostMapping("/scrape")
    public String scrapeCourses() {
        try {
            log.info("Starting course catalog scrape...");
            courseListScraper.scrapeRecentTerms();
            log.info("Scraping completed successfully.");
            return "Scraped successfully.";
        } catch (Exception e) {
            log.error("Error during scraping process", e);
            return "Error: " + e.getMessage();
        }
    }

    @PostMapping("/normalize")
    public String normalizeCourses() {
        try {
            log.info("Starting normalization of all unprocessed courses...");
            int count = courseNormalizer.normalizeAll();
            log.info("Normalization complete for {} courses.", count);
            return "Normalized " + count + " courses.";
        } catch (Exception e) {
            log.error("Error during normalization process", e);
            return "Error during normalization: " + e.getMessage();
        }
    }
}
