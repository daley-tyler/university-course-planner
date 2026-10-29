import { cn } from "@/lib/utils";
import { CourseChecklistItem } from "./CourseChecklistItem";
import { StatusIcon } from "./StatusIcon";
import type { CourseOptionGroup } from "./requirementStatus";
import { isGroupSatisfied } from "./requirementStatus";
import type { Plan } from "@/store/planStore";
import { planHasCourse } from "@/store/planStore";
import { Plus } from "lucide-react";

type Props = {
    group: CourseOptionGroup;
    plan: Plan;
    planCourses: Set<string>;
    onAddCourse: (code: string, title?: string, credits?: number) => void;
};

export function ChoiceGroup({ group, plan, planCourses, onAddCourse }: Props) {
    const satisfied = isGroupSatisfied(group, planCourses);

    if (group.kind === "single") {
        const course = group.courses[0];
        if (!course) return null;
        return (
            <CourseChecklistItem
                course={course}
                inPlan={planHasCourse(plan, course.code)}
                onAdd={() => onAddCourse(course.code, course.title ?? undefined, course.credits ?? undefined)}
            />
        );
    }

    const isBundle = group.kind === "bundle";
    const allInPlan = group.courses.every((c) => planHasCourse(plan, c.code));
    const someInPlan = !allInPlan && group.courses.some((c) => planHasCourse(plan, c.code));
    const subtitle = isBundle ? "Take all courses" : "Choose one";

    // Determine status: bundles can be partially satisfied (IN_PROGRESS)
    const groupStatus: "DONE" | "IN_PROGRESS" | "NEEDED" = satisfied
        ? "DONE"
        : someInPlan
          ? "IN_PROGRESS"
          : "NEEDED";

    return (
        <div
            className={cn(
                "rounded-lg border px-3 py-2",
                groupStatus === "DONE"
                    ? "border-emerald-200 bg-emerald-50/50"
                    : groupStatus === "IN_PROGRESS"
                      ? "border-amber-200 bg-amber-50/50"
                      : "border-gray-200 bg-gray-50/50"
            )}
        >
            <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                    <StatusIcon status={groupStatus} size="sm" />
                    <div>
                        <p className="text-xs font-semibold text-gray-900">{group.label}</p>
                        <p className="text-[11px] text-gray-500">
                            {subtitle}
                            {group.kind === "crosslisted" ? " (cross-listed)" : ""}
                        </p>
                    </div>
                </div>
                {isBundle && !allInPlan && (
                    <button
                        type="button"
                        onClick={() => {
                            group.courses.forEach((c) => {
                                if (!planHasCourse(plan, c.code))
                                    onAddCourse(c.code, c.title ?? undefined, c.credits ?? undefined);
                            });
                        }}
                        className="flex items-center gap-1 rounded-md border border-gray-200 px-2 py-1 text-[11px] font-medium text-gray-600 transition hover:border-brandPrimary/40 hover:text-brandPrimary"
                    >
                        <Plus className="h-3 w-3" />
                        Add all
                    </button>
                )}
            </div>
            <div className="mt-1.5 space-y-0.5">
                {group.courses.map((course) => (
                    <CourseChecklistItem
                        key={course.code}
                        course={course}
                        inPlan={planHasCourse(plan, course.code)}
                        onAdd={() => onAddCourse(course.code, course.title ?? undefined, course.credits ?? undefined)}
                    />
                ))}
            </div>
        </div>
    );
}
