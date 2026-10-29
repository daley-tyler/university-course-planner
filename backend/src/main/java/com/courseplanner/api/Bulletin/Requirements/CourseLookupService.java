package com.courseplanner.api.Bulletin.Requirements;

import com.courseplanner.api.Bulletin.dto.ParsedReqNodeCourseOption;
import com.courseplanner.api.model.entities.catalog.Course;
import com.courseplanner.api.model.repositories.CourseRepository;
import com.courseplanner.api.model.repositories.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseLookupService {

    private final CourseRepository courseRepo;
    private final SubjectRepository subjectRepo;

    /** Explicit (SUBJ NUM) pairs anywhere, e.g., "CSE 174", "BIO 485A" */
    private static final Pattern SIMPLE_CODE_PATTERN =
            Pattern.compile("([A-Z]{2,4})\\s*(\\d{3}[A-Z]?)");

    /** Same subject, multiple numbers: "ACC 221/333/453" or "CSE 174, 271 or 274" */
    private static final Pattern SAME_SUBJ_MULTI_NUMS =
            Pattern.compile("^([A-Z]{2,4})\\s+((?:\\d{3}[A-Z]?)(?:\\s*(?:/|,|or)\\s*\\d{3}[A-Z]?)+)$");

    /**
     * Normalizes common unicode/separator quirks from the bulletin:
     *  - NBSP → space
     *  - en/em dash → hyphen
     *  - division slash → slash
     *  - collapse multiple spaces
     */
    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace('\u00A0', ' ')
                .replace('\u2013', '-')  // en dash
                .replace('\u2014', '-')  // em dash
                .replace('\u2215', '/')  // division slash
                .replaceAll("\\s+", " ")
                .trim();
    }

    public Optional<Course> findCourse(String subjectCode, String number) {
        return courseRepo.findBySubject_CodeAndNumber(subjectCode, number);
    }

    /**
     * Extracts all (SUBJ, NUM) pairs from text like:
     *  - "CSE 174"
     *  - "CSE 456/BIO 485/MBI 485"
     *  - "CSE/BIO/CHM/MBI 466"
     *  - "ACC 221/333/453"
     *  - "CSE 174, 271 or 274"
     */
    public List<ParsedReqNodeCourseOption> extractCourseCodes(String raw) {
        String text = normalize(raw);

        // Strip leading "or " defensively (rows that carry OR from previous line)
        if (text.toLowerCase(Locale.ROOT).startsWith("or ")) {
            text = text.substring(3).trim();
        }

        // Use LinkedHashSet to preserve encounter order and avoid duplicates.
        Set<String> seen = new LinkedHashSet<>();
        List<ParsedReqNodeCourseOption> out = new ArrayList<>();

        // ---------- Case A: Same subject with multiple numbers (e.g., "ACC 221/333/453", "CSE 174, 271 or 274")
        Matcher sameSubj = SAME_SUBJ_MULTI_NUMS.matcher(text);
        if (sameSubj.find()) {
            String subj = sameSubj.group(1);
            String tail = sameSubj.group(2);
            for (String num : tail.split("\\s*(?:/|,|\\bor\\b)\\s*")) {
                if (num.isBlank()) continue;
                String key = subj + " " + num;
                if (seen.add(key)) {
                    out.add(ParsedReqNodeCourseOption.builder()
                            .subjectCode(subj)
                            .courseNumber(num)
                            .build());
                }
            }
            if (!out.isEmpty()) return out;
        }

        // ---------- Case B: Multiple subjects share one number (e.g., "CSE/BIO/CHM/MBI 466")
        if (text.contains("/") && text.matches("^[A-Z/]+\\s+\\d{3}[A-Z]?$")) {
            String[] parts = text.split("\\s+");
            if (parts.length >= 2) {
                String subjectsPart = parts[0];
                String number = parts[1];
                for (String subj : subjectsPart.split("/")) {
                    subj = subj.trim();
                    if (!subj.isEmpty()) {
                        String key = subj + " " + number;
                        if (seen.add(key)) {
                            out.add(ParsedReqNodeCourseOption.builder()
                                    .subjectCode(subj)
                                    .courseNumber(number)
                                    .build());
                        }
                    }
                }
                if (!out.isEmpty()) return out;
            }
        }

        // ---------- Case C: Fallback — pick up any explicit (SUBJ NUM) pairs anywhere
        Matcher m = SIMPLE_CODE_PATTERN.matcher(text);
        while (m.find()) {
            String subj = m.group(1);
            String num  = m.group(2);
            String key  = subj + " " + num;
            if (seen.add(key)) {
                out.add(ParsedReqNodeCourseOption.builder()
                        .subjectCode(subj)
                        .courseNumber(num)
                        .build());
            }
        }

        return out;
    }

    public Course createGhostCourse(String subject, String number) {
        var subj = subjectRepo.findByCode(subject)
                .orElseThrow(() -> new IllegalStateException("Subject " + subject + " not found in DB"));

        Course c = new Course();
        c.setSubject(subj);
        c.setNumber(number);
        c.setTitle("[Missing] " + subject + " " + number);
        c.setDescription("This course is referenced in program requirements but is not present in the Course List bulletin.");
        c.setCredits(BigDecimal.ZERO);
        c.setNormalized(false);

        return courseRepo.save(c);
    }
}
