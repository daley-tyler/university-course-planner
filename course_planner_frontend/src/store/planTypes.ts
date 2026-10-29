export type PlanKind = "CURRENT" | "WHAT_IF";
export type PlanCourseStatus = "PLANNED" | "COMPLETED";

export type PlanProgram = {
    id: string;
    name: string;
    catalogYear: string;
    advisor?: string;
    programType?: string;
    degreeType?: string;
    college?: string;
    catalogVersionId?: number;
};

export type PlanCourse = {
    id: string;
    code: string;
    title: string;
    credits: number;
    status: PlanCourseStatus;
    scheduledTermId: string | null;
    prereqs?: string[];
};

export type PlanTerm = {
    id: string;
    name: string;
    order: number;
    courseIds: string[];
    creditCap?: number;
};

export type Plan = {
    id: string;
    name: string;
    kind: PlanKind;
    parentPlanId?: string;
    programs: PlanProgram[];
    courses: PlanCourse[];
    terms: PlanTerm[];
};

export type ValidationIssue = {
    id: string;
    kind: "REQUIREMENT" | "COURSE" | "SCHEDULE";
    title: string;
    description: string;
    requirementLabel?: string;
    courseCode?: string;
};

export type ValidationResult = {
    status: "PASS" | "WARN" | "FAIL";
    checkedAt: string;
    requirementSummary: string;
    scheduleSummary: string;
    creditSummary: string;
    issues: ValidationIssue[];
};

export type PlanState = {
    currentPlanId: string;
    activePlanId: string;
    plans: Plan[];
    validationByPlan: Record<string, ValidationResult>;
    focus: { type: "course" | "requirement" | null; value: string | null };
};

export type PlanActions = {
    setActivePlanId: (planId: string) => void;
    renamePlan: (planId: string, name: string) => void;
    removePlan: (planId: string) => void;
    addCourseToPlan: (
        planId: string,
        courseCode: string,
        details?: { title?: string; credits?: number; prereqs?: string[] }
    ) => void;
    removeCourseFromPlan: (planId: string, planCourseId: string) => void;
    moveCourseToTerm: (
        planId: string,
        planCourseId: string,
        termId: string | null,
        overId?: string
    ) => void;
    reorderCourseInTerm: (planId: string, termId: string, fromId: string, toId: string) => void;
    reorderUnscheduled: (planId: string, fromId: string, toId: string) => void;
    updatePrograms: (planId: string, programs: PlanProgram[]) => void;
    removeProgramFromPlan: (planId: string, programId: string, catalogYear?: string) => void;
    duplicatePlanAsWhatIf: (sourcePlanId: string) => string;
    promotePlanToCurrent: (planId: string) => void;
    addTerm: (planId: string, name?: string) => void;
    renameTerm: (planId: string, termId: string, name: string) => void;
    removeTerm: (planId: string, termId: string) => void;
    validatePlan: (planId: string) => ValidationResult;
    clearPlanCourses: (planId: string) => void;
    setFocus: (focus: { type: "course" | "requirement" | null; value: string | null }) => void;
};
