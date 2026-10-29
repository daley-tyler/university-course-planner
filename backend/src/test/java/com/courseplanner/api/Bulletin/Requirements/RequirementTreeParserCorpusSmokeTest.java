package com.courseplanner.api.Bulletin.Requirements;

import com.courseplanner.api.Bulletin.dto.ParsedReqNode;
import com.courseplanner.api.model.entities.enums.ReqNodeKind;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementTreeParserCorpusSmokeTest {

    private static final Set<String> ALLOWED_KINDS = EnumSet.allOf(ReqNodeKind.class).stream()
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toSet());

    @Test
    void parsesAllFixturesWithoutInvalidNodes() throws IOException, URISyntaxException {
        Path bulletinDir = Path.of(Objects.requireNonNull(
                getClass().getClassLoader().getResource("synthetic")).toURI());

        try (Stream<Path> paths = Files.list(bulletinDir)) {
            List<Path> htmlPaths = paths
                    .filter(p -> p.getFileName().toString().endsWith(".html"))
                    .toList();
            assertThat(htmlPaths).hasSizeGreaterThanOrEqualTo(30);
            htmlPaths.forEach(this::parseAndValidate);
        }
    }

    private void parseAndValidate(Path htmlPath) {
        try {
            String html = Files.readString(htmlPath, StandardCharsets.UTF_8);
            Document doc = Jsoup.parse(html, "https://catalog.example.edu/");
            RequirementTreeParser parser = new RequirementTreeParser(new CourseLookupService(null, null));
            ParsedReqNode root = parser.parseRequirements(doc);
            assertThat(root).isNotNull();
            assertThat(root.getKind()).isEqualTo("ALL_OF");
            assertThat(root.getChildren()).isNotNull();
            assertValidNode(root);
            String retainedPolicies = collectPolicyText(root);
            for (var definition : doc.select("dl.sc_footnotes > dd")) {
                assertThat(retainedPolicies).as("Footnote retained from %s: %s", htmlPath.getFileName(), definition.text())
                        .contains(normalizePolicy(definition.text()));
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to parse fixture: " + htmlPath.getFileName(), e);
        }
    }

    private String normalizePolicy(String text) {
        return text.replace('\u00a0', ' ').replace('\u2013', '-').replace('\u2014', '-').replace('\u2215', '/').replaceAll("\\s+", " ").trim();
    }

    private String collectPolicyText(ParsedReqNode node) {
        return normalizePolicy(node.getFootnoteText() == null ? "" : node.getFootnoteText()) + "\n"
                + node.getChildren().stream().map(this::collectPolicyText).collect(java.util.stream.Collectors.joining("\n"));
    }

    private void assertValidNode(ParsedReqNode node) {
        assertThat(node.getKind()).isNotBlank();
        assertThat(ALLOWED_KINDS).contains(node.getKind());

        String kind = node.getKind();
        boolean hasChildren = node.getChildren() != null && !node.getChildren().isEmpty();
        boolean hasOptions = node.getCourseOptions() != null && !node.getCourseOptions().isEmpty();

        if ("LEAF_COURSES".equals(kind)) {
            assertThat(node.getCourseOptions()).isNotEmpty();
        }
        if ("N_OF".equals(kind)) {
            assertThat(node.getNRequired()).isNotNull();
        }
        if ("CREDIT_AT_LEAST".equals(kind)) {
            assertThat(node.getCreditMin() != null || node.getCreditMax() != null).isTrue();
        }
        if ("LEVEL_AT_LEAST".equals(kind)) {
            assertThat(node.getLevelMin()).isNotNull();
        }
        if ("COURSE_CAP".equals(kind)) {
            assertThat(node.getNRequired() != null
                    || node.getLevelCap100() != null || node.getLevelCap200() != null
                    || node.getLevelCap300() != null || node.getLevelCap400() != null
                    || node.getLevelCap100Courses() != null || node.getLevelCap200Courses() != null
                    || node.getLevelCap300Courses() != null || node.getLevelCap400Courses() != null)
                    .isTrue();
        }
        if ("NOTE_ONLY".equals(kind)) {
            assertThat((node.getNotes() != null && !node.getNotes().isBlank())
                    || (node.getFootnoteText() != null && !node.getFootnoteText().isBlank()))
                    .isTrue();
        }

        if (!hasChildren && !hasOptions) {
            // Only standalone constraint nodes may exist without children or courseOptions.
            // Container kinds (ALL_OF, ANY_OF, N_OF, etc.) must be pruned by RequirementTreeParser
            // before reaching this point.
            assertThat(kind).isIn(
                    "CREDIT_AT_LEAST",
                    "LEVEL_AT_LEAST",
                    "COURSE_CAP",
                    "GRADE_MIN",
                    "RESIDENCY_MIN",
                    "GPA_AT_LEAST",
                    "DOUBLE_COUNT_RULE",
                    "EXPERIENCE",
                    "CONCURRENCY_BLOCK",
                    "NOTE_ONLY"
            );
        }

        if (hasChildren) {
            for (ParsedReqNode child : node.getChildren()) {
                assertValidNode(child);
            }
        }
    }
}
