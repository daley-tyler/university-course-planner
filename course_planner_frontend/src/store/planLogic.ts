import type { Plan, PlanCourse, PlanTerm, ValidationIssue, ValidationResult } from "./planTypes";

export const buildTerms = (terms: PlanTerm[], courses: PlanCourse[]) => {
    const termMap = new Map(terms.map((term) => [term.id, term]));
    const updatedTerms = terms.map((term) => ({
        ...term,
        courseIds: term.courseIds.filter((courseId) =>
            courses.some((course) => course.id === courseId && course.scheduledTermId === term.id)
        ),
    }));

    courses.forEach((course) => {
        if (!course.scheduledTermId) return;
        const term = termMap.get(course.scheduledTermId);
        if (!term) return;
        const termIndex = updatedTerms.findIndex((item) => item.id === term.id);
        if (termIndex === -1) return;
        if (!updatedTerms[termIndex].courseIds.includes(course.id)) {
            updatedTerms[termIndex].courseIds.push(course.id);
        }
    });

    return updatedTerms;
};


export function copyPlanAsWhatIf(source: Plan, id: string, createId: (prefix: string) => string): Plan {
    const termIds = new Map(source.terms.map((term) => [term.id, createId("term")]));
    const courses = source.courses.map((course) => ({
        ...course,
        id: createId("course"),
        prereqs: course.prereqs ? [...course.prereqs] : undefined,
        scheduledTermId: course.scheduledTermId ? termIds.get(course.scheduledTermId) ?? null : null,
    }));
    const courseIds = new Map(source.courses.map((course, i) => [course.id, courses[i].id]));
    const terms = source.terms.map((term) => ({
        ...term,
        id: termIds.get(term.id)!,
        courseIds: term.courseIds.flatMap((courseId) => courseIds.get(courseId) ?? []),
    }));
    return {
        ...source, id, name: `${source.name} What-If`, kind: "WHAT_IF", parentPlanId: source.id,
        programs: source.programs.map((program) => ({ ...program })),
        courses, terms: buildTerms(terms, courses),
    };
}

// This checks only the flattened prerequisite codes supplied by the catalog API.
export function hasPrerequisiteViolation(course: PlanCourse, plan: Plan, termId: string, fallback: string[] = []): boolean {
    if (course.status === "COMPLETED") return false;
    const target = plan.terms.find((term) => term.id === termId);
    return (course.prereqs ?? fallback).some((code) => {
        const prerequisite = plan.courses.find((candidate) => candidate.code === code);
        if (prerequisite?.status === "COMPLETED") return false;
        const earlier = plan.terms.find((term) => term.id === prerequisite?.scheduledTermId);
        return !target || !earlier || earlier.order >= target.order;
    });
}

export function checkPlan(plan: Plan, createId: (prefix: string) => string): ValidationResult {
    const terms = new Set(plan.terms.map((term) => term.id));
    const unscheduled = plan.courses.filter((course) =>
        course.status !== "COMPLETED" && (!course.scheduledTermId || !terms.has(course.scheduledTermId))
    );
    const overloaded = plan.terms.filter((term) => {
        const credits = plan.courses.filter((course) => course.scheduledTermId === term.id)
            .reduce((total, course) => total + course.credits, 0);
        return credits > (term.creditCap ?? 18);
    });
    const issues: ValidationIssue[] = unscheduled.map((course) => ({
        id: createId("issue"), kind: "COURSE", title: `${course.code} not scheduled`,
        description: "Move this planned course into a term.", courseCode: course.code,
    }));
    overloaded.forEach((term) => issues.push({
        id: createId("issue"), kind: "SCHEDULE", title: `${term.name} exceeds credit cap`,
        description: "Reduce credits or move courses to another term.",
    }));
    return {
        status: issues.length === 0 ? "PASS" : issues.length > 3 ? "FAIL" : "WARN",
        checkedAt: new Date().toISOString(),
        requirementSummary: "Degree requirements, grades and residency are not checked here.",
        scheduleSummary: `${unscheduled.length} planned course(s) unscheduled. Class times and offerings are not checked.`,
        creditSummary: overloaded.length ? `${overloaded.length} term(s) exceed credit limits.` : "All terms are within their credit caps.",
        issues,
    };
}

export const nextTermOrder = (terms: PlanTerm[]) => Math.max(0, ...terms.map((term) => term.order)) + 1;
