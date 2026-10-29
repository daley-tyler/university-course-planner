package com.courseplanner.api.Courses.normalizer;

import com.courseplanner.api.model.entities.catalog.*;
import com.courseplanner.api.model.entities.enums.PrereqKind;
import com.courseplanner.api.model.entities.enums.RestrictionKind;
import com.courseplanner.api.model.entities.enums.RestrictionMode;
import com.courseplanner.api.model.entities.prereq.*;
import com.courseplanner.api.model.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CourseNormalizer {

    private final CourseRepository courseRepo;
    private final SubjectRepository subjectRepo;
    private final AttributeRepository attributeRepo;
    private final CourseAttributeRepository courseAttrRepo;
    private final CoursePrerequisiteGroupRepository prereqGroupRepo;
    private final CoursePrerequisiteCourseRepository prereqCourseRepo;
    private final CourseRestrictionRepository restrictionRepo;

    private static final Pattern COURSE_TOKEN = Pattern.compile("\\b([A-Z]{2,4})\\s*-?\\s*(\\d{3,4}[A-Z]?)\\b");
    private static final Pattern ATTR_WITH_CODE = Pattern.compile("\\(([^)]+)\\)\\s*$");
    private static final Pattern RESTR_LINE = Pattern.compile(
            "^(?:This section is\\s+)?(ONLY|EXCEPT)\\s+open\\s+for\\s+the\\s+following\\s+([A-Za-z]+):\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    /* -------------------------------------------------------------------------- */
    /* PUBLIC ENTRYPOINTS                                                        */
    /* -------------------------------------------------------------------------- */

    @Transactional
    public int normalizeAll() {
        List<Course> pending = courseRepo.findByNormalizedFalse();
        int count = 0;
        for (Course c : pending) {
            normalizeCourse(c);
            count++;
        }
        return count;
    }

    @Transactional
    public void normalizeCourse(Course course) {
        // Clear old data
        prereqCourseRepo.deleteByCourseId(course.getId());
        prereqGroupRepo.deleteByCourseId(course.getId());
        courseAttrRepo.deleteByCourseId(course.getId());
        restrictionRepo.deleteByCourseId(course.getId());

        /* ---- PREREQS / COREQS ---- */
        parseReq(course, course.getPrereqText(), PrereqKind.PREREQUISITE);
        parseReq(course, course.getCoreqText(), PrereqKind.COREQUISITE);

        /* ---- ATTRIBUTES ---- */
        parseAttributes(course, course.getAttributeText());

        /* ---- RESTRICTIONS ---- */
        parseRestrictions(course, course.getRestrictionText());

        course.setNormalized(true);
        courseRepo.save(course);
    }

    /* -------------------------------------------------------------------------- */
    /* PREREQUISITE / COREQUISITE PARSER                                         */
    /* -------------------------------------------------------------------------- */

    private void parseReq(Course host, String raw, PrereqKind kind) {
        if (raw == null || raw.isBlank()) return;
        String clean = stripHtml(raw);
        if (clean.isBlank()) return;

        List<List<String>> dnf = BooleanDNF.toDNF(clean);
        if (dnf.isEmpty()) {
            List<Course> refs = extractCourses(clean);
            if (!refs.isEmpty()) {
                var g = saveGroup(host, kind, "AND", clean);
                linkCourses(g, refs);
            } else saveGroup(host, kind, "AND", clean);
            return;
        }
        for (List<String> clause : dnf) {
            var g = saveGroup(host, kind, "AND", String.join(" AND ", clause));
            linkCourses(g, extractCourses(String.join(" ", clause)));
        }
    }

    private CoursePrerequisiteGroup saveGroup(Course host, PrereqKind kind, String op, String desc) {
        var g = new CoursePrerequisiteGroup();
        g.setCourse(host);
        g.setKind(kind);
        g.setOperator(op);
        g.setDescription(truncate(desc, 800));
        return prereqGroupRepo.save(g);
    }

    private void linkCourses(CoursePrerequisiteGroup g, List<Course> reqs) {
        for (Course r : reqs) {
            var link = new CoursePrerequisiteCourse();
            link.setGroup(g);
            link.setRequiredCourse(r);
            link.setConcurrentAllowed(false);
            prereqCourseRepo.save(link);
        }
    }

    private List<Course> extractCourses(String s) {
        List<Course> out = new ArrayList<>();
        Matcher m = COURSE_TOKEN.matcher(s);
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            String subj = m.group(1);
            String num = m.group(2);
            String key = subj + "-" + num;
            if (!seen.add(key)) continue;
            subjectRepo.findByCode(subj)
                    .flatMap(su -> courseRepo.findBySubjectAndNumber(su, num))
                    .ifPresent(out::add);
        }
        return out;
    }

    /* -------------------------------------------------------------------------- */
    /* ATTRIBUTE PARSER                                                          */
    /* -------------------------------------------------------------------------- */

    private void parseAttributes(Course course, String raw) {
        if (raw == null || raw.isBlank()) return;
        String text = replaceBr(raw);
        String[] parts = text.split(";|\\r?\\n");
        Set<Integer> added = new HashSet<>();

        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;

            Matcher m = ATTR_WITH_CODE.matcher(p);
            String code = null;
            if (m.find()) code = m.group(1).trim();
            else {
                String[] toks = p.split("\\s+");
                for (int i = toks.length - 1; i >= 0; i--) {
                    String t = toks[i].replaceAll("[^A-Z0-9]", "");
                    if (t.matches("[A-Z0-9]{2,6}")) { code = t; break; }
                }
            }

            String name = p.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
            String resolved = (code == null || code.isEmpty()) ? slug(name) : code;

            // capture as final variables for lambda
            final String finalCode = resolved.toUpperCase(Locale.ROOT);
            final String finalName = name;
            final String finalCategory = category(name, resolved);

            Attribute attr = attributeRepo.findByCodeIgnoreCase(finalCode)
                    .orElseGet(() -> attributeRepo.save(
                            Attribute.builder()
                                    .code(finalCode)
                                    .name(finalName)
                                    .category(finalCategory)
                                    .build()
                    ));

            if (added.add(attr.getId())) {
                courseAttrRepo.linkIfAbsent(course.getId(), attr.getId());
            }
        }
    }

    private static String category(String name, String code) {
        String n = (name + " " + code).toLowerCase(Locale.ROOT);
        if (n.contains("foundation") || n.startsWith("fnd")) return "Foundation";
        if (n.contains("general education") || n.contains("liberal education")) return "General Education";
        if (n.contains("pa ") || n.contains("pa-") || n.contains("pa3") || n.contains("pa4")) return "Perspective Area";
        if (n.contains("intercultural")) return "Intercultural Perspectives";
        if (n.contains("ohio transfer") || n.contains("(tm)") || n.contains("ot36")) return "Ohio Transfer 36";
        return "Other";
    }

    /* -------------------------------------------------------------------------- */
    /* RESTRICTIONS PARSER                                                       */
    /* -------------------------------------------------------------------------- */

    private void parseRestrictions(Course course, String raw) {
        if (raw == null || raw.isBlank()) return;

        for (String line : raw.split("\\r?\\n+")) {
            String t = Jsoup.parse(line).text().trim();
            if (t.isEmpty()) continue;
            Matcher m = RESTR_LINE.matcher(t);
            if (!m.find()) continue;

            String modeStr = m.group(1).toUpperCase(Locale.ROOT);
            String typeStr = m.group(2).toUpperCase(Locale.ROOT);
            String values = m.group(3);

            RestrictionMode mode = RestrictionMode.valueOf(modeStr);
            RestrictionKind type = normalizeKind(typeStr);

            for (String v : values.split(",")) {
                String val = v.trim();
                if (val.isEmpty()) continue;

                String normalizedVal = val.replaceAll("\\s+", " ").trim();

                boolean exists = restrictionRepo.existsByCourseIdAndRestrictionTypeAndModeAndValue(
                        course.getId(),
                        type,          // RestrictionKind enum
                        mode,          // RestrictionMode enum
                        normalizedVal  // String
                );

                if (!exists) {
                    CourseRestriction r = CourseRestriction.builder()
                            .course(course)
                            .mode(mode)
                            .restrictionType(type)
                            .value(normalizedVal)
                            .build();
                    restrictionRepo.save(r);
                }
            }
        }
    }

    /* -------------------------------------------------------------------------- */
    /* RESTRICTION KIND NORMALIZER                                               */
    /* -------------------------------------------------------------------------- */

    private RestrictionKind normalizeKind(String s) {
        String t = s.trim().toUpperCase(Locale.ROOT);
        try {
            return RestrictionKind.valueOf(t);
        } catch (Exception e) {
            if (t.startsWith("MAJ")) return RestrictionKind.MAJOR;
            if (t.startsWith("CAMP")) return RestrictionKind.CAMPUS;
            if (t.startsWith("COLL")) return RestrictionKind.COLLEGE;
            if (t.startsWith("LEV")) return RestrictionKind.LEVEL;
            if (t.startsWith("DEG")) return RestrictionKind.DEGREE;
            if (t.startsWith("PROG")) return RestrictionKind.PROGRAM;
            if (t.startsWith("DEPT")) return RestrictionKind.DEPARTMENT;
            if (t.contains("HONOR") || t.startsWith("COH")) return RestrictionKind.COHORT;
            return RestrictionKind.ATTRIBUTE;
        }
    }

    /* -------------------------------------------------------------------------- */
    /* UTILITIES                                                                 */
    /* -------------------------------------------------------------------------- */

    private static String stripHtml(String s) {
        if (s == null) return "";
        String t = Jsoup.parse(s).text().replace('\u00A0', ' ');
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private static String replaceBr(String s) {
        if (s == null) return "";
        String html = Jsoup.parse(s).body().html();
        html = html.replaceAll("(?i)<br\\s*/?>", "; ");
        return Jsoup.parse(html).text();
    }

    private static String slug(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replaceAll("[^A-Za-z0-9]+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s.length() > 24 ? s.substring(0, 24) : s.toUpperCase(Locale.ROOT);
    }

    private static String truncate(String s, int len) {
        return s == null ? null : s.length() > len ? s.substring(0, len) : s;
    }

    /* -------------------------------------------------------------------------- */
    /* Embedded Tiny Boolean Parser → DNF                                         */
    /* -------------------------------------------------------------------------- */

    private static class BooleanDNF {
        static List<List<String>> toDNF(String expr) {
            if (expr == null || expr.isBlank()) return List.of();
            String clean = Jsoup.parse(expr.replaceAll("(?i)<.?i>|<.?b>", " ")).text();
            clean = clean.replaceAll("(?i)\\band\\b", " AND ");
            clean = clean.replaceAll("(?i)\\bor\\b", " OR ");
            clean = clean.replaceAll("\\s+", " ").trim();
            if (!clean.contains("AND") && !clean.contains("OR")) return List.of();

            List<List<String>> dnf = new ArrayList<>();
            for (String orClause : clean.split("(?i)\\bOR\\b")) {
                List<String> ands = new ArrayList<>();
                for (String part : orClause.split("(?i)\\bAND\\b")) {
                    String p = part.replaceAll("[()]", "").trim();
                    if (!p.isEmpty()) ands.add(p);
                }
                if (!ands.isEmpty()) dnf.add(ands);
            }
            return dnf;
        }
    }
}
