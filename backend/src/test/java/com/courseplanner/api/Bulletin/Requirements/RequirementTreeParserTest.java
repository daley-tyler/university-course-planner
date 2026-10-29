package com.courseplanner.api.Bulletin.Requirements;

import com.courseplanner.api.Bulletin.dto.ParsedReqNode;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RequirementTreeParserTest {
    @Test void combinedWorkshopPlanKeepsChoicesBundlesCapsAndPolicies() {
        String html="""
            <div id="textcontainer"><h3>Invented Rover Workshop</h3>
            <table class="sc_courselist"><tbody>
              <tr class="areaheader"><td>Workshop entry</td></tr>
              <tr><td class="codecol">SYN 117</td></tr>
              <tr class="areaheader"><td>Build basket (2 courses)</td></tr>
              <tr><td class="codecol">SYN 229</td></tr>
              <tr class="orclass"><td class="codecol">SYN 331</td></tr>
              <tr><td><span class="courselistcomment">Choose two courses</span></td></tr>
              <tr><td class="codecol">SYN 443</td></tr>
              <tr><td class="codecol"><a class="bubblelink code">ART 119</a> &amp; <a class="bubblelink code">ART 221</a></td></tr>
              <tr><td><span class="courselistcomment">No more than 4 hours at 100 level</span></td></tr>
              <tr><td>Total Credit Hours</td><td class="hourscol">19-25</td></tr>
            </tbody></table>
            <dl class="sc_footnotes"><dt>6</dt><dd>The invented rover showcase meets in the east studio.</dd></dl>
            </div>
            """;
        var parser=new RequirementTreeParser(new CourseLookupService(null,null));
        var root=parser.parseRequirements(Jsoup.parse(html,"https://catalog.example.edu/synthetic/workshop/"));
        var all=flatten(root);
        var basket=all.stream().filter(n->n.getName().equals("Build basket")).findFirst().orElseThrow();
        assertThat(basket.getKind()).isEqualTo("N_OF");
        assertThat(basket.getNRequired()).isEqualTo(2);
        assertThat(basket.getChildren()).anySatisfy(n->{
            assertThat(n.getKind()).isEqualTo("ANY_OF");
            assertThat(n.getChildren()).extracting(ParsedReqNode::getName).containsExactly("SYN 229","SYN 331");
        });
        assertThat(all).anySatisfy(n->{
            assertThat(n.getName()).isEqualTo("ART 119 & ART 221");
            assertThat(n.getKind()).isEqualTo("ALL_OF");
            assertThat(n.getChildren()).extracting(ParsedReqNode::getName).containsExactly("ART 119","ART 221");
        });
        assertThat(all).anySatisfy(n->{
            assertThat(n.getKind()).isEqualTo("COURSE_CAP");
            assertThat(n.getLevelCap100()).isEqualByComparingTo("4");
        });
        assertThat(all).anySatisfy(n->{
            assertThat(n.getName()).isEqualTo("Total Credit Hours");
            assertThat(n.getCreditMin()).isEqualByComparingTo("19");
            assertThat(n.getCreditMax()).isEqualByComparingTo("25");
        });
        assertThat(all.stream().filter(n->"LEAF_COURSES".equals(n.getKind())).map(ParsedReqNode::getName))
                .containsExactly("SYN 117","SYN 229","SYN 331","SYN 443","ART 119","ART 221");
        assertThat(all.stream().filter(n->"Footnote".equals(n.getName())).map(ParsedReqNode::getFootnoteText))
                .containsExactly("The invented rover showcase meets in the east studio.");
    }
    private List<ParsedReqNode> flatten(ParsedReqNode n) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(n),n.getChildren().stream().flatMap(c->flatten(c).stream())).toList();
    }
}
