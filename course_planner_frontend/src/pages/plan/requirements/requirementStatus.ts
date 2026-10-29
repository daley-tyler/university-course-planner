import type { RequirementNodeDto } from "@/api/types";

// ── Shared types ──────────────────────────────────────────────────────

export type RequirementStatus = "DONE" | "IN_PROGRESS" | "NEEDED";

export type RequirementStatusInfo = {
    status: RequirementStatus;
    details: string;
    matchedCount: number;
    requiredCount: number;
};

export type SectionSummary = {
    status: RequirementStatus;
    satisfiedCount: number;
    totalCount: number;
    label: string;
};

export type CourseOptionGroupKind = "single" | "crosslisted" | "bundle";

export type CourseOptionGroup = {
    id: string;
    label: string;
    kind: CourseOptionGroupKind;
    courses: RequirementNodeDto["courseOptions"];
};

export type FilterMode = "all" | "remaining" | "completed";

// ── Constants ─────────────────────────────────────────────────────────

const optionalRangePattern = /(^|\D)0\s*[-–]\s*\d+/;
const optionalUpToPattern = /up to\s*\d+/;
const optionalToPattern = /0\s*to\s*\d+/;

export const informationalKinds = new Set([
    "NOTE_ONLY",
    "DOUBLE_COUNT_RULE",
    "COURSE_CAP",
    "LEVEL_AT_LEAST",
    "GRADE_MIN",
    "GPA_AT_LEAST",
    "RESIDENCY_MIN",
    "CONCURRENCY_BLOCK",
    "TRACK_OPTION",
    "EXPERIENCE",
]);

const selectionLabelPattern = /\b(select|choose|any\s+one|any\s+of)\b/i;
const selectionCountPattern =
    /\b(select|choose)\s+(?:at\s+least\s+)?(one|two|three|four|five|six|seven|eight|nine|ten|\d+)\b/i;
const selectAllPattern =
    /\bselect\s+(?:(?:all\s+of\s+the)|all|the)\s+following\b/i;
const genericLabelPattern =
    /\b(one of multiple options|select one of the following|one of the following)\b/i;

const countWordMap: Record<string, number> = {
    one: 1, two: 2, three: 3, four: 4, five: 5,
    six: 6, seven: 7, eight: 8, nine: 9, ten: 10,
};

const bundleLabelPattern = /(&|\band\b|\bplus\b)/i;

// ── Node classification helpers ───────────────────────────────────────

const isOptionalText = (text?: string | null) => {
    if (!text) return false;
    const normalized = text.toLowerCase();
    return (
        optionalRangePattern.test(normalized) ||
        optionalUpToPattern.test(normalized) ||
        optionalToPattern.test(normalized)
    );
};

export const isOptionalNode = (node: RequirementNodeDto) => {
    if (node.creditMin !== null && node.creditMin !== undefined && node.creditMin <= 0)
        return true;
    if (node.nRequired !== null && node.nRequired !== undefined && node.nRequired <= 0)
        return true;
    return isOptionalText(node.name) || isOptionalText(node.notes);
};

export const isSelectionNode = (node: RequirementNodeDto) => {
    if (selectAllPattern.test(node.name ?? "") || selectAllPattern.test(node.notes ?? ""))
        return false;
    if (node.nRequired !== null && node.nRequired !== undefined) return true;
    if (node.kind === "ANY_OF" || node.kind === "N_OF") return true;
    return (
        selectionLabelPattern.test(node.name ?? "") ||
        selectionLabelPattern.test(node.notes ?? "")
    );
};

export const isNoteNode = (node: RequirementNodeDto) => node.kind === "NOTE_ONLY";

const isShortLabel = (text?: string | null) => {
    if (!text) return false;
    const trimmed = text.trim();
    if (!trimmed || trimmed.length > 60) return false;
    return !/[.!?]/.test(trimmed);
};

export const isSectionLabelNode = (node: RequirementNodeDto) => {
    if (!isNoteNode(node)) return false;
    if ((node.children?.length ?? 0) > 0) return false;
    return isShortLabel(node.notes ?? node.name);
};

