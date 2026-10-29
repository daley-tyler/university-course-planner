import { toast } from "sonner";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { arrayMove } from "@dnd-kit/sortable";
import type { Plan, PlanActions, PlanState, PlanProgram, PlanCourse, PlanTerm } from "./planTypes";
import { PlanStoreContext } from "./planContext";
import { getCourseByCode } from "./planStore";
import { buildTerms, copyPlanAsWhatIf, checkPlan, nextTermOrder } from "./planLogic";

const STORAGE_KEY = "course-planner/plan-store-v1";
const STORAGE_VERSION = 1;

const createId = (prefix: string) =>
    `${prefix}_${Math.random().toString(36).slice(2, 8)}${Date.now().toString(36).slice(-4)}`;

const programKey = (program: PlanProgram) => `${program.id}::${program.catalogYear}`;

const dedupePrograms = (programs: PlanProgram[]) => {
    const seen = new Set<string>();
    return programs.filter((program) => {
        const key = programKey(program);
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
};

const defaultState: PlanState = {
    currentPlanId: "plan_current",
    activePlanId: "plan_current",
    plans: [
        {
            id: "plan_current",
            name: "Current Plan",
            kind: "CURRENT",
            programs: [],
            courses: [],
            terms: [
                {
                    id: "term_fall_2025",
                    name: "Fall 2025",
                    order: 1,
                    courseIds: [],
                    creditCap: 18,
                },
                {
                    id: "term_spring_2026",
                    name: "Spring 2026",
                    order: 2,
                    courseIds: [],
                    creditCap: 18,
                },
            ],
        },
        {
            id: "plan_whatif",
            name: "What-If Plan",
            kind: "WHAT_IF",
            parentPlanId: "plan_current",
            programs: [],
            courses: [],
            terms: [
                {
                    id: "wf_term_fall_2025",
                    name: "Fall 2025",
                    order: 1,
                    courseIds: [],
                    creditCap: 18,
                },
                {
                    id: "wf_term_spring_2026",
                    name: "Spring 2026",
                    order: 2,
                    courseIds: [],
                    creditCap: 18,
                },
            ],
        },
    ],
    validationByPlan: {},
    focus: { type: null, value: null },
};

type StoredState = { version: number; data: PlanState };

const normalizeState = (state: PlanState | null | undefined): PlanState => {
    if (!state || !Array.isArray(state.plans) || state.plans.length === 0) {
        return defaultState;
    }

    const plans = state.plans.map((plan) => {
        const programs = dedupePrograms(plan.programs ?? []);
        const courses = plan.courses ?? [];
        const terms = buildTerms(plan.terms ?? [], courses);
        return { ...plan, programs, courses, terms };
    });

    let currentPlanId = state.currentPlanId;
    if (!plans.some((plan) => plan.id === currentPlanId)) {
        currentPlanId = plans.find((plan) => plan.kind === "CURRENT")?.id ?? plans[0].id;
    }

    let activePlanId = state.activePlanId;
    if (!plans.some((plan) => plan.id === activePlanId)) {
        activePlanId = currentPlanId;
    }

    const normalizedPlans = plans.map((plan) => {
        if (plan.id === currentPlanId) {
            return { ...plan, kind: "CURRENT" as const };
        }
        if (plan.kind === "CURRENT") {
            return { ...plan, kind: "WHAT_IF" as const };
        }
        return plan;
    });

    return {
        ...state,
        plans: normalizedPlans,
        currentPlanId,
        activePlanId,
        validationByPlan: {},
        focus: state.focus ?? { type: null, value: null },
    };
};

const loadState = (): PlanState => {
    if (typeof window === "undefined") return defaultState;
    try {
        const raw = window.localStorage.getItem(STORAGE_KEY);
        if (!raw) return defaultState;
        const parsed = JSON.parse(raw) as StoredState;
        if (parsed.version !== STORAGE_VERSION) return defaultState;
        return normalizeState(parsed.data);
    } catch {
        return defaultState;
    }
};

export function PlanStoreProvider({ children }: { children: React.ReactNode }) {
    const [state, setRawState] = useState<PlanState>(() => loadState());
    const plansRef = useRef(state.plans);
    plansRef.current = state.plans;
    const setState = useCallback((update: (previous: PlanState) => PlanState) => {
        setRawState((previous) => {
            const next = update(previous);
            const changed = new Set(next.plans.filter((plan) =>
                previous.plans.find((old) => old.id === plan.id) !== plan
            ).map((plan) => plan.id));
            if (!changed.size) return next;
            return { ...next, validationByPlan: Object.fromEntries(
                Object.entries(next.validationByPlan).filter(([id]) => !changed.has(id))
            ) };
        });
    }, []);

    const storageWarningShown = useRef(false);
    useEffect(() => {
        if (typeof window === "undefined") return;
        // Exclude ephemeral UI state (focus) from persistence
        const dataToStore = { ...state, focus: { type: null, value: null } };
        const payload: StoredState = { version: STORAGE_VERSION, data: dataToStore as PlanState };
        try {
            window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
            storageWarningShown.current = false;
        } catch {
            if (!storageWarningShown.current) {
                toast.error("Your changes are only in this tab. Browser storage is unavailable or full.");
                storageWarningShown.current = true;
            }
        }
    }, [state]);

    const setActivePlanId = useCallback((planId: string) => {
        setState((prev) => ({ ...prev, activePlanId: planId }));
    }, [setState]);

    const renamePlan = useCallback((planId: string, name: string) => {
        setState((prev) => ({
            ...prev,
            plans: prev.plans.map((plan) =>
                plan.id === planId ? { ...plan, name } : plan
            ),
        }));
    }, [setState]);

    const removePlan = useCallback((planId: string) => {
        setState((prev) => {
            const target = prev.plans.find((plan) => plan.id === planId);
            if (!target || target.kind === "CURRENT" || planId === prev.currentPlanId) {
                return prev;
            }
            const nextPlans = prev.plans.filter((plan) => plan.id !== planId);
            if (nextPlans.length === 0) {
                return prev;
            }
            const activePlanId = nextPlans.some((plan) => plan.id === prev.activePlanId)
                ? prev.activePlanId
                : prev.currentPlanId;
            const validationByPlan = { ...prev.validationByPlan };
            delete validationByPlan[planId];
            return { ...prev, plans: nextPlans, activePlanId, validationByPlan };
        });
    }, [setState]);

    const addCourseToPlan = useCallback((
        planId: string,
        courseCode: string,
        details?: { title?: string; credits?: number; prereqs?: string[] }
    ) => {
        setState((prev) => {
            const plans = prev.plans.map((plan) => {
                if (plan.id !== planId) return plan;
                if (plan.courses.some((course) => course.code === courseCode)) return plan;

                const catalogCourse = getCourseByCode(courseCode);
                const newCourse: PlanCourse = {
                    id: createId("course"),
                    code: courseCode,
                    title: details?.title ?? catalogCourse?.title ?? courseCode,
                    credits: details?.credits ?? catalogCourse?.credits ?? 0,
                    status: "PLANNED",
                    scheduledTermId: null,
                    prereqs: details?.prereqs ?? catalogCourse?.prereqs,
                };

                return {
                    ...plan,
                    courses: [...plan.courses, newCourse],
                };
            });
            return { ...prev, plans };
        });
    }, [setState]);

    const removeCourseFromPlan = useCallback((planId: string, planCourseId: string) => {
        setState((prev) => {
            const plans = prev.plans.map((plan) => {
                if (plan.id !== planId) return plan;
                const nextCourses = plan.courses.filter((course) => course.id !== planCourseId);
                const nextTerms = plan.terms.map((term) => ({
                    ...term,
                    courseIds: term.courseIds.filter((id) => id !== planCourseId),
                }));
                return { ...plan, courses: nextCourses, terms: nextTerms };
            });
            return { ...prev, plans };
        });
    }, [setState]);

    const moveCourseToTerm = useCallback(
        (planId: string, planCourseId: string, termId: string | null, overId?: string) => {
            setState((prev) => {
                const plans = prev.plans.map((plan) => {
                    if (plan.id !== planId) return plan;

                    const updatedCourses = plan.courses.map((course) =>
                        course.id === planCourseId
                            ? { ...course, scheduledTermId: termId }
                            : course
                    );

                    let updatedTerms = plan.terms.map((term) => ({
                        ...term,
                        courseIds: term.courseIds.filter((id) => id !== planCourseId),
                    }));

                    if (termId) {
                        updatedTerms = updatedTerms.map((term) => {
                            if (term.id !== termId) return term;
                            if (term.courseIds.includes(planCourseId)) return term;

                            const nextIds = [...term.courseIds];
                            if (overId && nextIds.includes(overId)) {
                                const index = nextIds.indexOf(overId);
                                nextIds.splice(index, 0, planCourseId);
                            } else {
                                nextIds.push(planCourseId);
                            }
                            return { ...term, courseIds: nextIds };
                        });
                    }

                    return {
                        ...plan,
                        courses: updatedCourses,
                        terms: updatedTerms,
                    };
                });
                return { ...prev, plans };
            });
        },
        [setState]
    );

    const reorderCourseInTerm = useCallback(
        (planId: string, termId: string, fromId: string, toId: string) => {
            setState((prev) => {
                const plans = prev.plans.map((plan) => {
                    if (plan.id !== planId) return plan;
                    const terms = plan.terms.map((term) => {
                        if (term.id !== termId) return term;
                        const fromIndex = term.courseIds.indexOf(fromId);
                        const toIndex = term.courseIds.indexOf(toId);
                        if (fromIndex === -1 || toIndex === -1) return term;
                        const nextIds = arrayMove(term.courseIds, fromIndex, toIndex);
                        return { ...term, courseIds: nextIds };
                    });
                    return { ...plan, terms };
                });
                return { ...prev, plans };
            });
        },
        [setState]
    );

    const reorderUnscheduled = useCallback((planId: string, fromId: string, toId: string) => {
        setState((prev) => {
            const plans = prev.plans.map((plan) => {
                if (plan.id !== planId) return plan;
                const unscheduled = plan.courses.filter((course) => course.scheduledTermId === null);
                const unscheduledIds = unscheduled.map((course) => course.id);
                const fromIndex = unscheduledIds.indexOf(fromId);
                const toIndex = unscheduledIds.indexOf(toId);
                if (fromIndex === -1 || toIndex === -1) return plan;

                const reorderedIds = arrayMove(unscheduledIds, fromIndex, toIndex);
                const unscheduledMap = new Map(unscheduled.map((course) => [course.id, course]));

                // Preserve original interleaving: replace unscheduled courses
                // in their original positions with the reordered sequence.
                let unscheduledIdx = 0;
                const courses = plan.courses.map((course) => {
                    if (course.scheduledTermId !== null) return course;
                    return unscheduledMap.get(reorderedIds[unscheduledIdx++])!;
                });

                return { ...plan, courses };
            });
            return { ...prev, plans };
        });
    }, [setState]);

    const updatePrograms = useCallback((planId: string, programs: PlanProgram[]) => {
        setState((prev) => ({
            ...prev,
            plans: prev.plans.map((plan) =>
                plan.id === planId ? { ...plan, programs: dedupePrograms(programs) } : plan
            ),
        }));
    }, [setState]);

    const removeProgramFromPlan = useCallback(
        (planId: string, programId: string, catalogYear?: string) => {
            setState((prev) => ({
                ...prev,
                plans: prev.plans.map((plan) => {
                    if (plan.id !== planId) return plan;
                    const programs = plan.programs.filter((program) => {
                        if (program.id !== programId) return true;
                        if (catalogYear && program.catalogYear !== catalogYear) return true;
                        return false;
                    });
                    return { ...plan, programs: dedupePrograms(programs) };
                }),
            }));
        },
        [setState]
    );

    const duplicatePlanAsWhatIf = useCallback((sourcePlanId: string) => {
        if (!plansRef.current.some((plan) => plan.id === sourcePlanId)) return "";
        const newPlanId = createId("plan");
        setState((prev) => {
            const sourcePlan = prev.plans.find((plan) => plan.id === sourcePlanId);
            if (!sourcePlan) return prev;
            const newPlan = copyPlanAsWhatIf(sourcePlan, newPlanId, createId);
            return {
                ...prev,
                plans: [...prev.plans, newPlan],
                activePlanId: newPlanId,
            };
        });
        return newPlanId;
    }, [setState]);

    const promotePlanToCurrent = useCallback((planId: string) => {
        setState((prev) => {
            const plans: Plan[] = prev.plans.map((plan) => {
                if (plan.id === planId) return { ...plan, kind: "CURRENT" };
                if (plan.id === prev.currentPlanId) return { ...plan, kind: "WHAT_IF" };
                return plan;
            });
            return { ...prev, plans, currentPlanId: planId, activePlanId: planId };
        });
    }, [setState]);

    const addTerm = useCallback((planId: string, name?: string) => {
        setState((prev) => {
            const plans = prev.plans.map((plan) => {
                if (plan.id !== planId) return plan;
                const nextOrder = nextTermOrder(plan.terms);
                const newTerm: PlanTerm = {
                    id: createId("term"),
                    name: name ?? `Term ${nextOrder}`,
                    order: nextOrder,
                    courseIds: [],
                    creditCap: 18,
                };
                return { ...plan, terms: [...plan.terms, newTerm] };
            });
            return { ...prev, plans };
        });
    }, [setState]);

    const renameTerm = useCallback((planId: string, termId: string, name: string) => {
        setState((prev) => ({
            ...prev,
            plans: prev.plans.map((plan) =>
                plan.id === planId
                    ? {
                        ...plan,
                        terms: plan.terms.map((term) =>
                            term.id === termId ? { ...term, name } : term
                        ),
                    }
                    : plan
            ),
        }));
    }, [setState]);

    const removeTerm = useCallback((planId: string, termId: string) => {
        setState((prev) => {
            const plans = prev.plans.map((plan) => {
                if (plan.id !== planId) return plan;
                const courses = plan.courses.map((course) =>
                    course.scheduledTermId === termId
                        ? { ...course, scheduledTermId: null }
                        : course
                );
                const terms = plan.terms.filter((term) => term.id !== termId);
                return { ...plan, courses, terms };
            });
            return { ...prev, plans };
        });
    }, [setState]);

    const clearPlanCourses = useCallback((planId: string) => {
        setState((prev) => {
            const plans = prev.plans.map((plan) => {
                if (plan.id !== planId) return plan;
                const terms = plan.terms.map((term) => ({ ...term, courseIds: [] }));
                return { ...plan, courses: [], terms };
            });
            const validationByPlan = { ...prev.validationByPlan };
            delete validationByPlan[planId];
            return { ...prev, plans, validationByPlan };
        });
    }, [setState]);

    const validatePlan = useCallback((planId: string) => {
        const plan = plansRef.current.find((candidate) => candidate.id === planId);
        if (!plan) {
            return {
                status: "WARN" as const,
                checkedAt: new Date().toISOString(),
                requirementSummary: "No plan selected.",
                scheduleSummary: "No plan selected.",
                creditSummary: "No plan selected.",
                issues: [],
            };
        }

        const result = checkPlan(plan, createId);

        setState((prev) => ({
            ...prev,
            validationByPlan: { ...prev.validationByPlan, [planId]: result },
        }));

        return result;
    }, [setState]);

    const setFocus = useCallback((focus: { type: "course" | "requirement" | null; value: string | null }) => {
        setState((prev) => ({ ...prev, focus }));
    }, [setState]);

    const actions: PlanActions = useMemo(
        () => ({
            setActivePlanId,
            renamePlan,
            removePlan,
            addCourseToPlan,
            removeCourseFromPlan,
            moveCourseToTerm,
            reorderCourseInTerm,
            reorderUnscheduled,
            updatePrograms,
            removeProgramFromPlan,
            duplicatePlanAsWhatIf,
            promotePlanToCurrent,
            addTerm,
            renameTerm,
            removeTerm,
            validatePlan,
            clearPlanCourses,
            setFocus,
        }),
        [
            setActivePlanId,
            renamePlan,
            removePlan,
            addCourseToPlan,
            removeCourseFromPlan,
            moveCourseToTerm,
            reorderCourseInTerm,
            reorderUnscheduled,
            updatePrograms,
            removeProgramFromPlan,
            duplicatePlanAsWhatIf,
            promotePlanToCurrent,
            addTerm,
            renameTerm,
            removeTerm,
            validatePlan,
            clearPlanCourses,
            setFocus,
        ]
    );

    return <PlanStoreContext.Provider value={{ state, actions }}>{children}</PlanStoreContext.Provider>;
}
