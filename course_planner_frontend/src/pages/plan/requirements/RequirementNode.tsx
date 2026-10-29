import { cn } from "@/lib/utils";
import { Info } from "lucide-react";
import type { RequirementNodeDto } from "@/api/types";
import type { Plan } from "@/store/planStore";
import { planHasCourse } from "@/store/planStore";
import { StatusIcon } from "./StatusIcon";
import { CourseChecklistItem } from "./CourseChecklistItem";
import { ChoiceGroup } from "./ChoiceGroup";
import {
    isNoteNode,
    isSectionLabelNode,
    isOptionalNode,
    isStructuralGroup,
    getDisplayLabel,
    getGuidance,
    getNodeStatus,
    getNodeNotes,
    getCourseGroups,
    shouldShowNode,
    type FilterMode,
} from "./requirementStatus";

type Props = {
    node: RequirementNodeDto;
    plan: Plan;
    planCourses: Set<string>;
    depth: number;
    filter: FilterMode;
    onAddCourse: (code: string, title?: string, credits?: number) => void;
};

export function RequirementNode({
    node,
    plan,
    planCourses,
    depth,
    filter,
    onAddCourse,
}: Props) {
    if (!shouldShowNode(node, planCourses, filter)) return null;

    // Section label nodes: render as a quiet subheading
    if (isSectionLabelNode(node)) {
        const label = (node.notes ?? node.name ?? "").trim();
        return (
            <p className="mt-3 mb-1 text-[11px] font-semibold uppercase tracking-wider text-gray-400">
                {label}
            </p>
        );
    }

    // Note nodes: render as a quiet inline annotation
    if (isNoteNode(node)) {
        const noteText = node.notes ?? node.name ?? "";
        const isShort = noteText.length <= 120;
        return (
            <div className="rounded-md bg-gray-50 px-3 py-2 text-xs text-gray-500">
                <div className="flex items-start gap-2">
                    <Info className="mt-0.5 h-3 w-3 flex-shrink-0 text-gray-400" />
                    {isShort ? (
                        <p className="leading-relaxed italic">{noteText}</p>
                    ) : (
                        <details className="min-w-0 flex-1">
                            <summary className="cursor-pointer font-medium text-gray-600">
                                Note
                            </summary>
                            <p className="mt-1 leading-relaxed">{noteText}</p>
                        </details>
                    )}
                </div>
                {node.children && node.children.length > 0 && (
                    <div className="mt-2 space-y-1 pl-5">
                        {node.children.map((child) => (
                            <RequirementNode
                                key={child.id}
                                node={child}
                                plan={plan}
                                planCourses={planCourses}
                                depth={depth + 1}
                                filter={filter}
                                onAddCourse={onAddCourse}
                            />
                        ))}
                    </div>
                )}
            </div>
        );
    }

    const courseGroups = getCourseGroups(node);
    const effectiveCourseOptions = courseGroups ? [] : node.courseOptions ?? [];
    const hasChildren = !courseGroups && (node.children?.length ?? 0) > 0;
    const hasCourses = courseGroups
        ? courseGroups.length > 0
        : effectiveCourseOptions.length > 0;
    const hasAttributes = (node.attributeOptions?.length ?? 0) > 0;

    const guidance = getGuidance(node, courseGroups, effectiveCourseOptions);
    const label = getDisplayLabel(node);
    const optional = isOptionalNode(node);
    const structural = isStructuralGroup(node);
    const statusInfo = getNodeStatus(node, planCourses);
    const notes = getNodeNotes(node);
    const isPick = guidance.kind === "pick";

    // Leaf course node with a single course: render as just a checklist item
    if (
        !hasChildren &&
        !hasAttributes &&
        courseGroups &&
        courseGroups.length === 1 &&
        courseGroups[0].kind === "single"
    ) {
        const course = courseGroups[0].courses[0];
        if (course) {
            return (
                <CourseChecklistItem
                    course={course}
                    inPlan={planHasCourse(plan, course.code)}
                    onAdd={() =>
                        onAddCourse(
                            course.code,
                            course.title ?? undefined,
                            course.credits ?? undefined
                        )
                    }
                />
            );
        }
    }

    return (
        <div
            className={cn(
                "rounded-lg",
                depth === 0 && "border border-gray-200 bg-white px-4 py-3",
                depth > 0 && !isPick && "mt-2",
                depth > 0 && isPick && "mt-2 rounded-lg border-l-4 border-l-amber-400 bg-amber-50/40 px-3 py-2.5",
                depth > 0 && !isPick && structural && "border-l-2 border-l-gray-200 pl-3",
            )}
        >
            {/* Header row */}
            <div className="flex items-start justify-between gap-2">
                <div className="flex items-start gap-2">
                    <StatusIcon status={statusInfo.status} size="md" />
                    <div className="min-w-0">
                        <p className={cn(
                            "font-semibold text-gray-900",
                            depth === 0 ? "text-sm" : "text-xs"
                        )}>
                            {label}
                        </p>
                        {/* Guidance text - prominent for "pick" nodes */}
                        <p
                            className={cn(
                                "text-[11px] mt-0.5",
                                isPick
                                    ? "font-semibold text-amber-700"
                                    : guidance.kind === "credits"
                                      ? "font-medium text-brandPrimary"
                                      : "text-gray-500"
                            )}
                        >
                            {guidance.text}
                            {optional && (
                                <span className="ml-1.5 text-gray-400">(optional)</span>
                            )}
                        </p>
                    </div>
                </div>
                <span className="flex-shrink-0 text-[11px] tabular-nums text-gray-400">
                    {statusInfo.details}
                </span>
            </div>

            {/* Short notes - inline annotations */}
            {notes.length > 0 && (
                <div className="mt-1.5 flex items-start gap-1.5 pl-6">
                    <Info className="mt-0.5 h-3 w-3 flex-shrink-0 text-gray-300" />
                    {notes.length === 1 && notes[0].length <= 140 ? (
                        <p className="text-[11px] italic text-gray-400 leading-relaxed">
                            {notes[0]}
                        </p>
                    ) : (
                        <details className="text-[11px] text-gray-400">
                            <summary className="cursor-pointer font-medium">
                                {notes.length} note{notes.length > 1 ? "s" : ""}
                            </summary>
                            <div className="mt-1 space-y-1 leading-relaxed">
                                {notes.map((n) => (
                                    <p key={n}>{n}</p>
                                ))}
                            </div>
                        </details>
                    )}
                </div>
            )}

            {/* Course options */}
            {hasCourses && (
                <div className="mt-2 space-y-0.5 pl-6">
                    {courseGroups
                        ? courseGroups.map((group) => (
                              <ChoiceGroup
                                  key={group.id}
                                  group={group}
                                  plan={plan}
                                  planCourses={planCourses}
                                  onAddCourse={onAddCourse}
                              />
                          ))
                        : effectiveCourseOptions.map((course) => (
                              <CourseChecklistItem
                                  key={course.code}
                                  course={course}
                                  inPlan={planHasCourse(plan, course.code)}
                                  onAdd={() =>
                                      onAddCourse(
                                          course.code,
                                          course.title ?? undefined,
                                          course.credits ?? undefined
                                      )
                                  }
                              />
                          ))}
                </div>
            )}

            {/* Attribute options */}
            {!hasCourses && hasAttributes && (
                <div className="mt-2 flex flex-wrap gap-1.5 pl-6">
                    {node.attributeOptions.map((attr) => (
                        <span
                            key={attr.code}
                            className="rounded-full border border-gray-200 bg-gray-50 px-2 py-0.5 text-[11px] text-gray-500"
                        >
                            {attr.code}
                            {attr.name ? ` · ${attr.name}` : ""}
                        </span>
                    ))}
                </div>
            )}

            {/* Children */}
            {hasChildren && (
                <div className="mt-2 space-y-1 pl-4">
                    {node.children.map((child) => (
                        <RequirementNode
                            key={child.id}
                            node={child}
                            plan={plan}
                            planCourses={planCourses}
                            depth={depth + 1}
                            filter={filter}
                            onAddCourse={onAddCourse}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}