export const isLeafCourseNode = (node: RequirementNodeDto) =>
    (node.courseOptions?.length ?? 0) > 0 &&
    (node.attributeOptions?.length ?? 0) === 0 &&
    (node.children?.length ?? 0) === 0;

export const isBundleNode = (node: RequirementNodeDto) => {
    if (node.kind !== "ALL_OF") return false;
    if ((node.courseOptions?.length ?? 0) > 0) return false;
    if ((node.attributeOptions?.length ?? 0) > 0) return false;
    const children = node.children ?? [];
    if (children.length === 0) return false;
    if (!children.every(isLeafCourseNode)) return false;
    return bundleLabelPattern.test(node.name ?? "");
};

export const isStructuralGroup = (node: RequirementNodeDto) => {
    const hasChildren = (node.children?.length ?? 0) > 0;
    const hasCourses = (node.courseOptions?.length ?? 0) > 0;
    const hasAttributes = (node.attributeOptions?.length ?? 0) > 0;
    return hasChildren && !hasCourses && !hasAttributes;
};

// ── Label & guidance helpers ──────────────────────────────────────────

export const inferSelectionCount = (text?: string | null) => {
    if (!text) return null;
    const match = text.toLowerCase().match(selectionCountPattern);
    if (!match) return null;
    const token = match[2];
    if (token in countWordMap) return countWordMap[token];
    const numeric = Number(token);
    return Number.isFinite(numeric) ? numeric : null;
};

export const getDisplayLabel = (node: RequirementNodeDto) => {
    const name = node.name?.trim() ?? "";
    const note = node.notes?.trim() ?? "";
    if (!name || genericLabelPattern.test(name)) {
        if (isShortLabel(note)) return note;
        return "Requirement";
    }
    return name;
};

export type GuidanceInfo = {
    text: string;
    kind: "all" | "pick" | "credits" | "info";
    pickCount?: number;
    totalOptions?: number;
};

export const getGuidance = (
    node: RequirementNodeDto,
    courseGroups: CourseOptionGroup[] | null,
    effectiveCourseOptions: RequirementNodeDto["courseOptions"]
): GuidanceInfo => {
    if (selectAllPattern.test(node.name ?? "") || selectAllPattern.test(node.notes ?? "")) {
        return { text: "Take all of these", kind: "all" };
    }
    if (isSelectionNode(node)) {
        const required =
            node.nRequired ??
            inferSelectionCount(node.name) ??
            inferSelectionCount(node.notes) ??
            1;
        const totalOptions =
            courseGroups?.length ??
            effectiveCourseOptions.length ??
            node.children?.length ??
            0;
        const courseWord = required === 1 ? "course" : "courses";
        return {
            text: `Pick ${required} ${courseWord}`,
            kind: "pick",
            pickCount: required,
            totalOptions,
        };
    }
    if (node.creditMin !== null && node.creditMin !== undefined) {
        return {
            text: `${node.creditMin} credits needed`,
            kind: "credits",
        };
    }
    if (node.kind === "ALL_OF") {
        return { text: "Take all of these", kind: "all" };
    }
    return { text: "See details", kind: "info" };
};

// ── Course grouping ───────────────────────────────────────────────────

const parseCourseCode = (code: string) => {
    const parts = code.trim().split(/\s+/);
    if (parts.length < 2) return { subject: null, number: null };
    const number = parts.pop() ?? null;
    const subject = parts.join(" ").trim() || null;
    return { subject, number };
};

const normalizeTitle = (title?: string | null) => (title ?? "").trim().toLowerCase();

const buildBundleGroup = (node: RequirementNodeDto): CourseOptionGroup | null => {
    if (!isBundleNode(node)) return null;
    const courses = (node.children ?? [])
        .flatMap((child) => child.courseOptions)
        .filter((option) => option?.code);
    const unique = new Map(courses.map((c) => [c.code, c]));
    const deduped = Array.from(unique.values());
    if (deduped.length === 0) return null;
    const label =
        node.name?.trim() || deduped.map((c) => c.code).join(" + ");
    return { id: `${node.id}-bundle`, label, kind: "bundle", courses: deduped };
};

