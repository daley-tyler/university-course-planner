import { useContext } from "react";
import { mockCatalog } from "@/mockData/mockCatalog";
import { PlanStoreContext } from "./planContext";
import { buildTerms } from "./planLogic";
import type { Plan } from "./planTypes";
export type * from "./planTypes";

// Legacy fallback for callers that do not provide catalog details.
const courseByCode = new Map(mockCatalog.map((course) => [course.code, course]));

export function usePlanStore() {
    const context = useContext(PlanStoreContext);
    if (!context) {
        throw new Error("usePlanStore must be used within PlanStoreProvider");
    }
    return context;
}

export function useActivePlan() {
    const { state } = usePlanStore();
    return state.plans.find((plan) => plan.id === state.activePlanId) ?? state.plans[0];
}

export function usePlanById(planId: string) {
    const { state } = usePlanStore();
    return state.plans.find((plan) => plan.id === planId);
}

export function planHasCourse(plan: Plan | undefined, courseCode: string) {
    if (!plan) return false;
    return plan.courses.some((course) => course.code === courseCode);
}

export function getCourseByCode(code: string) {
    return courseByCode.get(code);
}

export function normalizePlan(plan: Plan) {
    const terms = buildTerms(plan.terms, plan.courses);
    return { ...plan, terms };
}
