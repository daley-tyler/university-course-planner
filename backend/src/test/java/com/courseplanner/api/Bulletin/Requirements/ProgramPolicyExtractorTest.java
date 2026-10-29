package com.courseplanner.api.Bulletin.Requirements;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProgramPolicyExtractorTest {
    @Test void readsInventedGradeAndPortfolioPolicies() {
        String grade = "The rover studio uses a grade of B+ or higher in SYN 117 and ART 229.";
        String admission = "Admission to the invented mural studio includes a portfolio of sketches.";
        var doc = Jsoup.parse("<div id='textcontainer'><p>"+grade+"</p><p>"+admission+"</p></div>",
                "https://catalog.example.edu/synthetic/policy/");
        var result = new ProgramPolicyExtractor().extract(doc,doc.location());
        assertThat(result.getGradeMinText()).isEqualTo(grade);
        assertThat(result.getGradeMinCourses()).containsExactly("SYN 117","ART 229");
        assertThat(result.getNotes()).isEqualTo(grade+"\n"+admission);
        assertThat(result.getSourceUrl()).isEqualTo(doc.location());
    }
}
