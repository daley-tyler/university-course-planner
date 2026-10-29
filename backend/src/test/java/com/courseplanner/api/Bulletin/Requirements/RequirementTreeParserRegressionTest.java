package com.courseplanner.api.Bulletin.Requirements;

import com.courseplanner.api.Bulletin.dto.ParsedReqNode;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RequirementTreeParserRegressionTest {
    private final RequirementTreeParser parser = new RequirementTreeParser(new CourseLookupService(null, null));

    @Test
    void repeatedFootnoteNumbersAreResolvedWithinTheirOwnTableSection() {
        var root = parse("""
            <h3>First track</h3>
            <table class="sc_courselist"><tbody><tr><td class="codecol">AAA 101<sup>1</sup></td></tr></tbody></table>
            <dl class="sc_footnotes"><dt>1</dt><dd>First track policy.</dd></dl>
            <h3>Second track</h3>
            <table class="sc_courselist"><tbody><tr><td class="codecol">BBB 101<sup>1</sup></td></tr></tbody></table>
            <dl class="sc_footnotes"><dt>1</dt><dd>Second track policy.</dd></dl>
            """);
        assertThat(find(root, "AAA 101").getFootnoteText()).isEqualTo("First track policy.");
        assertThat(find(root, "BBB 101").getFootnoteText()).isEqualTo("Second track policy.");
        assertThat(flatten(root).stream().filter(n -> "Footnote".equals(n.getName()))).isEmpty();
    }

    @Test
    void semesterDurationsAreNotMisreadAsCourseCounts() {
        var root = parse("""
            <table class="sc_courselist"><tbody>
              <tr><td><span class="courselistcomment">Complete three (3) semesters in a large ensemble; two semesters in a second ensemble.</span></td><td class="hourscol">5</td></tr>
            </tbody></table>
            """);
        var requirement = find(root, "Complete three");
        assertThat(requirement.getKind()).isEqualTo("CREDIT_AT_LEAST");
        assertThat(requirement.getCreditMin()).isEqualByComparingTo("5");
        assertThat(requirement.getNRequired()).isNull();
    }

    @Test
    void takeTheseFourCoursesRequiresAllFourChoices() {
        var root = parse("""
            <table class="sc_courselist"><tbody>
              <tr><td><span class="courselistcomment">Take these four courses:</span></td><td class="hourscol">12</td></tr>
              <tr><td class="codecol">AAA 101</td></tr><tr><td class="codecol">AAA 102</td></tr>
              <tr><td class="codecol">AAA 103</td></tr><tr><td class="codecol">AAA 104</td></tr>
            </tbody></table>
            """);
        var requirement = find(root, "Take these");
        assertThat(requirement.getKind()).isEqualTo("N_OF");
        assertThat(requirement.getNRequired()).isEqualTo(4);
        assertThat(requirement.getChildren()).hasSize(4);
    }

    @Test
    void unusedFootnoteFromAnotherSectionIsNotSilentlyDiscarded() {
        var root = parse("""
            <table class="sc_courselist"><tbody><tr><td class="codecol">AAA 101<sup>1</sup></td></tr></tbody></table>
            <dl class="sc_footnotes"><dt>1</dt><dd>First policy.</dd></dl>
            <h3>Second section</h3>
            <dl class="sc_footnotes"><dt>1</dt><dd>Unreferenced second policy.</dd></dl>
            """);
        assertThat(flatten(root).stream().filter(n -> "Footnote".equals(n.getName())).map(ParsedReqNode::getFootnoteText)).containsExactly("Unreferenced second policy.");
    }

    @Test
    void selectionHeadersKeepBothCourseCountsAndHours() {
        var root = parse("""
            <table class="sc_courselist"><tbody>
              <tr class="areaheader"><td><span class="courselistcomment">Select two of the following:</span></td><td class="hourscol">6</td></tr>
              <tr><td class="codecol">AAA 101</td></tr><tr><td class="codecol">AAA 102</td></tr>
              <tr><td class="codecol">AAA 103</td></tr>
            </tbody></table>
            """);
        var requirement = find(root, "Select two");
        assertThat(requirement.getKind()).isEqualTo("N_OF");
        assertThat(requirement.getNRequired()).isEqualTo(2);
        assertThat(requirement.getCreditMin()).isEqualByComparingTo("6");
        assertThat(requirement.getChildren()).hasSize(3);
    }

    @Test
    void pruningAnEmptySelectorDoesNotDiscardItsFootnote() {
        var root = parse("""
            <table class="sc_courselist"><tbody>
              <tr><td><span class="courselistcomment">Select two courses from another section.</span><sup>1</sup></td></tr>
              <tr class="areaheader"><td>Core</td></tr><tr><td class="codecol">AAA 101</td></tr>
            </tbody></table>
            <dl class="sc_footnotes"><dt>1</dt><dd>Never discard this policy.</dd></dl>
            """);
        assertThat(flatten(root).stream().map(ParsedReqNode::getFootnoteText)).contains("Never discard this policy.");
    }

    private ParsedReqNode parse(String body) { return parser.parseRequirements(Jsoup.parse("<div id='textcontainer'>" + body + "</div>")); }
    private ParsedReqNode find(ParsedReqNode root, String name) {
        return flatten(root).stream().filter(n -> n.getName().startsWith(name)).findFirst().orElseThrow();
    }
    private List<ParsedReqNode> flatten(ParsedReqNode node) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(node), node.getChildren().stream().flatMap(n -> flatten(n).stream())).toList();
    }
}
