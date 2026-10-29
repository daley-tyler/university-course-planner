import { useDroppable } from "@dnd-kit/core";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { CourseCard } from "@/components/CourseCard";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/EmptyState";
import { type Course } from "@/types";

interface CompletedCoursesCardProps {
    courses: Course[];
    onSearchClick?: () => void;
    dragInstructionId?: string;
}

export function CompletedCoursesCard({
    courses,
    onSearchClick,
    dragInstructionId,
}: CompletedCoursesCardProps) {
    const { setNodeRef, isOver } = useDroppable({ id: "completed" });

    return (
        <Card
            ref={setNodeRef}
            aria-label="Completed courses"
            className={`border border-gray-300 shadow-sm transition ${
                isOver ? "bg-green-50 border-green-400" : "bg-white/80"
            }`}
        >
            <CardHeader className="flex justify-between items-center">
                <CardTitle className="text-lg font-semibold text-brandPrimary">
                    Completed Courses
                </CardTitle>
                {onSearchClick && (
                    <Button
                        onClick={onSearchClick}
                        variant="outline"
                        size="sm"
                        className="border-brandPrimary text-brandPrimary hover:bg-brandPrimary hover:text-white"
                    >
                        + Add
                    </Button>
                )}
            </CardHeader>

            <CardContent className="flex flex-col gap-2">
                {courses.length === 0 ? (
                    <EmptyState
                        title="No completed courses"
                        description="Drag finished courses here to keep your plan accurate."
                    />
                ) : (
                    courses.map((course) => (
                        <CourseCard
                            key={course.id}
                            course={course}
                            dragInstructionId={dragInstructionId}
                        />
                    ))
                )}
            </CardContent>
        </Card>
    );
}
