import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { type Course } from "@/types";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { useDroppable } from "@dnd-kit/core";
import { CourseCard } from "./CourseCard";
import { useState } from "react";
import { Check, X } from "lucide-react";
import { EmptyState } from "@/components/EmptyState";

export function SemesterCard({
                                 id,
                                 name,
                                 courses,
                                 totalCredits,
                                 onDelete,
                                 onRename,
                                 dragInstructionId,
                             }: {
    id: string;
    name: string;
    courses: Course[];
    totalCredits: number;
    onDelete: (id: string) => void;
    onRename: (id: string, newName: string) => void;
    dragInstructionId?: string;
}) {
    const { setNodeRef, isOver } = useDroppable({ id });
    const [editing, setEditing] = useState(false);
    const [tempName, setTempName] = useState(name);
    const [completed, setCompleted] = useState(false);

    const handleRename = () => {
        onRename(id, tempName.trim() || name);
        setEditing(false);
    };

    return (
        <Card
            ref={setNodeRef}
            aria-label={`${name} semester`}
            className={`border shadow-md transition
                ${isOver ? "ring-2 ring-brandPrimary ring-offset-2 ring-offset-white" : ""}
                ${completed ? "border-green-500 bg-green-50" : "bg-white/80 border-brandPrimary/30"}
            `}
        >
            <CardHeader className="flex flex-row items-center justify-between pb-2">
                <div className="flex items-center gap-2">
                    {editing ? (
                        <Input
                            value={tempName}
                            onChange={(e) => setTempName(e.target.value)}
                            onBlur={handleRename}
                            onKeyDown={(e) => e.key === "Enter" && handleRename()}
                            className="w-40 text-sm"
                            autoFocus
                        />
                    ) : (
                        <CardTitle
                            className="text-lg font-semibold text-brandPrimary cursor-pointer"
                            onClick={() => setEditing(true)}
                        >
                            {name}
                        </CardTitle>
                    )}

                    {/* Check button */}
                    <Button
                        variant={completed ? "secondary" : "outline"}
                        size="icon"
                        onClick={() => setCompleted((prev) => !prev)}
                        className={`transition ${
                            completed
                                ? "border-green-600 bg-green-100 hover:bg-green-200"
                                : "border-gray-300 hover:border-green-400"
                        }`}
                    >
                        {completed ? (
                            <Check className="h-4 w-4 text-green-700" />
                        ) : (
                            <Check className="h-4 w-4 text-gray-500" />
                        )}
                    </Button>
                </div>

                <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onDelete(id)}
                    className="text-gray-600 hover:text-red-700"
                >
                    <X className="h-4 w-4" />
                </Button>
            </CardHeader>

            <Separator />
            <CardContent className="pt-3">
                <p className="text-sm text-gray-600 mb-2">
                    Total Credits: <span className="font-medium">{totalCredits}</span>
                </p>
                <SortableContext
                    id={id}
                    items={courses.map((c) => c.id)}
                    strategy={verticalListSortingStrategy}
                >
                    <div className="space-y-3 min-h-[5rem]">
                        {courses.length === 0 ? (
                            <EmptyState
                                title="Drop courses here"
                                description="Drag from Remaining Courses or other semesters."
                                className="bg-brandSurface/60"
                            />
                        ) : (
                            courses.map((c) => (
                                <CourseCard
                                    key={c.id}
                                    course={c}
                                    dragInstructionId={dragInstructionId}
                                />
                            ))
                        )}
                    </div>
                </SortableContext>
            </CardContent>
        </Card>
    );
}
