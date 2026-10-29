import { cn } from "@/lib/utils";
import type { RequirementNodeDto } from "@/api/types";
import type { FilterMode, SectionSummary } from "./requirementStatus";
import { getSectionSummary, collectLeafStatuses } from "./requirementStatus";
import { StatusIcon } from "./StatusIcon";

type Props = {
    requirementTree: RequirementNodeDto;
    sectionNodes: RequirementNodeDto[];
    planCourses: Set<string>;
    totalPlanCredits: number;
    filter: FilterMode;
    onFilterChange: (filter: FilterMode) => void;
    precomputedSummaries?: Map<string | number, SectionSummary>;
};

const filterOptions: { value: FilterMode; label: string }[] = [
    { value: "all", label: "All" },
    { value: "remaining", label: "Uncovered" },
    { value: "completed", label: "Covered" },
];

export function ProgressSummary({
    requirementTree,
    sectionNodes,
    planCourses,
    totalPlanCredits,
    filter,
    onFilterChange,
    precomputedSummaries,
}: Props) {
    const allStatuses = collectLeafStatuses(requirementTree, planCourses);
    const total = allStatuses.length;
    const done = allStatuses.filter((s) => s.status === "DONE").length;
    const percent = total > 0 ? Math.round((done / total) * 100) : 0;

    const sectionSummaries: (SectionSummary & { name: string; nodeId: string | number })[] =
        sectionNodes.map((node) => ({
            nodeId: node.id,
            name: node.name?.trim() || "Requirements",
            ...(precomputedSummaries?.get(node.id) ?? getSectionSummary(node, planCourses)),
        }));

    return (
        <div className="rounded-xl border border-brandBorder bg-brandSurface shadow-sm">
            <div className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between">
                {/* Overall progress */}
                <div className="flex items-center gap-4">
                    <div className="relative flex h-14 w-14 flex-shrink-0 items-center justify-center">
                        <svg viewBox="0 0 36 36" className="h-14 w-14 -rotate-90">
                            <circle
                                cx="18" cy="18" r="15.5"
                                fill="none"
                                stroke="var(--color-brandBorder)"
                                strokeWidth="3"
                            />
                            <circle
                                cx="18" cy="18" r="15.5"
                                fill="none"
                                stroke="var(--color-brandPrimary)"
                                strokeWidth="3"
                                strokeDasharray={`${percent * 0.974} 97.4`}
                                strokeLinecap="round"
                                className="transition-all duration-500"
                            />
                        </svg>
                        <span className="absolute text-xs font-bold text-gray-900">
                            {percent}%
                        </span>
                    </div>
                    <div>
                        <p className="text-sm font-semibold text-gray-900">
                            {done} of {total} checks covered in plan
                        </p>
                        <p className="text-xs text-gray-500">
                            {totalPlanCredits} credits in plan. Policy notes still need adviser review.
                        </p>
                    </div>
                </div>

                {/* Filter toggle */}
                <div className="flex rounded-lg border border-brandBorder bg-brandWash/60 p-0.5">
                    {filterOptions.map((opt) => (
                        <button
                            key={opt.value}
                            type="button"
                            onClick={() => onFilterChange(opt.value)}
                            className={cn(
                                "rounded-md px-3 py-1 text-xs font-medium transition-colors",
                                filter === opt.value
                                    ? "bg-brandSurface text-gray-900 shadow-sm"
                                    : "text-gray-500 hover:text-gray-700"
                            )}
                        >
                            {opt.label}
                        </button>
                    ))}
                </div>
            </div>

            {/* Per-section mini progress */}
            {sectionSummaries.length > 1 && (
                <div className="border-t border-gray-100 px-4 py-3">
                    <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                        {sectionSummaries.map((section) => {
                            const pct =
                                section.totalCount > 0
                                    ? Math.round(
                                          (section.satisfiedCount / section.totalCount) * 100
                                      )
                                    : 0;
                            return (
                                <div
                                    key={section.nodeId}
                                    className="flex items-center gap-2"
                                >
                                    <StatusIcon status={section.status} size="sm" />
                                    <span className="min-w-0 flex-1 truncate text-xs text-gray-700">
                                        {section.name}
                                    </span>
                                    <div className="h-1.5 w-16 flex-shrink-0 overflow-hidden rounded-full bg-gray-100">
                                        <div
                                            className={cn(
                                                "h-full rounded-full transition-all duration-300",
                                                section.status === "DONE"
                                                    ? "bg-emerald-500"
                                                    : section.status === "IN_PROGRESS"
                                                      ? "bg-amber-400"
                                                      : "bg-gray-300"
                                            )}
                                            style={{ width: `${pct}%` }}
                                        />
                                    </div>
                                    <span className="w-10 flex-shrink-0 text-right text-[11px] tabular-nums text-gray-400">
                                        {section.satisfiedCount}/{section.totalCount}
                                    </span>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
}