const buildCourseGroup = (
    options: RequirementNodeDto["courseOptions"],
    labelHint?: string | null
): CourseOptionGroup => {
    const parsed = options.map((o) => ({
        ...parseCourseCode(o.code),
        code: o.code,
        title: o.title ?? null,
    }));
    const numbers = new Set(parsed.map((p) => p.number).filter(Boolean) as string[]);
    const subjects = new Set(parsed.map((p) => p.subject).filter(Boolean) as string[]);
    const titles = new Set(parsed.map((p) => normalizeTitle(p.title)).filter(Boolean));

    if (options.length === 1) {
        return { id: options[0].code, label: options[0].code, kind: "single", courses: options };
    }
    if (numbers.size === 1 && titles.size <= 1) {
        const number = Array.from(numbers)[0] ?? "";
        const label = number
            ? `${Array.from(subjects).join("/") || "Course"} ${number}`.trim()
            : options.map((o) => o.code).join(" / ");
        return { id: label, label, kind: "crosslisted", courses: options };
    }
    const label =
        labelHint && !selectionLabelPattern.test(labelHint)
            ? labelHint
            : options.map((o) => o.code).join(" + ");
    return { id: label, label, kind: "bundle", courses: options };
};

export const getCourseGroups = (
    node: RequirementNodeDto
): CourseOptionGroup[] | null => {
    if (isSelectionNode(node)) {
        const children = node.children ?? [];
        if (
            children.length > 0 &&
            children.every((c) => isLeafCourseNode(c) || isBundleNode(c))
        ) {
            return children
                .map((child) =>
                    isLeafCourseNode(child)
                        ? buildCourseGroup(child.courseOptions, child.name ?? node.name)
                        : buildBundleGroup(child)
                )
                .filter(Boolean) as CourseOptionGroup[];
        }
    }
    if (isSelectionNode(node) && isLeafCourseNode(node)) {
        return node.courseOptions.map((option) => buildCourseGroup([option]));
    }
    const bundleGroup = buildBundleGroup(node);
    if (bundleGroup) return [bundleGroup];
    if (isLeafCourseNode(node)) {
        return [buildCourseGroup(node.courseOptions, node.name)];
    }
    return null;
};

// ── Status calculation ────────────────────────────────────────────────

export const isGroupSatisfied = (
    group: CourseOptionGroup,
    planCourses: Set<string>
) => {
    const codes = group.courses.map((c) => c.code);
    if (group.kind === "bundle") return codes.every((c) => planCourses.has(c));
    return codes.some((c) => planCourses.has(c));
};

const getGroupCredits = (group: CourseOptionGroup) => {
    if (group.kind === "bundle") {
        const credits = group.courses.map((c) => c.credits);
        if (credits.some((v) => v === null || v === undefined)) return null;
        return credits.reduce((sum: number, v) => sum + (v ?? 0), 0);
    }
    return group.courses[0]?.credits ?? null;
};

