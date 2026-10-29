import { cn } from "@/lib/utils";
import { Plus, Check } from "lucide-react";
import type { RequirementCourseOptionDto } from "@/api/types";

type Props = {
    course: RequirementCourseOptionDto;
    inPlan: boolean;
    onAdd: () => void;
};

export function CourseChecklistItem({ course, inPlan, onAdd }: Props) {
    return (
        <div
            className={cn(
                "group flex items-center gap-3 rounded-md px-2.5 py-1.5 text-sm transition-colors",
                inPlan
                    ? "bg-emerald-50"
                    : "hover:bg-gray-50"
            )}
        >
            {inPlan ? (
                <span className="flex h-4 w-4 flex-shrink-0 items-center justify-center rounded-full bg-emerald-500">
                    <Check className="h-2.5 w-2.5 text-white" strokeWidth={3} />
                </span>
            ) : (
                <span className="h-4 w-4 flex-shrink-0 rounded-full border-2 border-gray-300" />
            )}

            <span className="min-w-[72px] flex-shrink-0 font-mono text-xs font-semibold text-gray-900">
                {course.code}
            </span>

            <span className="min-w-0 flex-1 truncate text-xs text-gray-600">
                {course.title ?? ""}
            </span>

            {course.credits != null && (
                <span className="flex-shrink-0 text-[11px] text-gray-400">
                    {course.credits} cr
                </span>
            )}

            {!inPlan && (
                <button
                    type="button"
                    onClick={onAdd}
                    className="flex-shrink-0 rounded-md border border-gray-200 p-1 text-gray-400 opacity-0 transition-all hover:border-brandPrimary/40 hover:text-brandPrimary group-hover:opacity-100"
                    aria-label={`Add ${course.code} to plan`}
                >
                    <Plus className="h-3 w-3" />
                </button>
            )}
        </div>
    );
}
