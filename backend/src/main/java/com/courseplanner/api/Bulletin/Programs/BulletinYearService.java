package com.courseplanner.api.Bulletin.Programs;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class BulletinYearService {

    @Value("${bulletin.archive-root:https://catalog.example.edu/archive/}")
    private String archiveRoot = "https://catalog.example.edu/archive/";

    @Value("${bulletin.current-root:https://catalog.example.edu}")
    private String currentRoot = "https://catalog.example.edu";

    private static final Pattern CATALOG_LABEL_PATTERN =
            Pattern.compile("(20\\d{2})\\s*[-\\u2013]\\s*(20\\d{2})");

    /**
     * Returns ALL bulletin roots:
     * - current bulletin home
     * - each /archive/YYYY-YYYY/ root
     * Deduplicated, with CURRENT_ROOT first.
     */
    public List<String> getAllBulletinRoots() {
        Set<String> roots = new LinkedHashSet<>();

        roots.add(currentRoot);

        try {
            Document doc = Jsoup.connect(archiveRoot)
                    .userAgent("Mozilla/5.0")
                    .timeout(10_000)
                    .get();

            for (Element a : doc.select("a[href]")) {
                String href = a.attr("href").trim();
                if (!href.contains("/archive/")) continue;

                String cleaned = extractArchivePath(href);
                if (cleaned == null) continue;

                cleaned = normalizeYearRange(cleaned);

                // Only accept proper year folders like /archive/2024-2025/
                if (!cleaned.matches("/archive/\\d{4}-\\d{4}/")) {
                    continue;
                }

                roots.add(resolveAgainstCurrentRoot(cleaned));
            }

        } catch (Exception ex) {
            log.error("Failed to fetch archive page: {}", ex.getMessage());
        }

        return new ArrayList<>(roots);
    }

    private String extractArchivePath(String href) {
        int idx = href.indexOf("/archive/");
        if (idx == -1) return null;

        String tail = href.substring(idx);
        if (!tail.endsWith("/")) tail += "/";
        return tail;
    }

    /**
     * Normalize things like /archive/2024-25/ -> /archive/2024-2025/
     */
    private String normalizeYearRange(String url) {
        String raw = url.replace("/archive/", "").replace("/", "");
        String[] parts = raw.split("-");
        if (parts.length == 2 && parts[1].length() == 2) {
            return "/archive/" + parts[0] + "-20" + parts[1] + "/";
        }
        return url;
    }

    private String resolveAgainstCurrentRoot(String archivePath) {
        URI current = URI.create(currentRoot);
        return current.getScheme() + "://" + current.getHost() + archivePath;
    }

    /**
     * Get last N bulletin roots, newest first.
     * The current catalog root is treated as "year 9999" to sort first.
     */
    public List<String> getLastNBulletins(int n) {
        List<String> all = getAllBulletinRoots();

        // Store as sortable record
        record BYear(String url, int year) {}

        List<BYear> parsed = all.stream()
                .map(url -> {
                    if (url.equals(currentRoot)) {
                        return new BYear(url, 9999);   // always sort first
                    }

                    String part = url.replace(archiveRoot, "")
                            .replace("/", "");

                    int startYear = Integer.parseInt(part.split("-")[0]);
                    return new BYear(url, startYear);
                })
                .sorted((a, b) -> b.year() - a.year()) // DESC
                .limit(n)
                .toList();

        return parsed.stream().map(BYear::url).toList();
    }

    public String resolveCatalogLabel(String root) {
        if (root == null) return null;

        if (root.contains("/archive/")) {
            try {
                String path = java.net.URI.create(root).getPath();
                String label = path.replace("/archive/", "").replace("/", "");
                return label.isBlank() ? null : label;
            } catch (Exception ex) {
                return null;
            }
        }

        try {
            Document doc = Jsoup.connect(root)
                    .userAgent("Mozilla/5.0")
                    .timeout(10_000)
                    .get();

            Element edition = doc.selectFirst("#edition a");
            if (edition != null) {
                String label = edition.text().replaceAll("[^0-9\\-]", "");
                if (!label.isBlank()) return label;
            }

            String text = doc.text();
            Matcher matcher = CATALOG_LABEL_PATTERN.matcher(text);
            if (matcher.find()) {
                return matcher.group(1) + "-" + matcher.group(2);
            }

            for (Element a : doc.select("a[href]")) {
                Matcher hrefMatch = CATALOG_LABEL_PATTERN.matcher(a.attr("href"));
                if (hrefMatch.find()) {
                    return hrefMatch.group(1) + "-" + hrefMatch.group(2);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to resolve bulletin label for {}: {}", root, ex.getMessage());
        }

        return null;
    }
}
