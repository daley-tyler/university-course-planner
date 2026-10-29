package com.courseplanner.api.Bulletin.Requirements;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ProgramPolicyExtractor {

    private static final Pattern GRADE_LETTER_PATTERN = Pattern.compile("grade of\\s+\"?([A-F][+-]?)\"?\\s+or\\s+higher", Pattern.CASE_INSENSITIVE);
    private static final Pattern COURSE_CODE_PATTERN = Pattern.compile("([A-Z]{2,4})\\s*(\\d{3}[A-Z]?)");

    public PolicyExtractionResult extract(Document doc, String sourceUrl) {
        Element container = doc.selectFirst("#textcontainer");
        if (container == null) {
            return PolicyExtractionResult.builder()
                    .sourceUrl(sourceUrl)
                    .build();
        }

        Elements paragraphs = container.select("p, li");
        List<String> relevantTexts = new ArrayList<>();
        String gradeMinText = null;
        List<String> gradeMinCourses = new ArrayList<>();

        for (Element p : paragraphs) {
            if (p.closest("table.sc_courselist") != null) continue;
            if (p.closest("dl.sc_footnotes") != null) continue;
            String text = normalize(p.text());
            if (text.isEmpty()) continue;

            boolean keyword = containsKeyword(text);
            Matcher gradeM = GRADE_LETTER_PATTERN.matcher(text);
            if (gradeM.find()) {
                gradeMinText = text;
                keyword = true;
                gradeMinCourses.addAll(extractCourses(text));
            }

            if (keyword) {
                relevantTexts.add(text);
            }
        }

        String notes = relevantTexts.isEmpty() ? null : String.join("\n", relevantTexts);

        return PolicyExtractionResult.builder()
                .gradeMinText(gradeMinText)
                .gradeMinCourses(gradeMinCourses.isEmpty() ? null : gradeMinCourses)
                .notes(notes)
                .sourceUrl(sourceUrl)
                .build();
    }

    private List<String> extractCourses(String text) {
        Matcher m = COURSE_CODE_PATTERN.matcher(text);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group(1) + " " + m.group(2));
        }
        return out;
    }

    private boolean containsKeyword(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("grade") ||
                lower.contains("admission") ||
                lower.contains("progress") ||
                lower.contains("cohort") ||
                lower.contains("clinical") ||
                lower.contains("internship") ||
                lower.contains("practicum") ||
                lower.contains("portfolio");
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
