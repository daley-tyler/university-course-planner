import { useState } from "react";
import { cn } from "@/lib/utils";
import { ChevronRight } from "lucide-react";
import type { RequirementNodeDto } from "@/api/types";
import type { Plan } from "@/store/planStore";
import { StatusIcon } from "./StatusIcon";
import { RequirementNode } from "./RequirementNode";
import {
    getSectionSummary,
    isNoteNode,
    isSectionLabelNode,
    type FilterMode,
    type SectionSummary,
    shouldShowNode,
} from "./requirementStatus";

type Props = {
    node: RequirementNodeDto;
    plan: Plan;
    planCourses: Set<string>;
    filter: FilterMode;
    defaultOpen?: boolean;
    precomputedSummary?: SectionSummary;
    onAddCourse: (code: string, title?: string, credits?: number) => void;
    onSectionVisible?: (name: string) => void;
};

export function RequirementSection({
    node,
    plan,
    planCourses,
    filter,
    defaultOpen = false,
    precomputedSummary,
    onAddCourse,
}: Props) {
    const [isOpen, setIsOpen] = useState(defaultOpen);

    const summary = precomputedSummary ?? getSectionSummary(node, planCourses);
    const label = node.name?.trim() || "Requirements";
    const percent =
        summary.totalCount > 0
            ? Math.round((summary.satisfiedCount / summary.totalCount) * 100)
            : 0;

    const children =
        node.children && node.children.length > 0 ? node.children : [node];

    // Filter out note-only nodes from the main children list for quieter display
    const contentChildren = children.filter(
        (c) => !isNoteNode(c) || isSectionLabelNode(c)
    );
    const noteChildren = children.filter(
        (c) => isNoteNode(c) && !isSectionLabelNode(c)
    );

    // Check if any child passes the filter
    const hasVisibleChildren = children.some((c) =>
        shouldShowNode(c, planCourses, filter)
    );
    if (!hasVisibleChildren && filter !== "all") return null;

    return (
        <div className="rounded-xl border border-brandBorder bg-brandSurface shadow-sm overflow-hidden">
            {/* Accordion header */}
            <button
                type="button"
                onClick={() => setIsOpen((prev) => !prev)}
                className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-brandWash/60"
            >
                <ChevronRight
                    className={cn(
                        "h-4 w-4 flex-shrink-0 text-gray-400 transition-transform duration-200",
                        isOpen && "rotate-90"
                    )}
                />
                <StatusIcon status={summary.status} size="md" />
                <span className="min-w-0 flex-1 text-sm font-semibold text-gray-900 truncate">
                    {label}
                </span>

                {/* Inline progress bar */}
                <div className="hidden sm:flex items-center gap-2 flex-shrink-0">
                    <div className="h-1.5 w-20 overflow-hidden rounded-full bg-gray-100">
                        <div
                            className={cn(
                                "h-full rounded-full transition-all duration-300",
                                summary.status === "DONE"
                                    ? "bg-emerald-500"
                                    : summary.status === "IN_PROGRESS"
                                      ? "bg-amber-400"
                                      : "bg-gray-300"
                            )}
                            style={{ width: `${percent}%` }}
                        />
                    </div>
                    <span className="w-12 text-right text-xs tabular-nums text-gray-400">
                        {summary.satisfiedCount}/{summary.totalCount}
                    </span>
                </div>

                {/* Mobile-only count */}
                <span className="sm:hidden flex-shrink-0 text-xs tabular-nums text-gray-400">
                    {summary.satisfiedCount}/{summary.totalCount}
                </span>
            </button>

            {/* Expanded content */}
            {isOpen && (
                <div className="border-t border-gray-100 px-4 py-3 space-y-1">
                    {/* Content nodes */}
                    {contentChildren.map((child) => (
                        <RequirementNode
                            key={child.id}
                            node={child}
                            plan={plan}
                            planCourses={planCourses}
                            depth={0}
                            filter={filter}
                            onAddCourse={onAddCourse}
                        />
                    ))}

                    {/* Notes section - displayed quietly at the bottom */}
                    {noteChildren.length > 0 && (
                        <details className="mt-3 rounded-md bg-brandWash/60 px-3 py-2 text-xs text-gray-500">
                            <summary className="cursor-pointer font-medium text-gray-600">
                                {noteChildren.length} note{noteChildren.length > 1 ? "s" : ""}
                            </summary>
                            <div className="mt-2 space-y-1.5 leading-relaxed">
                                {noteChildren.map((note) => (
                                    <p key={note.id} className="italic">
                                        {note.notes ?? note.name ?? "Note"}
                                    </p>
                                ))}
                            </div>
                        </details>
                    )}
                </div>
            )}
        </div>
    );
}
