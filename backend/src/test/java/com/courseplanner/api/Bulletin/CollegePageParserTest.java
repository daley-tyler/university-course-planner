package com.courseplanner.api.Bulletin;

import com.courseplanner.api.Bulletin.Programs.CollegePageParser;
import com.courseplanner.api.Bulletin.dto.CollegeLink;
import com.courseplanner.api.model.entities.enums.ProgramKind;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CollegePageParserTest {
    private static final String ROOT = "https://catalog.example.edu/archive/2091-2092/";
    private final CollegePageParser parser = new CollegePageParser();
    private final CollegeLink college = new CollegeLink("Invented Studio School", "/studio/", ROOT+"studio/");

    @Test void standardContainerClassifiesEveryProgramKind() {
        var doc = Jsoup.parse("""
            <div id="degreesandprogramstextcontainer">
              <h2>Bachelor of Rover Studies</h2><ul><li><a href="rover/">Rover Workshop</a></li></ul>
              <h3>Minors</h3><ul><li><a href="mural/">Mural Workshop</a></li></ul>
              <h3>Certificates</h3><ul><li><a href="kite/">Kite Workshop</a></li></ul>
            </div>
            """,college.url());
        var programs = parser.parsePrograms(ROOT,college,doc);
        assertThat(programs).extracting(p->p.getSlug()).containsExactly("rover","mural","kite");
        assertThat(programs).extracting(p->p.getProgramName()).containsExactly("Rover Workshop","Mural Workshop","Kite Workshop");
        assertThat(programs).extracting(p->p.getKind()).containsExactly(ProgramKind.MAJOR,ProgramKind.MINOR,ProgramKind.CERTIFICATE);
        assertThat(programs).allSatisfy(p->{
            assertThat(p.getCatalogLabel()).isEqualTo("2091-2092");
            assertThat(p.getCollegeName()).isEqualTo(college.name());
        });
    }
    @Test void fallbackLayoutKeepsCoMajorsAndStopsAtTheNextSection() {
        var doc = Jsoup.parse("""
            <h2>Degrees and Majors Offered</h2>
            <h3>Bachelor of Kite Studies</h3><ul><li><a href="kite/">Kite Workshop</a></li></ul>
            <h3>Co-Majors</h3><ul><li><a href="mural/">Mural Workshop</a></li></ul>
            <h2>Unrelated links</h2><h3>Minors</h3><ul><li><a href="excluded/">Excluded</a></li></ul>
            """,college.url());
        var programs=parser.parsePrograms(ROOT,college,doc);
        assertThat(programs).extracting(p->p.getSlug()).containsExactly("kite","mural");
        assertThat(programs).extracting(p->p.getKind()).containsExactly(ProgramKind.MAJOR,ProgramKind.CO_MAJOR);
    }
    @Test void alternateStandardContainerPreservesAbsoluteUrls() {
        var doc=Jsoup.parse("""
            <div id="degreesandmajorstextcontainer">
              <h3>Minors</h3><ul><li><a href="https://catalog.example.edu/invented/mural/">Mural Workshop</a></li></ul>
            </div>
            """,college.url());
        var programs=parser.parsePrograms(ROOT,college,doc);
        assertThat(programs).hasSize(1);
        assertThat(programs.getFirst().getKind()).isEqualTo(ProgramKind.MINOR);
        assertThat(programs.getFirst().getProgramUrl()).isEqualTo("https://catalog.example.edu/invented/mural/");
    }
}
