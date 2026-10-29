package com.courseplanner.api.Courses.scraper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.courseplanner.api.model.entities.catalog.Course;
import com.courseplanner.api.model.entities.catalog.Subject;
import com.courseplanner.api.model.entities.scrapeLog.ScrapeLog;
import com.courseplanner.api.model.repositories.CourseRepository;
import com.courseplanner.api.model.repositories.SubjectRepository;
import com.courseplanner.api.model.repositories.ScrapeLogRepository;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CourseListScraper {

    private final CourseRepository courseRepository;
    private final SubjectRepository subjectRepository;
    private final ScrapeLogRepository scrapeLogRepository;
    private final Gson gson = new Gson();

    @Value("${course.catalog.base-url:https://catalog.example.edu/courselist/}")
    private String courseCatalogBaseUrl = "https://catalog.example.edu/courselist/";

    public CourseListScraper(CourseRepository courseRepository,
                             SubjectRepository subjectRepository,
                             ScrapeLogRepository scrapeLogRepository) {
        this.courseRepository = courseRepository;
        this.subjectRepository = subjectRepository;
        this.scrapeLogRepository = scrapeLogRepository;
    }

    public void scrapeRecentTerms() throws IOException {
        log.info("Starting course catalog scrape...");

        // Step 1: Load form page for token and metadata
        Connection.Response initial = Jsoup.connect(courseCatalogUrl())
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .method(Connection.Method.GET)
                .execute();

        Document formPage = initial.parse();
        String token = formPage.select("input[name=_token]").val();
        if (token.isBlank()) throw new IllegalStateException("Failed to fetch CSRF token.");

        // Step 2: Determine recent terms (limit 20)
        List<String> allTerms = formPage.select("select[name=term] option[value]")
                .stream()
                .map(opt -> opt.attr("value").trim())
                .filter(v -> !v.isBlank())
                .limit(20)
                .toList();

        List<String> termsToScrape = allTerms.stream()
                .filter(term -> !scrapeLogRepository.existsByTerm(term))
                .toList();

        if (termsToScrape.isEmpty()) {
            log.info("All recent terms already scraped. Nothing to do.");
            return;
        }

        // Step 3: Sync subjects
        List<Subject> subjects = formPage.select("select[name=subject[]] option").stream()
                .filter(opt -> !opt.attr("value").isBlank())
                .map(opt -> {
                    String code = opt.attr("value").trim();
                    String name = opt.text().trim();
                    return subjectRepository.findByCode(code)
                            .map(existing -> {
                                if (!Objects.equals(existing.getName(), name)) {
                                    existing.setName(name);
                                    subjectRepository.save(existing);
                                }
                                return existing;
                            })
                            .orElseGet(() -> {
                                Subject s = new Subject();
                                s.setCode(code);
                                s.setName(name);
                                return subjectRepository.save(s);
                            });
                })
                .collect(Collectors.toList());

        log.info("Loaded {} subjects", subjects.size());

        // Step 4: Track existing course (subject + number)
        Set<String> existingKeys = new HashSet<>();
        courseRepository.findAll().forEach(c ->
                existingKeys.add(c.getSubject().getCode() + "-" + c.getNumber())
        );

        // Step 5: Loop through unscripted terms
        for (String term : termsToScrape) {
            log.info("========== TERM {} ==========", term);
            int subjectIndex = 0;

            for (Subject subject : subjects) {
                String subjCode = subject.getCode();
                subjectIndex++;
                log.debug("[{}/{}] Scraping subject: {}", subjectIndex, subjects.size(), subjCode);

                // Refresh token per subject
                Connection.Response refresh = Jsoup.connect(courseCatalogUrl())
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .method(Connection.Method.GET)
                        .execute();
                token = refresh.parse().select("input[name=_token]").val();
                Map<String, String> cookies = refresh.cookies();

                try {
                    // Build POST request
                    Connection.Response response = Jsoup.connect(courseCatalogUrl())
                            .cookies(cookies)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .referrer(courseCatalogUrl())
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .data("_token", token)
                            .data("term", term)
                            .data("campusFilter[]", "All")
                            .data("subject[]", subjCode)
                            .data("courseNumber", "")
                            .data("openWaitlist", "")
                            .data("crnNumber", "")
                            .data("level", "")
                            .data("courseTitle", "")
                            .data("instructor", "")
                            .data("instructorUid", "")
                            .data("creditHours", "")
                            .data("startEndTime[]", "")
                            .data("startEndTime[]", "")
                            .data("courseSearch", "")
                            .method(Connection.Method.POST)
                            .ignoreHttpErrors(true)
                            .timeout(30_000)
                            .execute();

                    if (response.statusCode() != 200) {
                        log.error("HTTP {} for subject {}", response.statusCode(), subjCode);
                        Files.writeString(Path.of("debug_" + subjCode + ".html"), response.body());
                        continue;
                    }

                    Document doc = response.parse();
                    Elements rows = doc.select("table#resultsTable tbody tr");
                    if (rows.isEmpty()) continue;

                    List<Course> batch = new ArrayList<>();

                    for (Element row : rows) {
                        Elements cols = row.select("td");
                        if (cols.size() < 7) continue;

                        String number = cols.get(1).text().trim();
                        String title = cols.get(2).text().trim();
                        String creditStr = cols.get(6).text().trim();

                        Element detailsLink = row.selectFirst("a.detailLink");
                        String crn = detailsLink != null ? detailsLink.attr("data-crn") : null;

                        if (number.isEmpty() || title.isEmpty() || crn == null) continue;

                        String key = subjCode + "-" + number;
                        if (existingKeys.contains(key)) continue; // skip duplicates

                        BigDecimal credits;
                        try {
                            credits = new BigDecimal(creditStr);
                        } catch (NumberFormatException e) {
                            credits = BigDecimal.ONE;
                        }

                        // Fetch and clean course description (Gson)
                        // Fetch and clean course description + prereqs (Gson)
                        String description = "";
                        String prereqText = "";
                        String coreqText = "";
                        String restrictions = "";
                        String attributes = "";
                        try {
                            Connection.Response jsonResp = Jsoup.connect(
                                            sectionDetailUrl(term, crn))
                                    .ignoreContentType(true)
                                    .method(Connection.Method.GET)
                                    .timeout(10_000)
                                    .execute();

                            JsonObject json = gson.fromJson(jsonResp.body(), JsonObject.class);

                            // --- Description ---
                            if (json.has("courseDescription") && !json.get("courseDescription").isJsonNull()) {
                                String rawDesc = json.get("courseDescription").getAsString().trim();
                                String[] parts = rawDesc.split("\n", 2);
                                if (parts.length > 1) description = parts[1].trim();
                                else description = rawDesc;
                            }

                            // --- Details (raw text) ---
                            if (json.has("prerequisiteString") && !json.get("prerequisiteString").isJsonNull()) {
                                prereqText = json.get("prerequisiteString").getAsString().trim();
                            }

                            if (json.has("corequisiteString") && !json.get("corequisiteString").isJsonNull()) {
                                coreqText = json.get("corequisiteString").getAsString().trim();
                            }

                            if (json.has("attributes") && !json.get("attributes").isJsonNull()) {
                                attributes = json.get("attributes").getAsString().trim();
                            }

                            // --- Restrictions ---
                            if (json.has("restrictions") && json.get("restrictions").getAsString().equalsIgnoreCase("True")) {
                                StringBuilder restr = new StringBuilder();
                                String codeI = json.has("restrictionCodeI") ? json.get("restrictionCodeI").getAsString() : "Only";
                                String codeE = json.has("restrictionCodeE") ? json.get("restrictionCodeE").getAsString() : "Except";

                                // helper for nested restriction objects
                                for (String field : List.of(
                                        "DepartmentRestrictions", "CohortRestrictions", "AttributeRestrictions",
                                        "ClassRestrictions", "CampusRestrictions", "CollegeRestrictions",
                                        "DegreeRestrictions", "LevelRestrictions", "MajorRestrictions", "ProgramRestrictions"
                                )) {
                                    if (json.has(field) && !json.get(field).isJsonNull()) {
                                        JsonObject obj = json.getAsJsonObject(field);
                                        if (obj.has("values") && obj.get("values").isJsonArray()) {
                                            String mode = obj.get("code").getAsString().equals("I") ? codeI : codeE;
                                            List<String> values = new ArrayList<>();
                                            obj.get("values").getAsJsonArray().forEach(v -> values.add(v.getAsString()));
                                            restr.append(String.format("%s %s: %s%n",
                                                    mode, field.replace("Restrictions", "").toLowerCase(),
                                                    String.join(", ", values)));
                                        }
                                    }
                                }
                                restrictions = restr.toString().trim();
                            }


                        } catch (Exception ignored) { }


                        Course c = new Course();
                        c.setSubject(subject);
                        c.setNumber(number);
                        c.setTitle(title);
                        c.setCredits(credits);
                        c.setDescription(description);
                        c.setPrereqText(prereqText);
                        c.setCoreqText(coreqText);
                        c.setAttributeText(attributes);
                        c.setRestrictionText(restrictions);
                        c.setNormalized(false);


                        batch.add(c);
                        existingKeys.add(key);
                    }

                    if (!batch.isEmpty()) {
                        courseRepository.saveAll(batch);
                        log.info("{} -> {} new courses", subjCode, batch.size());
                    }

                    Thread.sleep(400);

                } catch (Exception e) {
                    log.error("Error scraping subject {}: {}", subjCode, e.getMessage(), e);
                }
            }

            scrapeLogRepository.save(new ScrapeLog(null, term, LocalDateTime.now()));
            log.info("Completed term {}", term);
        }

        log.info("Scraping complete for all pending terms.");
    }

    private String courseCatalogUrl() {
        return courseCatalogBaseUrl.endsWith("/") ? courseCatalogBaseUrl : courseCatalogBaseUrl + "/";
    }

    private String sectionDetailUrl(String term, String crn) {
        return courseCatalogUrl() + "sectionDetail/" + term + "/" + crn;
    }
}
