package com.courseplanner.api.Bulletin.Programs;

import com.courseplanner.api.Bulletin.dto.CollegeLink;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class CollegeDiscoveryService {

    private static final Set<String> VALID_COLLEGE_PATHS = Set.of(
            "/arts-science/",
            "/business/",
            "/engineering-computing/",
            "/creative-arts/",
            "/education-health-society/",
            "/liberal-arts-applied-science/"
    );

    public List<CollegeLink> getColleges(String catalogRoot) {
        List<CollegeLink> result = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(catalogRoot)
                    .userAgent("Mozilla/5.0")
                    .timeout(10_000)
                    .get();

            Element menu = doc.selectFirst("#cl-menu");
            if (menu == null) {
                log.warn("Unable to find #cl-menu on {}", catalogRoot);
                return result;
            }

            for (Element a : menu.select("li.isparent > a[href]")) {
                String path = a.attr("href").trim();
                if (!path.endsWith("/")) path += "/";

                // Normalize archive paths
                String normalized = path;
                if (catalogRoot.contains("/archive/") && normalized.startsWith("/archive/")) {
                    // Strip "/archive/2024-2025"
                    normalized = normalized.substring(normalized.indexOf("/", 9));
                }

                if (VALID_COLLEGE_PATHS.contains(normalized)) {
                    String name = a.text().trim();
                    String url = catalogRoot.endsWith("/")
                            ? catalogRoot + normalized.substring(1)
                            : catalogRoot + normalized;
                    result.add(new CollegeLink(name, normalized, url));
                }
            }

        } catch (Exception ex) {
            log.error("Error fetching colleges from {}: {}", catalogRoot, ex.getMessage());
        }

        return result;
    }
}
