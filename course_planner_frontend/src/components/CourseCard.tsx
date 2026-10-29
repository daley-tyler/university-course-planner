import { mockCatalog } from "@/mockData/mockCatalog";
import { useState } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { type Course } from "@/types";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
    DialogTrigger,
} from "@/components/ui/dialog";
import { Info } from "lucide-react";

export function CourseCard({
    course,
    dragInstructionId,
}: {
    course: Course;
    dragInstructionId?: string;
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
        data: { type: "course", course },
    });

    const [open, setOpen] = useState(false);

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.5 : 1,
    };

    // Invented prototype details.
    const mockDescriptions = Object.fromEntries(mockCatalog.map((entry) => [
        entry.code, `Invented ${entry.title.toLowerCase()} exercise for exploring this planner.`,
    ]));

    const description = mockDescriptions[course.code] || "No description available.";

    return (
        <Card
            ref={setNodeRef}
            style={style}
            className="flex justify-between items-center bg-white border border-gray-200 shadow-sm rounded-md p-3 hover:shadow-md transition"
        >
            {/* Draggable area */}
            <button
                ref={setActivatorNodeRef}
                type="button"
                className="flex-1 text-left cursor-grab active:cursor-grabbing select-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brandPrimary/70 focus-visible:ring-offset-2"
                aria-label={`Move ${course.code}`}
                title="Drag to move or press space to lift"
                {...attributes}
                {...listeners}
                aria-describedby={dragInstructionId}
            >
                <p className="font-semibold text-gray-800">{course.code}</p>
                <p className="text-sm text-gray-600">{course.title}</p>
            </button>

            {/* Info button */}
            <div className="flex items-center gap-2">
                <p className="text-sm font-medium text-gray-700">{course.credits} cr</p>
                <Dialog open={open} onOpenChange={setOpen}>
                    <DialogTrigger asChild>
                        <Button
                            variant="ghost"
                            size="sm"
                            className="p-1 text-gray-500 hover:text-brandPrimary cursor-pointer"
                            onClick={(e) => e.stopPropagation()} // prevent drag
                        >
                            <Info className="w-4 h-4" />
                        </Button>
                    </DialogTrigger>
                    <DialogContent className="sm:max-w-md">
                        <DialogHeader>
                            <DialogTitle className="text-lg font-semibold text-brandPrimary">
                                {course.code}: {course.title}
                            </DialogTitle>
                            <DialogDescription>
                                <p className="text-gray-700 mt-2">
                                    <strong>Credits:</strong> {course.credits}
                                </p>
                                <p className="text-gray-700 mt-2 leading-relaxed">{description}</p>
                                <p className="text-gray-700 mt-3 italic">
                                    <strong>Attributes:</strong> General Education Quantitative Reasoning
                                </p>
                                <p className="text-gray-700 mt-1 italic">
                                    <strong>Instructor:</strong> Sample Instructor
                                </p>
                            </DialogDescription>
                        </DialogHeader>
                    </DialogContent>
                </Dialog>
            </div>
        </Card>
    );
}
