package com.courseplanner.api.Bulletin.Requirements;

import com.courseplanner.api.Bulletin.dto.ParsedReqNode;
import com.courseplanner.api.Bulletin.dto.ParsedReqNodeCourseOption;
import com.courseplanner.api.model.entities.catalog.Course;
import com.courseplanner.api.model.entities.enums.ReqNodeKind;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import com.courseplanner.api.model.entities.requirements.ReqNode;
import com.courseplanner.api.model.entities.requirements.ReqNodeCourseOption;
import com.courseplanner.api.model.repositories.ReqNodeCourseOptionRepository;
import com.courseplanner.api.model.repositories.ReqNodeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementPersistenceService {

    private final ReqNodeRepository reqNodeRepo;
    private final ReqNodeCourseOptionRepository reqNodeCourseOptionRepo;
    private final CourseLookupService courseLookupService;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Replace the entire requirement tree for a ProgramCatalogVersion (PCV).
     *
     * Strategy:
     *   1) Delete existing req_nodes by PCV
     *   2) Build full in-memory tree (no saves yet); collect LEAF_COURSES options in a side list
     *   3) Save root once — cascade persists entire tree (children get IDs)
     *   4) Insert all ReqNodeCourseOption rows using the now-persisted node IDs
     */
    @Transactional
    public void replaceRequirementsForProgram(ProgramCatalogVersion pcv, ParsedReqNode rootTree) {
        log.info("Replacing requirements for PCV {} (program={})",
                pcv.getId(), pcv.getProgram().getName());

        if (rootTree == null) {
            log.warn("No requirement tree parsed for PCV {} — nothing to persist.", pcv.getId());
            return;
        }
        if (!hasMeaningfulContent(rootTree)) {
            log.warn("Parsed requirement tree for PCV {} was empty — keeping existing requirements.", pcv.getId());
            return;
        }

        // 1) Hard reset of previous tree for this PCV
        reqNodeRepo.deleteByCatalogVersion_Id(pcv.getId());
        reqNodeRepo.flush();
        entityManager.flush();
        entityManager.clear();

        // 2) Build in-memory tree and gather LEAF_COURSES to insert later
        List<PendingCourseOption> pendingCourseOptions = new ArrayList<>();
        ReqNode root = buildTreeRecursive(pcv, null, rootTree, 0, pendingCourseOptions);

        // In pathological cases, parsing could yield nothing meaningful
        if (root == null) {
            log.warn("Parsed requirement tree produced no root node for PCV {}", pcv.getId());
            return;
        }

        // 3) Single save (cascade down to children)
        root = reqNodeRepo.save(root);
        reqNodeRepo.flush();

        // 4) Insert leaf course options (now node IDs exist)
        persistAllPendingCourseOptions(pendingCourseOptions);

        log.info("Requirement replace complete for PCV {}. Nodes: {}, Pending options inserted: {}",
                pcv.getId(), countNodes(root), pendingCourseOptions.size());
    }

    /**
     * Build the ReqNode tree entirely in memory (do not save here).
     * Also collect any LEAF_COURSES options to persist after nodes exist.
     */
    private ReqNode buildTreeRecursive(
            ProgramCatalogVersion pcv,
            ReqNode parent,
            ParsedReqNode parsed,
            int orderIndex,
            List<PendingCourseOption> pendingCourseOptions
    ) {
        if (parsed == null || parsed.getKind() == null) return null;

        ReqNodeKind kind;
        try {
            kind = ReqNodeKind.valueOf(parsed.getKind());
        } catch (IllegalArgumentException e) {
            log.error("Invalid ReqNodeKind '{}' for node '{}'. Skipping.",
                    parsed.getKind(), parsed.getName());
            return null;
        }

        ReqNode node = new ReqNode();
        node.setCatalogVersion(pcv);
        node.setName(parsed.getName());
        node.setKind(kind);
        node.setOrderIndex(orderIndex);
        node.setNRequired(parsed.getNRequired());
        node.setCreditMin(parsed.getCreditMin());
        node.setCreditMax(parsed.getCreditMax());
        node.setNotes(parsed.getNotes());
        node.setLevelMin(parsed.getLevelMin() != null ? parsed.getLevelMin().shortValue() : null);
        node.setLevelCap100(parsed.getLevelCap100());
        node.setLevelCap200(parsed.getLevelCap200());
        node.setLevelCap300(parsed.getLevelCap300());
        node.setLevelCap400(parsed.getLevelCap400());
        node.setLevelCap100Courses(toShort(parsed.getLevelCap100Courses()));
        node.setLevelCap200Courses(toShort(parsed.getLevelCap200Courses()));
        node.setLevelCap300Courses(toShort(parsed.getLevelCap300Courses()));
        node.setLevelCap400Courses(toShort(parsed.getLevelCap400Courses()));
        node.setGradeMin(parsed.getGradeMin());
        node.setResidencyCredits(parsed.getResidencyCredits());
        node.setDoubleCountAllowed(parsed.getDoubleCountAllowed() != null ? parsed.getDoubleCountAllowed() : Boolean.TRUE);
        node.setDoubleCountCap(parsed.getDoubleCountCap());
        node.setFootnoteText(parsed.getFootnoteText());
        node.setSourceUrl(parsed.getSourceUrl());
        node.setSourceRef(parsed.getSourceRef());

        if (parent != null) {
            parent.addChild(node); // maintains both sides
        }

        // Stash LEAF_COURSES for later batch insert
        if (kind == ReqNodeKind.LEAF_COURSES && parsed.getCourseOptions() != null) {
            for (ParsedReqNodeCourseOption opt : parsed.getCourseOptions()) {
                if (opt == null) continue;
                pendingCourseOptions.add(new PendingCourseOption(node, opt));
            }
        }

        // Recurse children
        if (parsed.getChildren() != null) {
            int childOrder = 0;
            for (ParsedReqNode childParsed : parsed.getChildren()) {
                ReqNode child = buildTreeRecursive(pcv, node, childParsed, childOrder++, pendingCourseOptions);
                // child is attached in buildTreeRecursive via parent.addChild(node)
            }
        }

        return node;
    }

    /**
     * After the node tree is saved (and nodes have IDs), create ReqNodeCourseOption rows.
     */
    private void persistAllPendingCourseOptions(List<PendingCourseOption> pending) {
        if (pending == null || pending.isEmpty()) return;

        List<ReqNodeCourseOption> batch = new ArrayList<>(pending.size());

        for (PendingCourseOption p : pending) {
            if (p == null || p.getNode() == null || p.getOpt() == null) continue;

            ParsedReqNodeCourseOption opt = p.getOpt();
            ReqNode node = p.getNode();

            Optional<Course> maybeCourse = courseLookupService.findCourse(
                    opt.getSubjectCode(), opt.getCourseNumber());

            if (maybeCourse.isEmpty()) {
                log.warn("Missing course: {} {} — creating ghost course",
                        opt.getSubjectCode(), opt.getCourseNumber());
                Course ghost = courseLookupService.createGhostCourse(
                        opt.getSubjectCode(), opt.getCourseNumber());
                maybeCourse = Optional.of(ghost);
            }

            ReqNodeCourseOption rco = new ReqNodeCourseOption();
            rco.setReqNode(node);                 // node now has an ID
            rco.setCourse(maybeCourse.get());     // real or ghost
            batch.add(rco);
        }

        if (!batch.isEmpty()) {
            reqNodeCourseOptionRepo.saveAll(batch);
            reqNodeCourseOptionRepo.flush();
        }
    }

    private int countNodes(ReqNode root) {
        if (root == null) return 0;
        int total = 1;
        if (root.getChildren() != null) {
            for (ReqNode c : root.getChildren()) total += countNodes(c);
        }
        return total;
    }

    private Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }

    private boolean hasMeaningfulContent(ParsedReqNode node) {
        if (node == null) return false;
        if (node.getCourseOptions() != null && !node.getCourseOptions().isEmpty()) return true;
        if (node.getNotes() != null && !node.getNotes().isBlank()) return true;
        if (node.getFootnoteText() != null && !node.getFootnoteText().isBlank()) return true;
        if (node.getNRequired() != null) return true;
        if (node.getCreditMin() != null || node.getCreditMax() != null) return true;
        if (node.getLevelMin() != null) return true;
        if (node.getLevelCap100() != null || node.getLevelCap200() != null
                || node.getLevelCap300() != null || node.getLevelCap400() != null) return true;
        if (node.getLevelCap100Courses() != null || node.getLevelCap200Courses() != null
                || node.getLevelCap300Courses() != null || node.getLevelCap400Courses() != null) return true;
        if (node.getGradeMin() != null || node.getResidencyCredits() != null) return true;
        if (node.getDoubleCountCap() != null) return true;
        if (node.getChildren() != null) {
            for (ParsedReqNode child : node.getChildren()) {
                if (hasMeaningfulContent(child)) return true;
            }
        }
        return false;
    }

    @Value
    private static class PendingCourseOption {
        ReqNode node;
        ParsedReqNodeCourseOption opt;
    }
}
