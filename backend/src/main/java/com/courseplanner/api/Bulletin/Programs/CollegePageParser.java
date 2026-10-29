package com.courseplanner.api.Bulletin.Programs;

import com.courseplanner.api.Bulletin.dto.CollegeLink;
import com.courseplanner.api.Bulletin.dto.ProgramMeta;
import com.courseplanner.api.model.entities.enums.ProgramKind;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CollegePageParser {

    public List<ProgramMeta> parsePrograms(String catalogRoot, CollegeLink college, Document doc) {
        List<ProgramMeta> result = new ArrayList<>();

        /* ----------------------------------------------------
         * 1) PRIMARY FORMAT:
         *    #degreesandmajorstextcontainer OR #degreesandprogramstextcontainer
         * ---------------------------------------------------- */
        Element container = doc.selectFirst("#degreesandmajorstextcontainer");
        if (container == null) {
            container = doc.selectFirst("#degreesandprogramstextcontainer");
        }

        if (container != null) {
            log.info("Using standard degree container for {}", college.name());
            extractFromContainer(result, catalogRoot, college, container);
            return result;
        }

        /* ----------------------------------------------------
         * 2) FALLBACK FORMAT:
         *    Creative Arts + Education:
         *    <h2>Degrees and Majors Offered</h2>
         *    <h3>...</h3>
         *    <ul>...</ul>
         * ---------------------------------------------------- */
        log.warn("No standard container for {} — using fallback parser.", college.name());
        Element h2 = doc.selectFirst("h2:matchesOwn(^Degrees and Majors Offered$)");

        if (h2 != null) {
            extractFallback(result, catalogRoot, college, h2);
        } else {
            log.error("No recognizable program structure for {}", college.name());
        }

        return result;
    }

    /* ============================================================
     *    PRIMARY PARSER: for container-based colleges
     * ============================================================ */
    private void extractFromContainer(List<ProgramMeta> result,
                                      String catalogRoot,
                                      CollegeLink college,
                                      Element container) {

        // Some catalogs use both h2 and h3 under containers.
        for (Element headingEl : container.select("> h2, > h3")) {

            String heading = headingEl.text().trim();
            if (heading.isEmpty()) continue;

            ProgramKind kind = determineKind(heading);
            if (kind == null) continue;

            Element ul = findNextUlSibling(headingEl);
            if (ul == null) continue;

            addProgramLinks(result, catalogRoot, college, ul, heading, kind);
        }
    }

    /* ============================================================
     *    FALLBACK PARSER: for Creative Arts, EHS, etc.
     * ============================================================ */
    private void extractFallback(List<ProgramMeta> result,
                                 String catalogRoot,
                                 CollegeLink college,
                                 Element startH2) {

        Element el = startH2.nextElementSibling();

        while (el != null) {

            // stop when next big section begins
            if (el.tagName().equals("h2")) break;

            if (el.tagName().equals("h3")) {

                String heading = el.text().trim();
                ProgramKind kind = determineKind(heading);
                if (kind == null) {
                    el = el.nextElementSibling();
                    continue;
                }

                Element ul = findNextUlSibling(el);
                if (ul != null) {
                    addProgramLinks(result, catalogRoot, college, ul, heading, kind);
                }
            }

            el = el.nextElementSibling();
        }
    }

    /* ============================================================
     *    Extract links from a <ul> list
     * ============================================================ */
    private void addProgramLinks(List<ProgramMeta> result,
                                 String root,
                                 CollegeLink college,
                                 Element ul,
                                 String heading,
                                 ProgramKind kind) {

        ul.select("li > a[href]").forEach(a -> {

            String programName = a.text().trim();
            String programUrl  = a.absUrl("href").trim();
            if (programUrl.isBlank()) return;

            String slug = extractSlug(programUrl);

            ProgramMeta meta = ProgramMeta.builder()
                    .catalogRoot(root)
                    .catalogLabel(extractCatalogLabelFromRoot(root))
                    .collegeName(college.name())
                    .collegeUrl(college.url())
                    .programName(programName)
                    .programUrl(programUrl)
                    .slug(slug)
                    .kind(kind)
                    .degreeLabel(heading)
                    .build();

            result.add(meta);
        });
    }

    /* ============================================================
     *    Helpers
     * ============================================================ */
    private ProgramKind determineKind(String heading) {
        String lower = heading.toLowerCase();

        if (lower.contains("bachelor")) return ProgramKind.MAJOR;
        if (lower.contains("co-major")) return ProgramKind.CO_MAJOR;
        if (lower.contains("minor")) return ProgramKind.MINOR;
        if (lower.contains("certificate")) return ProgramKind.CERTIFICATE;

        return null;
    }

    private Element findNextUlSibling(Element h) {
        Element e = h.nextElementSibling();
        while (e != null) {
            if (e.tagName().equals("ul")) return e;
            e = e.nextElementSibling();
        }
        return null;
    }

    private String extractSlug(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();

            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if (path.endsWith("index")) path = path.substring(0, path.lastIndexOf("/"));

            int idx = path.lastIndexOf('/');
            return (idx >= 0) ? path.substring(idx + 1) : path;
        } catch (Exception e) {
            return "";
        }
    }

    private String extractCatalogLabelFromRoot(String root) {
        if (root == null) return null;

        if (root.contains("/archive/")) {
            try {
                URI uri = URI.create(root);
                String p = uri.getPath();
                String label = p.replace("/archive/", "").replace("/", "");
                return label.isBlank() ? null : label;
            } catch (Exception e) {
                return null;
            }
        }

        try {
            Document d = Jsoup.connect(root)
                    .userAgent("Mozilla/5.0")
                    .timeout(10_000)
                    .get();

            Element edition = d.selectFirst("#edition a");
            if (edition != null) {
                String text = edition.text();
                String label = text.replaceAll("[^0-9\\-]", "");
                return label.isBlank() ? null : label;
            }
        } catch (Exception ignored) {}

        return null;
    }
}
