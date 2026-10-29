package com.courseplanner.api.Bulletin.Requirements;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.courseplanner.api.Bulletin.dto.ParsedReqNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementTreeParserGoldenTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @ParameterizedTest
    @MethodSource("fixtures")
    void parsesRequirementSnapshots(String fixtureHtml, String expectedJson, String url) throws Exception {
        String html = readResource(fixtureHtml);
        Document doc = Jsoup.parse(html, url);

        RequirementTreeParser parser = new RequirementTreeParser(new CourseLookupService(null, null));
        ParsedReqNode actual = parser.parseRequirements(doc);

        JsonNode actualJson = mapper.readTree(mapper.writeValueAsBytes(actual));
        JsonNode expectedJsonNode = mapper.readTree(readResource(expectedJson));

        assertThat(actualJson).isEqualTo(expectedJsonNode);
    }

    private static Stream<Arguments> fixtures() throws IOException {
        var entries = new ObjectMapper().readTree(readResource("synthetic/cases.json"));
        assertThat(entries.size()).isEqualTo(30);
        var arguments = Stream.<Arguments>builder();
        for (var entry : entries) {
            String slug = entry.get("slug").asText();
            arguments.add(Arguments.of("synthetic/" + slug + ".html",
                    "synthetic/" + slug + ".expected.json",
                    "https://catalog.example.edu/synthetic/" + slug + "/"));
        }
        return arguments.build();
    }

    private static String readResource(String path) throws IOException {
        ClassLoader loader = RequirementTreeParserGoldenTest.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
