import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { AlertTriangle, GripVertical, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { type PlanCourse } from "@/store/planStore";

export type PlanCourseWarning = {
    label: string;
    tone: "amber" | "rose";
};

export function PlanCourseCard({
    course,
    warning,
    dragInstructionId,
    onRemove,
    className,
}: {
    course: PlanCourse;
    warning?: PlanCourseWarning | null;
    dragInstructionId?: string;
    onRemove?: (courseId: string) => void;
    className?: string;
}) {
    const {
        setNodeRef,
        setActivatorNodeRef,
        attributes,
        listeners,
        transform,
        transition,
        isDragging,
    } = useSortable({
        id: course.id,
        data: { course },
    });

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.5 : 1,
    };

    return (
        <Card
            ref={setNodeRef}
            style={style}
            className={cn(
                "flex items-center justify-between gap-3 border border-brandBorder bg-brandSurface p-3 shadow-sm transition-shadow hover:shadow-md",
                warning ? "border-amber-200" : "",
                className
            )}
        >
            <button
                ref={setActivatorNodeRef}
                type="button"
                className="flex flex-1 items-start gap-2 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brandPrimary/70 focus-visible:ring-offset-2"
                {...attributes}
                {...listeners}
                aria-describedby={dragInstructionId}
            >
                <GripVertical className="mt-1 h-4 w-4 text-gray-400" />
                <div>
                    <p className="text-sm font-semibold text-gray-900">{course.code}</p>
                    <p className="text-xs text-gray-500">{course.title}</p>
                    <p className="text-xs text-gray-500">{course.credits} credits</p>
                </div>
            </button>

            <div className="flex items-center gap-2">
                {warning ? (
                    <span
                        className={cn(
                            "inline-flex items-center gap-1 rounded-full px-2 py-1 text-[10px] font-semibold uppercase tracking-widest",
                            warning.tone === "amber"
                                ? "bg-amber-100 text-amber-700"
                                : "bg-rose-100 text-rose-700"
                        )}
                    >
                        <AlertTriangle className="h-3 w-3" />
                        {warning.label}
                    </span>
                ) : null}
                {onRemove ? (
                    <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => onRemove(course.id)}
                        className="text-gray-400 hover:text-red-600"
                    >
                        <X className="h-4 w-4" />
                    </Button>
                ) : null}
            </div>
        </Card>
    );
}
