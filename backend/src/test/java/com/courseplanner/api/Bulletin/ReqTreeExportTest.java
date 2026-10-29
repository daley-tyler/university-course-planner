package com.courseplanner.api.Bulletin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.courseplanner.api.Bulletin.dto.ReqTreeVizNode;
import com.courseplanner.api.model.entities.enums.ReqNodeKind;
import com.courseplanner.api.model.entities.requirements.ReqNode;
import com.courseplanner.api.model.entities.requirements.ReqNodeCourseOption;
import com.courseplanner.api.model.repositories.ReqNodeCourseOptionRepository;
import com.courseplanner.api.model.repositories.ReqNodeRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SpringBootTest
@Disabled("Utility test — run manually to export requirement tree JSON")
class ReqTreeExportTest {

    @Autowired
    private ReqNodeRepository reqNodeRepo;

    @Autowired
    private ReqNodeCourseOptionRepository reqNodeCourseOptionRepo;

    /**
     * Change this PCV ID before running.
     * Export JSON goes into target/reqtree-PCV_{id}.json
     */
    private static final long PCV_ID_TO_EXPORT = 165L;

    @Test
    @Transactional // <— keeps the Persistence Context open during the whole traversal
    void exportRequirementTreeToJsonFile() throws Exception {
        List<ReqNode> roots =
                reqNodeRepo.findByCatalogVersion_IdAndParentIsNullOrderByOrderIndexAsc(PCV_ID_TO_EXPORT);

        ReqTreeVizNode exportRoot;

        if (roots == null || roots.isEmpty()) {
            exportRoot = ReqTreeVizNode.builder()
                    .name("Program Requirements")
                    .kind("ALL_OF")
                    .children(new ArrayList<>())
                    .build();
        } else if (roots.size() == 1) {
            exportRoot = toVizNode(roots.get(0));
        } else {
            exportRoot = ReqTreeVizNode.builder()
                    .name("Program Requirements")
                    .kind("ALL_OF")
                    .children(new ArrayList<>())
                    .build();

            roots.stream()
                    .sorted(Comparator.comparing(r -> r.getOrderIndex() == null ? 0 : r.getOrderIndex()))
                    .forEach(r -> exportRoot.getChildren().add(toVizNode(r)));
        }

        // Serialize as pretty JSON
        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        File out = new File("target/reqtree-PCV_" + PCV_ID_TO_EXPORT + ".json");
        mapper.writeValue(out, exportRoot);

        System.out.println("\n\n===== Requirement Tree Export Complete =====");
        System.out.println("JSON file written to:\n" + out.getAbsolutePath());
        System.out.println("Upload this file into JSON Crack.\n");
    }

    // ---------------- Helper method to build JSON-friendly node ---------------- //
    private ReqTreeVizNode toVizNode(ReqNode node) {
        List<ReqTreeVizNode> childList = new ArrayList<>();

        ReqTreeVizNode.ReqTreeVizNodeBuilder b = ReqTreeVizNode.builder()
                .name(node.getName() != null ? node.getName() : "")
                .kind(node.getKind() != null ? node.getKind().name() : "ALL_OF")
                .nRequired(node.getNRequired())
                .creditMin(node.getCreditMin())
                .creditMax(node.getCreditMax())
                .children(childList);  // attach list directly

        // LEAF_COURSES → collect course codes
        if (node.getKind() == ReqNodeKind.LEAF_COURSES) {
            List<ReqNodeCourseOption> opts = reqNodeCourseOptionRepo.findByReqNode_Id(node.getId());
            List<String> courses = new ArrayList<>();
            for (ReqNodeCourseOption opt : opts) {
                if (opt.getCourse() != null &&
                        opt.getCourse().getSubject() != null &&
                        opt.getCourse().getNumber() != null) {
                    courses.add(opt.getCourse().getSubject().getCode() + " " + opt.getCourse().getNumber());
                }
            }
            b.courses(courses);
        }

        // Recurse children (defensive ordering)
        if (node.getChildren() != null) {
            node.getChildren().stream()
                    .sorted(Comparator.comparing(ch -> ch.getOrderIndex() == null ? 0 : ch.getOrderIndex()))
                    .forEach(ch -> childList.add(toVizNode(ch)));
        }

        return b.build();
    }
}