export const getLeafStatus = (
    node: RequirementNodeDto,
    planCourses: Set<string>
): RequirementStatusInfo => {
    if (informationalKinds.has(node.kind ?? "")) {
        return { status: "DONE", details: "Informational", matchedCount: 0, requiredCount: 0 };
    }

    const optional = isOptionalNode(node);
    const courseOptions = node.courseOptions ?? [];
    const hasAttributeOptions = (node.attributeOptions?.length ?? 0) > 0;
    const matchedCourses = courseOptions.filter((c) => planCourses.has(c.code));
    const matchedCount = matchedCourses.length;

    if (node.creditMin !== null && node.creditMin !== undefined) {
        const matchedCredits = matchedCourses.reduce((s, c) => s + (c.credits ?? 0), 0);
        if (optional && matchedCredits === 0)
            return { status: "DONE", details: "Optional", matchedCount: 0, requiredCount: 0 };
        if (matchedCredits >= node.creditMin && (node.kind !== "ALL_OF" || matchedCount === courseOptions.length))
            return { status: "DONE", details: `${matchedCredits}/${node.creditMin} cr`, matchedCount, requiredCount: 0 };
        if (matchedCount > 0)
            return { status: "IN_PROGRESS", details: `${matchedCredits}/${node.creditMin} cr`, matchedCount, requiredCount: 0 };
        return {
            status: optional ? "DONE" : "NEEDED",
            details: optional ? "Optional" : `0/${node.creditMin} cr`,
            matchedCount, requiredCount: 0,
        };
    }

    const inferredRequired = isSelectionNode(node)
        ? inferSelectionCount(node.name) ?? inferSelectionCount(node.notes)
        : null;
    const requiredCount =
        node.nRequired ??
        inferredRequired ??
        (node.kind === "ALL_OF" && courseOptions.length > 0 ? courseOptions.length : 1);

    if (optional) {
        return {
            status: "DONE",
            details: matchedCount === 0 ? "Optional" : `Optional (${matchedCount} added)`,
            matchedCount, requiredCount,
        };
    }
    if (courseOptions.length === 0) {
        if (hasAttributeOptions)
            return { status: "IN_PROGRESS", details: "Attribute requirement", matchedCount: 0, requiredCount: 0 };
        return { status: "NEEDED", details: "No courses listed", matchedCount: 0, requiredCount: 0 };
    }
    if (matchedCount >= requiredCount)
        return { status: "DONE", details: `${matchedCount}/${requiredCount} added`, matchedCount, requiredCount };
    if (matchedCount > 0)
        return { status: "IN_PROGRESS", details: `${matchedCount}/${requiredCount} added`, matchedCount, requiredCount };
    return { status: "NEEDED", details: `${requiredCount} needed`, matchedCount, requiredCount };
};

export const getSelectionGroupStatus = (
    node: RequirementNodeDto,
    planCourses: Set<string>,
    groups: CourseOptionGroup[]
): RequirementStatusInfo => {
    const optional = isOptionalNode(node);
    const requiredCount =
        node.nRequired ?? inferSelectionCount(node.name) ?? inferSelectionCount(node.notes) ?? 1;
    const satisfiedGroups = groups.filter((g) => isGroupSatisfied(g, planCourses));
    const matchedCount = satisfiedGroups.length;

    if (node.creditMin !== null && node.creditMin !== undefined) {
        const credits = satisfiedGroups.map((g) => getGroupCredits(g));
        if (!credits.some((v) => v === null || v === undefined)) {
            const matchedCredits = credits.reduce((s: number, v) => s + (v ?? 0), 0);
            if (optional && matchedCredits === 0)
                return { status: "DONE", details: "Optional", matchedCount, requiredCount };
            if (matchedCredits >= node.creditMin && matchedCount >= requiredCount)
                return { status: "DONE", details: `${matchedCredits}/${node.creditMin} cr`, matchedCount, requiredCount };
            if (matchedCount > 0)
                return { status: "IN_PROGRESS", details: `${matchedCredits}/${node.creditMin} cr`, matchedCount, requiredCount };
        } else if (matchedCount > 0) {
            return { status: optional ? "DONE" : "IN_PROGRESS", details: "Credit values need review", matchedCount, requiredCount };
        }
    }

    if (optional) {
        return {
            status: "DONE",
            details: matchedCount === 0 ? "Optional" : `Optional (${matchedCount} added)`,
            matchedCount, requiredCount,
        };
    }
    if (matchedCount >= requiredCount)
        return { status: "DONE", details: `${matchedCount}/${requiredCount} picked`, matchedCount, requiredCount };
    if (matchedCount > 0)
        return { status: "IN_PROGRESS", details: `${matchedCount}/${requiredCount} picked`, matchedCount, requiredCount };
    return { status: "NEEDED", details: `Pick ${requiredCount}`, matchedCount, requiredCount };
};

