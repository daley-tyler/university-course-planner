package com.courseplanner.api.api.services;

import com.courseplanner.api.api.dto.RequirementAttributeOptionDto;
import com.courseplanner.api.api.dto.RequirementCourseOptionDto;
import com.courseplanner.api.api.dto.RequirementNodeDto;
import com.courseplanner.api.api.dto.RequirementSourceDto;
import com.courseplanner.api.api.dto.RequirementTreeResponse;
import com.courseplanner.api.model.entities.catalog.Course;
import com.courseplanner.api.model.entities.catalog.Subject;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import com.courseplanner.api.model.entities.requirements.ReqNode;
import com.courseplanner.api.model.entities.requirements.ReqNodeAttributeOption;
import com.courseplanner.api.model.entities.requirements.ReqNodeCourseOption;
import com.courseplanner.api.model.repositories.ReqNodeAttributeOptionRepository;
import com.courseplanner.api.model.repositories.ReqNodeCourseOptionRepository;
import com.courseplanner.api.model.repositories.ReqNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementQueryServiceImpl implements RequirementQueryService {

    private final ProgramQueryService programQueryService;
    private final ReqNodeRepository reqNodeRepository;
    private final ReqNodeCourseOptionRepository reqNodeCourseOptionRepository;
    private final ReqNodeAttributeOptionRepository reqNodeAttributeOptionRepository;

    private static final Pattern TRACK_HEADER_PATTERN = Pattern.compile("(?i)^\\s*tracks?\\b");
    private static final Pattern TRACK_OPTION_PATTERN =
            Pattern.compile("(?i)^(?:track\\s+)?(?:[A-Z]|\\d+)\\s*[:.)-].*");
    private static final Pattern CHOICES_CAN_ALSO_PATTERN =
            Pattern.compile("(?i)choices?\\s+(?:can|may)\\s+also\\s+be\\s+made\\s+from\\b");

    @Override
    public RequirementTreeResponse getRequirementTree(Long programId, Integer catalogYear, String catalogLabel) {
        log.debug("Getting requirement tree programId={} catalogYear={} catalogLabel={}", programId, catalogYear, catalogLabel);
        ProgramCatalogVersion catalogVersion =
                programQueryService.resolveCatalogVersion(programId, catalogYear, catalogLabel);
        if (catalogVersion == null) {
            return new RequirementTreeResponse(programId, catalogYear, catalogLabel, null, null);
        }

        Integer resolvedYear = catalogVersion.getCatalogYear();
        String resolvedLabel = catalogVersion.getVersionLabel();

        List<ReqNode> nodes =
                reqNodeRepository.findWithParentByCatalogVersionId(catalogVersion.getId());
        if (nodes.isEmpty()) {
            return new RequirementTreeResponse(programId, resolvedYear, resolvedLabel, catalogVersion.getId(), null);
        }

        Map<Long, List<ReqNode>> childrenByParentId = new HashMap<>();
        List<ReqNode> roots = new ArrayList<>();
        for (ReqNode node : nodes) {
            if (node.getParent() == null) {
                roots.add(node);
            } else {
                Long parentId = node.getParent().getId();
                childrenByParentId.computeIfAbsent(parentId, key -> new ArrayList<>()).add(node);
            }
        }

        childrenByParentId.values().forEach(children ->
                children.sort(Comparator.comparing(ReqNode::getOrderIndex, Comparator.nullsLast(Integer::compareTo)))
        );

        List<Long> nodeIds = nodes.stream().map(ReqNode::getId).toList();
        Map<Long, List<RequirementCourseOptionDto>> courseOptionsByNode = buildCourseOptionMap(nodeIds);
        Map<Long, List<RequirementAttributeOptionDto>> attributeOptionsByNode = buildAttributeOptionMap(nodeIds);

        RequirementNodeDto root;
        if (roots.size() == 1) {
            root = toDto(roots.get(0), childrenByParentId, courseOptionsByNode, attributeOptionsByNode);
        } else {
            List<RequirementNodeDto> children = roots.stream()
                    .map(node -> toDto(node, childrenByParentId, courseOptionsByNode, attributeOptionsByNode))
                    .toList();
            root = RequirementNodeDto.builder()
                    .name("Requirements")
                    .kind("ALL_OF")
                    .children(children)
                    .build();
        }

        root = sanitizeTree(root);

        return new RequirementTreeResponse(
                programId,
                resolvedYear,
                resolvedLabel,
                catalogVersion.getId(),
                root
        );
    }

    private RequirementNodeDto toDto(
            ReqNode node,
            Map<Long, List<ReqNode>> childrenByParentId,
            Map<Long, List<RequirementCourseOptionDto>> courseOptionsByNode,
            Map<Long, List<RequirementAttributeOptionDto>> attributeOptionsByNode
    ) {
        RequirementNodeDto dto = RequirementNodeDto.builder()
                .id(node.getId())
                .name(node.getName())
                .kind(node.getKind() != null ? node.getKind().name() : null)
                .nRequired(node.getNRequired())
                .creditMin(node.getCreditMin())
                .creditMax(node.getCreditMax())
                .notes(node.getNotes())
                .levelMin(node.getLevelMin() != null ? node.getLevelMin().intValue() : null)
                .levelCap100(node.getLevelCap100())
                .levelCap200(node.getLevelCap200())
                .levelCap300(node.getLevelCap300())
                .levelCap400(node.getLevelCap400())
                .levelCap100Courses(node.getLevelCap100Courses() != null ? node.getLevelCap100Courses().intValue() : null)
                .levelCap200Courses(node.getLevelCap200Courses() != null ? node.getLevelCap200Courses().intValue() : null)
                .levelCap300Courses(node.getLevelCap300Courses() != null ? node.getLevelCap300Courses().intValue() : null)
                .levelCap400Courses(node.getLevelCap400Courses() != null ? node.getLevelCap400Courses().intValue() : null)
                .gradeMin(node.getGradeMin())
                .residencyCredits(node.getResidencyCredits())
                .doubleCountAllowed(node.getDoubleCountAllowed())
                .doubleCountCap(node.getDoubleCountCap())
                .footnoteText(node.getFootnoteText())
                .source(new RequirementSourceDto(node.getSourceUrl(), node.getSourceRef()))
                .courseOptions(courseOptionsByNode.getOrDefault(node.getId(), List.of()))
                .attributeOptions(attributeOptionsByNode.getOrDefault(node.getId(), List.of()))
                .build();

        List<ReqNode> children = childrenByParentId.getOrDefault(node.getId(), List.of());
        if (!children.isEmpty()) {
            List<RequirementNodeDto> childDtos = children.stream()
                    .map(child -> toDto(child, childrenByParentId, courseOptionsByNode, attributeOptionsByNode))
                    .toList();
            dto.setChildren(childDtos);
        }

        return dto;
    }

    private RequirementNodeDto sanitizeTree(RequirementNodeDto root) {
        if (root == null) return null;
        Set<String> referencedFootnotes = new HashSet<>();
        collectFootnotes(root, referencedFootnotes);
        sanitizeNode(root, referencedFootnotes);
        return root;
    }

    private void collectFootnotes(RequirementNodeDto node, Set<String> referencedFootnotes) {
        if (node == null) return;
        if (node.getFootnoteText() != null && !"NOTE_ONLY".equals(node.getKind())) {
            String[] parts = node.getFootnoteText().split("\\n");
            for (String part : parts) {
                String normalized = normalizeNoteText(part);
                if (!normalized.isEmpty()) {
                    referencedFootnotes.add(normalized);
                }
            }
        }
        if (node.getChildren() == null) return;
        for (RequirementNodeDto child : node.getChildren()) {
            collectFootnotes(child, referencedFootnotes);
        }
    }

    private void sanitizeNode(RequirementNodeDto node, Set<String> referencedFootnotes) {
        if (node == null) return;

        if (isBlank(node.getName())) {
            node.setName("Requirement");
        } else if ("unnamed requirement block".equalsIgnoreCase(node.getName().trim())) {
            node.setName("Required Courses");
        }

        List<RequirementNodeDto> children = node.getChildren();
        if (children != null && !children.isEmpty()) {
            List<RequirementNodeDto> cleaned = new ArrayList<>();
            Set<String> seenNotes = new HashSet<>();
            boolean isTrackGroup = isTrackGroupNode(node);
            RequirementNodeDto lastRequirement = null;

            for (int i = 0; i < children.size(); i++) {
                RequirementNodeDto child = children.get(i);
                sanitizeNode(child, referencedFootnotes);

                if (isTrackGroup && isTrackOptionNode(child)) {
                    child.setKind("TRACK_OPTION");
                }

                if (isChoicesAlsoNode(child) && lastRequirement != null) {
                    List<RequirementNodeDto> extras = new ArrayList<>();
                    int j = i + 1;
                    while (j < children.size()) {
                        RequirementNodeDto candidate = children.get(j);
                        sanitizeNode(candidate, referencedFootnotes);
                        if (!isCourseOptionNode(candidate)) {
                            break;
                        }
                        extras.add(candidate);
                        j++;
                    }

                    if (!extras.isEmpty()) {
                        if (canMergeChoicesIntoRequirement(lastRequirement, extras)) {
                            mergeCourseOptions(lastRequirement, extras);
                        } else {
                            List<RequirementNodeDto> noteChildren = new ArrayList<>();
                            if (child.getChildren() != null) {
                                noteChildren.addAll(child.getChildren());
                            }
                            noteChildren.addAll(extras);
                            child.setChildren(noteChildren);
                            attachChild(lastRequirement, child);
                        }
                        i = j - 1;
                        continue;
                    }
                }

                if (isNoteNode(child)) {
                    String noteKey = noteKeyForNode(child);
                    if (noteKey.isEmpty()) {
                        continue;
                    }
                    if ("footnote".equalsIgnoreCase(normalizeNoteText(child.getName()))
                            && referencedFootnotes.contains(noteKey)) {
                        continue;
                    }
                    if (seenNotes.contains(noteKey)) {
                        continue;
                    }
                    seenNotes.add(noteKey);
                }

                cleaned.add(child);
                if (!isNoteNode(child)) {
                    lastRequirement = child;
                }
            }

            node.setChildren(cleaned);
        }

        if (isTrackGroupNode(node) && node.getChildren() != null) {
            long trackOptions = node.getChildren().stream()
                    .filter(child -> "TRACK_OPTION".equals(child.getKind()))
                    .count();
            if (trackOptions > 0) {
                String kind = node.getKind();
                if (kind == null || "ALL_OF".equals(kind) || "ANY_OF".equals(kind)) {
                    node.setKind("ANY_OF");
                    if (node.getNRequired() == null) {
                        node.setNRequired(1);
                    }
                }
            }
        }
    }

    private boolean isNoteNode(RequirementNodeDto node) {
        return "NOTE_ONLY".equals(node.getKind());
    }

    private boolean isTrackGroupNode(RequirementNodeDto node) {
        if (node == null || isBlank(node.getName())) return false;
        return TRACK_HEADER_PATTERN.matcher(node.getName()).find();
    }

    private boolean isTrackOptionNode(RequirementNodeDto node) {
        if (node == null || isBlank(node.getName())) return false;
        return TRACK_OPTION_PATTERN.matcher(node.getName()).find();
    }

    private boolean isChoicesAlsoNode(RequirementNodeDto node) {
        if (node == null || !isNoteNode(node)) return false;
        String text = firstNonBlank(node.getName(), node.getNotes());
        if (isBlank(text)) return false;
        return CHOICES_CAN_ALSO_PATTERN.matcher(text).find();
    }

    private boolean isCourseOptionNode(RequirementNodeDto node) {
        if (node == null) return false;
        if (node.getCourseOptions() != null && !node.getCourseOptions().isEmpty()) {
            return true;
        }
        List<RequirementNodeDto> children = node.getChildren();
        if (children == null || children.isEmpty()) return false;
        return children.stream().allMatch(child ->
                child.getCourseOptions() != null && !child.getCourseOptions().isEmpty()
        );
    }

    private boolean canMergeChoicesIntoRequirement(RequirementNodeDto requirement, List<RequirementNodeDto> extras) {
        if (requirement == null) return false;
        String kind = requirement.getKind();
        if ("ANY_OF".equals(kind) || "N_OF".equals(kind)) {
            return false;
        }
        if (requirement.getChildren() != null && !requirement.getChildren().isEmpty()) {
            return false;
        }
        return extras.stream().allMatch(extra ->
                extra.getChildren() == null || extra.getChildren().isEmpty()
        );
    }

    private void mergeCourseOptions(RequirementNodeDto requirement, List<RequirementNodeDto> extras) {
        if (requirement.getCourseOptions() == null) {
            requirement.setCourseOptions(new ArrayList<>());
        }
        Set<String> seen = new HashSet<>();
        for (RequirementCourseOptionDto option : requirement.getCourseOptions()) {
            if (option != null && option.getCode() != null) {
                seen.add(option.getCode());
            }
        }
        for (RequirementNodeDto extra : extras) {
            if (extra.getCourseOptions() == null) continue;
            for (RequirementCourseOptionDto option : extra.getCourseOptions()) {
                if (option == null || option.getCode() == null) continue;
                if (seen.add(option.getCode())) {
                    requirement.getCourseOptions().add(option);
                }
            }
        }
    }

    private void attachChild(RequirementNodeDto parent, RequirementNodeDto child) {
        if (parent.getChildren() == null) {
            parent.setChildren(new ArrayList<>());
        }
        parent.getChildren().add(child);
    }

    private String noteKeyForNode(RequirementNodeDto node) {
        String candidate = firstNonBlank(node.getNotes(), node.getFootnoteText(), node.getName());
        return normalizeNoteText(candidate);
    }

    private String normalizeNoteText(String value) {
        if (value == null) return "";
        return value.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!isBlank(value)) return value;
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Map<Long, List<RequirementCourseOptionDto>> buildCourseOptionMap(List<Long> nodeIds) {
        Map<Long, List<RequirementCourseOptionDto>> map = new HashMap<>();
        if (nodeIds.isEmpty()) {
            return map;
        }

        List<ReqNodeCourseOption> options =
                reqNodeCourseOptionRepository.findWithCourseByReqNodeIds(nodeIds);

        for (ReqNodeCourseOption option : options) {
            Long nodeId = option.getReqNode().getId();
            Course course = option.getCourse();
            Subject subject = course.getSubject();
            String code = subject != null
                    ? subject.getCode() + " " + course.getNumber()
                    : course.getNumber();

            RequirementCourseOptionDto dto = new RequirementCourseOptionDto(
                    code,
                    course.getTitle(),
                    course.getCredits()
            );

            map.computeIfAbsent(nodeId, key -> new ArrayList<>()).add(dto);
        }

        map.values().forEach(list ->
                list.sort(Comparator.comparing(RequirementCourseOptionDto::getCode, Comparator.nullsLast(String::compareTo)))
        );

        return map;
    }

    private Map<Long, List<RequirementAttributeOptionDto>> buildAttributeOptionMap(List<Long> nodeIds) {
        Map<Long, List<RequirementAttributeOptionDto>> map = new HashMap<>();
        if (nodeIds.isEmpty()) {
            return map;
        }

        List<ReqNodeAttributeOption> options =
                reqNodeAttributeOptionRepository.findWithAttributeByReqNodeIds(nodeIds);

        for (ReqNodeAttributeOption option : options) {
            Long nodeId = option.getReqNode().getId();
            RequirementAttributeOptionDto dto = new RequirementAttributeOptionDto(
                    option.getAttribute().getCode(),
                    option.getAttribute().getName(),
                    option.getAttribute().getCategory()
            );

            map.computeIfAbsent(nodeId, key -> new ArrayList<>()).add(dto);
        }

        map.values().forEach(list ->
                list.sort(Comparator.comparing(RequirementAttributeOptionDto::getCode, Comparator.nullsLast(String::compareTo)))
        );

        return map;
    }
}
