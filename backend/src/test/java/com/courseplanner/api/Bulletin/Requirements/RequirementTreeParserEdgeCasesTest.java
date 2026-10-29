package com.courseplanner.api.Bulletin.Requirements;

import com.courseplanner.api.Bulletin.dto.ParsedReqNode;
import com.courseplanner.api.Bulletin.dto.ParsedReqNodeCourseOption;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Independent examples for parser boundaries, not extracted catalog policies. */
class RequirementTreeParserEdgeCasesTest {
    private final RequirementTreeParser parser = new RequirementTreeParser(new CourseLookupService(null, null));

    @Test void creditsCanBeWrittenAsWords() {
        var n = named(parse(table(comment("Choose a minimum of five credits") + row("SYN 117"))), "Choose");
        assertThat(n.getKind()).isEqualTo("CREDIT_AT_LEAST");
        assertThat(n.getCreditMin()).isEqualByComparingTo("5");
        assertThat(n.getNRequired()).isNull();
    }
    @Test void creditMaximumDoesNotInventAMinimum() {
        var n = named(parse(table(comment("Take up to seven credits") + row("SYN 117"))), "Take");
        assertThat(n.getCreditMax()).isEqualByComparingTo("7");
        assertThat(n.getCreditMin()).isNull();
    }
    @Test void requiredPairKeepsBothMembers() {
        var n = named(parse(table(comment("Take both in the rover studio") + row("SYN 117") + row("SYN 229"))), "Take");
        assertThat(n.getKind()).isEqualTo("ALL_OF");
        assertThat(codes(n)).containsExactly("SYN 117", "SYN 229");
    }
    @Test void equalityTargetRemainsACreditConstraint() {
        var n = named(parse(table(comment("Choose rover modules to equal 27 hours") + row("SYN 117"))), "Choose");
        assertThat(n.getKind()).isEqualTo("CREDIT_AT_LEAST");
        assertThat(n.getCreditMin()).isEqualByComparingTo("27");
    }
    @Test void groupCapDoesNotSwallowCourses() {
        var root = parse(table(comment("Maximum of two courses from the rover basket") + row("SYN 117")));
        assertThat(named(root,"Maximum").getNRequired()).isEqualTo(2);
        assertThat(codes(root)).containsExactly("SYN 117");
    }
    @Test void headerCarriesItsReferencedPolicy() {
        var root = parse(table("<tr class='areaheader'><td>Rover studio<sup>8</sup></td></tr>" + row("SYN 117")) + footnote(8,"The rover showcase uses the east room."));
        assertThat(named(root,"Rover studio").getFootnoteText()).isEqualTo("The rover showcase uses the east room.");
    }
    @Test void coursePolicyCanDenyOverlap() {
        var root = parse(table("<tr><td class='codecol'>SYN 117<sup>8</sup></td></tr>") + footnote(8,"Rover credits must not be double-counted."));
        assertThat(named(root,"SYN 117").getDoubleCountAllowed()).isFalse();
    }
    @Test void narrativePolicyCanDenyOverlap() {
        var root = parse("<p>Rover credits may not be counted twice.</p>" + table(row("SYN 117")));
        assertThat(named(root,"Rover credits").getDoubleCountAllowed()).isFalse();
    }
    @Test void headerRangeNeedsNoChildren() {
        var n = named(parse(table("<tr class='areaheader'><td>Rover practice</td><td class='hourscol'>7-13</td></tr>")),"Rover practice");
        assertThat(n.getKind()).isEqualTo("CREDIT_AT_LEAST");
        assertThat(n.getCreditMin()).isEqualByComparingTo("7");
        assertThat(n.getCreditMax()).isEqualByComparingTo("13");
    }
    @Test void headerHoursSurviveWithoutNumbersInTheLabel() {
        var n = named(parse(table("<tr class='areaheader'><td>Rover practice</td><td class='hourscol'>9</td></tr>" + row("SYN 117"))),"Rover practice");
        assertThat(n.getCreditMin()).isEqualByComparingTo("9");
        assertThat(codes(n)).containsExactly("SYN 117");
    }
    @Test void headerCountAndCreditsCoexist() {
        var n = named(parse(table("<tr class='areaheader'><td>Choose two courses</td><td class='hourscol'>7</td></tr>" + row("SYN 117") + row("SYN 229"))),"Choose");
        assertThat(n.getKind()).isEqualTo("N_OF");
        assertThat(n.getNRequired()).isEqualTo(2);
        assertThat(n.getCreditMin()).isEqualByComparingTo("7");
    }
    @Test void explicitHeaderCreditsTakePrecedenceOverSupplementaryHours() {
        var n = named(parse(table("<tr class='areaheader'><td>Rover practice 7-13 credits</td><td class='hourscol'>99</td></tr>")),"Rover practice");
        assertThat(n.getCreditMin()).isEqualByComparingTo("7");
        assertThat(n.getCreditMax()).isEqualByComparingTo("13");
    }
    @Test void consecutiveTablesDoNotShareHeadings() {
        var root = parse("<h3>Rover room</h3>" + table(row("SYN 117")) + table(row("SYN 229")));
        assertThat(root.getChildren()).extracting(ParsedReqNode::getName).containsExactly("Rover room","Required Courses");
        assertThat(codes(root)).containsExactly("SYN 117","SYN 229");
    }
    @Test void eachTableCanHaveItsOwnHeading() {
        var root = parse("<h3>Rover room</h3>" + table(row("SYN 117")) + "<h3>Mural room</h3>" + table(row("SYN 229")));
        assertThat(root.getChildren()).extracting(ParsedReqNode::getName).containsExactly("Rover room","Mural room");
    }
    @Test void plainCodeCellsNeedNoLinks() {
        assertThat(codes(parse(table(row("SYN 117"))))).containsExactly("SYN 117");
    }
    @Test void crossListingPreservesBothSubjects() {
        assertThat(codes(parse(table(row("SYN/ART 337"))))).containsExactly("SYN 337","ART 337");
    }
    @Test void slashSeparatedNumbersPreserveEveryOption() {
        assertThat(codes(parse(table(row("SYN 337/449"))))).containsExactly("SYN 337","SYN 449");
    }
    @Test void narrativeOverlapCapKeepsTheWholePolicy() {
        String text = "Only four credits may count toward both the rover and mural requirements.";
        var n = named(parse("<p>"+text+"</p>" + table(row("SYN 117"))),"Only four");
        assertThat(n.getDoubleCountCap()).isEqualByComparingTo("4");
        assertThat(n.getNotes()).isEqualTo(text);
    }

    private ParsedReqNode parse(String body) {
        return parser.parseRequirements(Jsoup.parse("<div id='textcontainer'>"+body+"</div>","https://catalog.example.edu/synthetic/edges/"));
    }
    private String table(String rows) { return "<table class='sc_courselist'><tbody>"+rows+"</tbody></table>"; }
    private String row(String code) { return "<tr><td class='codecol'>"+code+"</td></tr>"; }
    private String comment(String text) { return "<tr><td><span class='courselistcomment'>"+text+"</span></td></tr>"; }
    private String footnote(int number,String text) { return "<dl class='sc_footnotes'><dt>"+number+"</dt><dd>"+text+"</dd></dl>"; }
    private List<ParsedReqNode> flatten(ParsedReqNode n) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(n),n.getChildren().stream().flatMap(c->flatten(c).stream())).toList();
    }
    private ParsedReqNode named(ParsedReqNode n,String prefix) {
        return flatten(n).stream().filter(c->c.getName().startsWith(prefix)).findFirst().orElseThrow();
    }
    private List<String> codes(ParsedReqNode n) {
        return flatten(n).stream().flatMap(c->c.getCourseOptions().stream()).map(o->o.getSubjectCode()+" "+o.getCourseNumber()).toList();
    }
}