export const collectLeafStatuses = (
    node: RequirementNodeDto,
    planCourses: Set<string>
): RequirementStatusInfo[] => {
    if (informationalKinds.has(node.kind ?? "")) return [];
    const courseGroups = getCourseGroups(node);
    if (courseGroups && isSelectionNode(node))
        return [getSelectionGroupStatus(node, planCourses, courseGroups)];
    if (courseGroups) {
        const bundle = courseGroups.length === 1 && courseGroups[0].kind === "bundle";
        if (bundle) {
            const pseudo = { ...node, kind: "ALL_OF", courseOptions: courseGroups[0].courses };
            return [getLeafStatus(pseudo, planCourses)];
        }
        return [getLeafStatus(node, planCourses)];
    }
    const hasLeaf =
        (node.courseOptions?.length ?? 0) > 0 || (node.attributeOptions?.length ?? 0) > 0;
    if (hasLeaf || (node.children?.length ?? 0) === 0)
        return [getLeafStatus(node, planCourses)];
    return (node.children ?? []).flatMap((c) => collectLeafStatuses(c, planCourses));
};

export const getSectionSummary = (
    node: RequirementNodeDto,
    planCourses: Set<string>
): SectionSummary => {
    const statuses = collectLeafStatuses(node, planCourses);
    const totalCount = statuses.length;
    const satisfiedCount = statuses.filter((s) => s.status === "DONE").length;

    let status: RequirementStatus = "NEEDED";
    if (totalCount > 0 && satisfiedCount === totalCount) status = "DONE";
    else if (satisfiedCount > 0) status = "IN_PROGRESS";

    const label =
        totalCount === 0
            ? "No requirements"
            : `${satisfiedCount} of ${totalCount} covered in plan`;

    return { status, satisfiedCount, totalCount, label };
};

/** Pre-compute section summaries once, keyed by node id. */
export const precomputeSectionSummaries = (
    sectionNodes: RequirementNodeDto[],
    planCourses: Set<string>
): Map<string | number, SectionSummary> => {
    const map = new Map<string | number, SectionSummary>();
    for (const node of sectionNodes) {
        map.set(node.id, getSectionSummary(node, planCourses));
    }
    return map;
};

export const getNodeStatus = (
    node: RequirementNodeDto,
    planCourses: Set<string>
): RequirementStatusInfo => {
    const courseGroups = getCourseGroups(node);
    const hasChildren = (node.children?.length ?? 0) > 0 && !courseGroups;

    if (hasChildren) {
        const summary = getSectionSummary(node, planCourses);
        return {
            status: summary.status,
            details: summary.label,
            matchedCount: summary.satisfiedCount,
            requiredCount: summary.totalCount,
        };
    }
    if (courseGroups && isSelectionNode(node))
        return getSelectionGroupStatus(node, planCourses, courseGroups);
    if (courseGroups && courseGroups.length === 1 && courseGroups[0].kind === "bundle")
        return getLeafStatus(
            { ...node, kind: "ALL_OF", courseOptions: courseGroups[0].courses },
            planCourses
        );
    return getLeafStatus(node, planCourses);
};

// ── Filtering helpers ─────────────────────────────────────────────────

export const shouldShowNode = (
    node: RequirementNodeDto,
    planCourses: Set<string>,
    filter: FilterMode
): boolean => {
    if (filter === "all") return true;

    // For structural groups (has children, no direct courses), recurse:
    // a parent should be visible if any descendant matches the filter.
    if (isStructuralGroup(node)) {
        return (node.children ?? []).some((child) =>
            shouldShowNode(child, planCourses, filter)
        );
    }

    const status = getNodeStatus(node, planCourses).status;
    if (filter === "completed") return status === "DONE";
    return status !== "DONE";
};

// ── Notes helpers ─────────────────────────────────────────────────────

export const getNodeNotes = (node: RequirementNodeDto): string[] => {
    const candidates = [node.notes, node.footnoteText]
        .map((t) => t?.trim())
        .filter(Boolean) as string[];
    const label = getDisplayLabel(node).trim().toLowerCase();
    return Array.from(new Set(candidates)).filter(
        (n) => n.trim().toLowerCase() !== label
    );
};
