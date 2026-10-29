import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { useDroppable } from "@dnd-kit/core";
import { CourseCard } from "@/components/CourseCard";
import { EmptyState } from "@/components/EmptyState";
import { Search } from "lucide-react";
import { type Course } from "@/types";

interface RemainingCoursesCardProps {
    courses: Course[];
    onSearchClick: () => void;
    dragInstructionId?: string;
}

export function RemainingCoursesCard({
    courses,
    onSearchClick,
    dragInstructionId,
}: RemainingCoursesCardProps) {
    // Droppable only applies to the outer container, not the SortableContext
    const { setNodeRef, isOver } = useDroppable({ id: "remaining" });

    return (
        <Card
            ref={setNodeRef}
            id="remaining"
            aria-label="Remaining courses"
            className={`shadow-md border transition min-h-[60vh] bg-white/80 ${
                isOver
                    ? "ring-4 ring-brandPrimary/80 ring-offset-2 ring-offset-white drop-shadow-[0_0_12px_rgba(193,0,26,0.45)]"
                    : "border-brandPrimary/30"
            }`}
        >
            {/* ---- Header ---- */}
            <CardHeader className="flex justify-between items-center">
                <CardTitle className="text-xl font-semibold text-brandPrimary">
                    Remaining Courses
                </CardTitle>

                <Button
                    size="sm"
                    variant="outline"
                    className="border-brandPrimary text-brandPrimary hover:bg-brandPrimary hover:text-white"
                    onClick={onSearchClick}
                >
                    <Search className="w-4 h-4 mr-2" />
                    Search
                </Button>
            </CardHeader>

            {/* ---- Course List ---- */}
            <CardContent>
                {/* Move SortableContext *inside* a div that is NOT droppable */}
                <div className="p-2 min-h-[200px]">
                    <SortableContext
                        items={courses.map((c) => c.id)}
                        strategy={verticalListSortingStrategy}
                    >
                        <div className="space-y-3">
                            {courses.length === 0 ? (
                                <EmptyState
                                    title="No remaining courses"
                                    description="Search the catalog or drag courses back here."
                                    action={
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            className="border-brandPrimary text-brandPrimary hover:bg-brandPrimary hover:text-white"
                                            onClick={onSearchClick}
                                        >
                                            <Search className="w-4 h-4 mr-2" />
                                            Search courses
                                        </Button>
                                    }
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
                        </div>
                    </SortableContext>
                </div>
            </CardContent>
        </Card>
    );
}
