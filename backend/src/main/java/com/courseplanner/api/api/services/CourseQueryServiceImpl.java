package com.courseplanner.api.api.services;

import com.courseplanner.api.api.dto.AttributeDto;
import com.courseplanner.api.api.dto.CourseDetailDto;
import com.courseplanner.api.api.dto.CourseFiltersResponse;
import com.courseplanner.api.api.dto.CourseSearchRequest;
import com.courseplanner.api.api.dto.CourseSearchResponse;
import com.courseplanner.api.api.dto.CourseSummaryDto;
import com.courseplanner.api.api.dto.DepartmentDto;
import com.courseplanner.api.model.entities.catalog.Attribute;
import com.courseplanner.api.model.entities.catalog.Course;
import com.courseplanner.api.model.entities.catalog.CourseAttribute;
import com.courseplanner.api.model.entities.catalog.Subject;
import com.courseplanner.api.model.entities.prereq.CoursePrerequisiteCourse;
import com.courseplanner.api.model.entities.requirements.ReqNode;
import com.courseplanner.api.model.entities.requirements.ReqNodeCourseOption;
import com.courseplanner.api.model.repositories.AttributeRepository;
import com.courseplanner.api.model.repositories.CourseAttributeRepository;
import com.courseplanner.api.model.repositories.CoursePrerequisiteCourseRepository;
import com.courseplanner.api.model.repositories.CourseRepository;
import com.courseplanner.api.model.repositories.ReqNodeCourseOptionRepository;
import com.courseplanner.api.model.repositories.ReqNodeRepository;
import com.courseplanner.api.model.repositories.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseQueryServiceImpl implements CourseQueryService {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 100;

    private final CourseRepository courseRepository;
    private final CourseAttributeRepository courseAttributeRepository;
    private final CoursePrerequisiteCourseRepository coursePrerequisiteCourseRepository;
    private final ReqNodeRepository reqNodeRepository;
    private final ReqNodeCourseOptionRepository reqNodeCourseOptionRepository;
    private final SubjectRepository subjectRepository;
    private final AttributeRepository attributeRepository;

    @Override
    public CourseSearchResponse search(CourseSearchRequest request) {
        log.debug("Course search: q={} departments={} page={}", request.getQ(), request.getDepartments(), request.getPage());
        int page = request.getPage() == null ? 1 : Math.max(1, request.getPage());
        int pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
        pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        List<Long> requirementCourseIds = resolveRequirementCourseIds(request.getRequirementId());
        if (request.getRequirementId() != null && requirementCourseIds.isEmpty()) {
            return new CourseSearchResponse(List.of(), page, pageSize, 0, null);
        }

        Specification<Course> spec = buildSpecification(request, requirementCourseIds);
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("subject.code").and(Sort.by("number")));

        Page<Course> results = courseRepository.findAll(spec, pageable);

        List<Course> courses = results.getContent();
        Map<Long, List<String>> attributesByCourseId = loadAttributes(courses);
        Map<Long, List<String>> prereqsByCourseId = loadPrereqs(courses);

        List<CourseSummaryDto> items = courses.stream()
                .map(course -> toSummaryDto(course, attributesByCourseId, prereqsByCourseId))
                .toList();

        Integer nextPage = results.hasNext() ? page + 1 : null;

        return new CourseSearchResponse(
                items,
                page,
                pageSize,
                results.getTotalElements(),
                nextPage
        );
    }

    @Override
    public CourseDetailDto getCourse(String courseCode) {
        CourseCode parsed = parseCourseCode(courseCode);
        if (parsed == null) {
            return null;
        }

        Course course = courseRepository
                .findBySubject_CodeAndNumber(parsed.subjectCode, parsed.number)
                .orElse(null);
        if (course == null) {
            return null;
        }

        Map<Long, List<String>> attributesByCourseId = loadAttributes(List.of(course));
        Map<Long, List<String>> prereqsByCourseId = loadPrereqs(List.of(course));
        List<String> attributes = attributesByCourseId.getOrDefault(course.getId(), List.of());
        List<String> prereqs = prereqsByCourseId.getOrDefault(course.getId(), List.of());

        String department = course.getSubject() != null ? course.getSubject().getCode() : null;
        Integer level = deriveLevel(course.getNumber());

        return new CourseDetailDto(
                course.getId(),
                buildCourseCode(course),
                course.getTitle(),
                course.getCredits(),
                department,
                level,
                attributes,
                prereqs,
                course.getDescription(),
                course.getPrereqText(),
                course.getCoreqText(),
                course.getRestrictionText(),
                course.getAttributeText()
        );
    }

    @Override
    public CourseFiltersResponse getFilters(Integer catalogYear) {
        List<DepartmentDto> departments = subjectRepository.findAll().stream()
                .sorted(Comparator.comparing(Subject::getCode))
                .map(subject -> new DepartmentDto(subject.getCode(), subject.getName()))
                .toList();

        List<AttributeDto> attributes = attributeRepository.findAll().stream()
                .sorted(Comparator.comparing(Attribute::getCode))
                .map(attr -> new AttributeDto(attr.getCode(), attr.getName(), attr.getCategory()))
                .toList();

        Set<Integer> levels = new HashSet<>();
        for (String number : courseRepository.findDistinctNumbers()) {
            Integer level = deriveLevel(number);
            if (level != null) {
                levels.add(level);
            }
        }

        List<Integer> sortedLevels = levels.stream()
                .sorted()
                .toList();

        return new CourseFiltersResponse(departments, sortedLevels, attributes);
    }

    private Specification<Course> buildSpecification(CourseSearchRequest request, List<Long> requirementCourseIds) {
        String query = sanitizeQuery(request.getQ());
        List<String> departments = normalizeList(request.getDepartments());
        List<Integer> levels = normalizeLevels(request.getLevels());
        List<String> attributes = normalizeList(request.getAttributes());
        BigDecimal minCredits = request.getMinCredits();
        BigDecimal maxCredits = request.getMaxCredits();

        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(query)) {
                Join<Course, Subject> subjectJoin = root.join("subject");
                String like = "%" + query.toLowerCase(Locale.US) + "%";
                Predicate codeMatch = cb.like(
                        cb.lower(cb.concat(subjectJoin.get("code"), cb.concat(" ", root.get("number")))),
                        like
                );
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), like);
                predicates.add(cb.or(codeMatch, titleMatch));
            }

            if (!departments.isEmpty()) {
                Join<Course, Subject> subjectJoin = root.join("subject");
                predicates.add(subjectJoin.get("code").in(departments));
            }

            if (!levels.isEmpty()) {
                List<String> digits = levels.stream()
                        .map(level -> String.valueOf(level / 100))
                        .distinct()
                        .toList();
                predicates.add(cb.substring(root.get("number"), 1, 1).in(digits));
            }

            if (minCredits != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("credits"), minCredits));
            }

            if (maxCredits != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("credits"), maxCredits));
            }

            if (!attributes.isEmpty()) {
                criteriaQuery.distinct(true);
                Subquery<Long> attrSubquery = criteriaQuery.subquery(Long.class);
                var attrRoot = attrSubquery.from(CourseAttribute.class);
                Join<CourseAttribute, Attribute> attrJoin = attrRoot.join("attribute", JoinType.INNER);
                attrSubquery.select(attrRoot.get("course").get("id"))
                        .where(attrJoin.get("code").in(attributes));
                predicates.add(root.get("id").in(attrSubquery));
            }

            if (requirementCourseIds != null) {
                predicates.add(root.get("id").in(requirementCourseIds));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<Long, List<String>> loadAttributes(List<Course> courses) {
        if (courses.isEmpty()) {
            return Map.of();
        }

        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        List<CourseAttribute> attributes = courseAttributeRepository.findByCourseIdsWithAttribute(courseIds);

        Map<Long, Set<String>> byCourseId = new HashMap<>();
        for (CourseAttribute attribute : attributes) {
            Long courseId = attribute.getCourse().getId();
            String code = attribute.getAttribute().getCode();
            byCourseId.computeIfAbsent(courseId, key -> new HashSet<>()).add(code);
        }

        Map<Long, List<String>> result = new HashMap<>();
        byCourseId.forEach((courseId, codes) -> {
            List<String> sorted = codes.stream().sorted().toList();
            result.put(courseId, sorted);
        });

        return result;
    }

    private Map<Long, List<String>> loadPrereqs(List<Course> courses) {
        if (courses.isEmpty()) {
            return Map.of();
        }

        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        List<CoursePrerequisiteCourse> prereqs =
                coursePrerequisiteCourseRepository.findByCourseIdsWithCourses(courseIds);

        Map<Long, Set<String>> byCourseId = new HashMap<>();
        for (CoursePrerequisiteCourse prereq : prereqs) {
            Course course = prereq.getGroup().getCourse();
            Course required = prereq.getRequiredCourse();
            if (course == null || required == null || required.getSubject() == null) {
                continue;
            }
            String code = required.getSubject().getCode() + " " + required.getNumber();
            byCourseId.computeIfAbsent(course.getId(), key -> new HashSet<>()).add(code);
        }

        Map<Long, List<String>> result = new HashMap<>();
        byCourseId.forEach((courseId, codes) -> {
            List<String> sorted = codes.stream().sorted().toList();
            result.put(courseId, sorted);
        });

        return result;
    }

    private CourseSummaryDto toSummaryDto(
            Course course,
            Map<Long, List<String>> attributesByCourseId,
            Map<Long, List<String>> prereqsByCourseId
    ) {
        String department = course.getSubject() != null ? course.getSubject().getCode() : null;
        Integer level = deriveLevel(course.getNumber());

        return new CourseSummaryDto(
                course.getId(),
                buildCourseCode(course),
                course.getTitle(),
                course.getCredits(),
                department,
                level,
                attributesByCourseId.getOrDefault(course.getId(), List.of()),
                prereqsByCourseId.getOrDefault(course.getId(), List.of())
        );
    }

    private List<Long> resolveRequirementCourseIds(Long requirementId) {
        if (requirementId == null) {
            return null;
        }

        ReqNode root = reqNodeRepository.findWithCatalogVersionById(requirementId).orElse(null);
        if (root == null || root.getCatalogVersion() == null) {
            return List.of();
        }

        List<ReqNode> nodes =
                reqNodeRepository.findWithParentByCatalogVersionId(root.getCatalogVersion().getId());

        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (ReqNode node : nodes) {
            if (node.getParent() != null) {
                Long parentId = node.getParent().getId();
                childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(node.getId());
            }
        }

        Set<Long> targetNodeIds = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(root.getId());

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!targetNodeIds.add(current)) {
                continue;
            }
            List<Long> children = childrenByParent.getOrDefault(current, List.of());
            queue.addAll(children);
        }

        if (targetNodeIds.isEmpty()) {
            return List.of();
        }

        List<ReqNodeCourseOption> options =
                reqNodeCourseOptionRepository.findWithCourseByReqNodeIds(new ArrayList<>(targetNodeIds));

        return options.stream()
                .map(option -> option.getCourse().getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String sanitizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        String trimmed = query.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            return trimmed.substring(0, MAX_QUERY_LENGTH);
        }
        return trimmed;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.US))
                .distinct()
                .toList();
    }

    private List<Integer> normalizeLevels(List<Integer> levels) {
        if (levels == null) {
            return List.of();
        }
        return levels.stream()
                .filter(Objects::nonNull)
                .filter(level -> level >= 0)
                .distinct()
                .toList();
    }

    private Integer deriveLevel(String number) {
        if (!StringUtils.hasText(number)) {
            return null;
        }
        char first = number.trim().charAt(0);
        if (!Character.isDigit(first)) {
            return null;
        }
        int digit = Character.getNumericValue(first);
        return digit * 100;
    }

    private String buildCourseCode(Course course) {
        if (course.getSubject() == null) {
            return course.getNumber();
        }
        return course.getSubject().getCode() + " " + course.getNumber();
    }

    private CourseCode parseCourseCode(String courseCode) {
        if (!StringUtils.hasText(courseCode)) {
            return null;
        }
        String trimmed = courseCode.trim().toUpperCase(Locale.US);
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        String subject = parts[0];
        String number = trimmed.substring(subject.length()).trim();
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(number)) {
            return null;
        }
        return new CourseCode(subject, number);
    }

    private record CourseCode(String subjectCode, String number) {}
}
