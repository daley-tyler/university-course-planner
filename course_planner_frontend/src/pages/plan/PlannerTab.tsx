import { useMemo, useState } from "react";
import {
    DndContext,
    KeyboardSensor,
    PointerSensor,
    closestCenter,
    type DragEndEvent,
    useDroppable,
    useSensor,
    useSensors,
} from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy, sortableKeyboardCoordinates } from "@dnd-kit/sortable";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/EmptyState";
import { PlanCourseCard } from "@/components/PlanCourseCard";
import { Input } from "@/components/ui/input";
import { AlertTriangle, Plus, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { type PlanCourse, getCourseByCode, useActivePlan, usePlanStore } from "@/store/planStore";
import { AnimatePresence, motion, type Variants } from "framer-motion";

import { hasPrerequisiteViolation } from "@/store/planLogic";

const DRAG_INSTRUCTIONS_ID = "planner-dnd-instructions";

type DroppableInfo = {
    setNodeRef: (node: HTMLElement | null) => void;
    isOver: boolean;
};

function useDropZone(id: string): DroppableInfo {
    const { setNodeRef, isOver } = useDroppable({ id });
    return { setNodeRef, isOver };
}

const pageVariants: Variants = {
    hidden: { opacity: 0, y: 12 },
    show: {
        opacity: 1,
        y: 0,
        transition: { duration: 0.35, ease: "easeOut", when: "beforeChildren", staggerChildren: 0.08 },
    },
};

const sectionVariants: Variants = {
    hidden: { opacity: 0, y: 10 },
    show: { opacity: 1, y: 0, transition: { duration: 0.3, ease: "easeOut" } },
};

const cardVariants: Variants = {
    hidden: { opacity: 0, y: 10 },
    show: { opacity: 1, y: 0, transition: { duration: 0.25, ease: "easeOut" } },
    exit: { opacity: 0, y: -10, transition: { duration: 0.2, ease: "easeIn" } },
};

export function PlannerTab() {
    const { actions, state } = usePlanStore();
    const activePlan = useActivePlan();

    const [editingTermId, setEditingTermId] = useState<string | null>(null);
    const [termNameDraft, setTermNameDraft] = useState("");

    const sensors = useSensors(
        useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
        useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
    );

    const sortedTerms = useMemo(() => {
        if (!activePlan) return [];
        return [...activePlan.terms].sort((a, b) => a.order - b.order);
    }, [activePlan]);

    const courseById = useMemo(() => {
        if (!activePlan) return new Map();
        return new Map(activePlan.courses.map((course) => [course.id, course]));
    }, [activePlan]);

    const unscheduledCourses = useMemo(() => {
        if (!activePlan) return [];
        return activePlan.courses.filter((course) => course.scheduledTermId === null);
    }, [activePlan]);

    const getTermCredits = (termId: string) => {
        const term = sortedTerms.find((item) => item.id === termId);
        if (!term) return 0;
        return term.courseIds.reduce((sum, courseId) => {
            const course = courseById.get(courseId);
            return sum + (course?.credits ?? 0);
        }, 0);
    };

    const isPrereqViolation = (courseId: string, termId: string) => {
        const course = courseById.get(courseId);
        if (!course) return false;
        const catalogCourse = getCourseByCode(course.code);
        return activePlan ? hasPrerequisiteViolation(course, activePlan, termId, catalogCourse?.prereqs) : false;
    };

    const handleDragEnd = (event: DragEndEvent) => {
        const { active, over } = event;
        if (!activePlan || !over) return;
        const courseId = active.id as string;
        const overId = over.id as string;

        const unscheduledIds = unscheduledCourses.map((course) => course.id);
        const findContainer = (id: string) => {
            if (unscheduledIds.includes(id)) return "unscheduled";
            const term = sortedTerms.find((item) => item.courseIds.includes(id));
            return term?.id;
        };

        const sourceId = findContainer(courseId);
        const destinationId =
            overId === "unscheduled" || sortedTerms.some((term) => term.id === overId)
                ? overId
                : findContainer(overId);

        if (!sourceId || !destinationId) return;

        if (sourceId === destinationId) {
            if (sourceId === "unscheduled") {
                if (courseId !== overId) {
                    actions.reorderUnscheduled(activePlan.id, courseId, overId);
                }
            } else {
                if (courseId !== overId) {
                    actions.reorderCourseInTerm(activePlan.id, sourceId, courseId, overId);
                }
            }
            return;
        }

        const termTarget = destinationId === "unscheduled" ? null : destinationId;
        const insertBefore = overId !== destinationId ? overId : undefined;
        actions.moveCourseToTerm(activePlan.id, courseId, termTarget, insertBefore);
    };

    const handleRemoveCourse = (courseId: string) => {
        if (!activePlan) return;
        actions.removeCourseFromPlan(activePlan.id, courseId);
    };

    if (!activePlan) {
        return (
            <EmptyState
                title="No active plan"
                description="Select or create a plan to start scheduling courses."
            />
        );
    }

    return (
        <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragEnd={handleDragEnd}
        >
            <motion.div className="space-y-6" variants={pageVariants} initial="hidden" animate="show">
                <div id={DRAG_INSTRUCTIONS_ID} className="sr-only">
                    Use space or enter to lift a course, arrow keys to move, and space to drop.
                </div>

                <div className="grid gap-6 lg:grid-cols-[280px_1fr]">
                    <motion.aside className="space-y-4" variants={sectionVariants}>
                        <motion.div
                            className="rounded-lg border border-brandBorder bg-brandSurface p-4 shadow-sm"
                            variants={cardVariants}
                        >
                            <div className="flex items-center justify-between">
                                <div>
                                    <p className="text-xs uppercase tracking-widest text-gray-500">Unscheduled</p>
                                    <h2 className="text-base font-semibold text-gray-900">Course Queue</h2>
                                </div>
                                <span className="text-xs text-gray-500">
                                    {unscheduledCourses.length} courses
                                </span>
                            </div>
                            <div className="mt-3">
                                <UnscheduledList
                                    courses={unscheduledCourses}
                                    onRemoveCourse={handleRemoveCourse}
                                    highlightCode={state.focus.type === "course" ? state.focus.value : null}
                                />
                            </div>
                        </motion.div>

                        <motion.div
                            className="rounded-lg border border-brandBorder bg-brandSurface p-4 shadow-sm"
                            variants={cardVariants}
                        >
                            <div className="flex items-center justify-between">
                                <div>
                                    <p className="text-xs uppercase tracking-widest text-gray-500">Terms</p>
                                    <h3 className="text-base font-semibold text-gray-900">Manage schedule</h3>
                                </div>
                                <Button
                                    size="sm"
                                    variant="outline"
                                    className="border-brandBorder"
                                    onClick={() => actions.addTerm(activePlan.id)}
                                >
                                    <Plus className="mr-1 h-4 w-4" />
                                    Add Term
                                </Button>
                            </div>
                            <p className="mt-2 text-xs text-gray-500">
                                Drag courses into terms. Warnings highlight prerequisites or credit caps.
                            </p>
                        </motion.div>
                    </motion.aside>

                    <motion.section className="space-y-4" variants={sectionVariants}>
                        <motion.div className="flex items-center justify-between" variants={cardVariants}>
                            <h2 className="text-lg font-semibold text-brandPrimary font-display">Semester Plan</h2>
                            <span className="text-xs uppercase tracking-widest text-gray-400">
                                {sortedTerms.length} term{sortedTerms.length === 1 ? "" : "s"}
                            </span>
                        </motion.div>

                        {sortedTerms.length === 0 ? (
                            <EmptyState
                                title="No terms yet"
                                description="Add a term to start scheduling courses."
                            />
                        ) : (
                            <div className="grid gap-4 lg:grid-cols-2">
                                <AnimatePresence mode="popLayout">
                                    {sortedTerms.map((term) => {
                                        const termCredits = getTermCredits(term.id);
                                        const overCap = termCredits > (term.creditCap ?? 18);

                                        return (
                                            <motion.div
                                                key={term.id}
                                                layout
                                                variants={cardVariants}
                                                initial="hidden"
                                                animate="show"
                                                exit="exit"
                                            >
                                                <div
                                                    className={cn(
                                                        "rounded-lg border border-brandBorder bg-brandSurface p-4 shadow-sm transition-shadow hover:shadow-md",
                                                        overCap ? "border-amber-300" : ""
                                                    )}
                                                >
                                                    <div className="flex items-start justify-between gap-3">
                                                        {editingTermId === term.id ? (
                                                            <Input
                                                                value={termNameDraft}
                                                                onChange={(event) => setTermNameDraft(event.target.value)}
                                                                onBlur={() => {
                                                                    actions.renameTerm(activePlan.id, term.id, termNameDraft || term.name);
                                                                    setEditingTermId(null);
                                                                }}
                                                                onKeyDown={(event) => {
                                                                    if (event.key === "Enter") {
                                                                        actions.renameTerm(
                                                                            activePlan.id,
                                                                            term.id,
                                                                            termNameDraft || term.name
                                                                        );
                                                                        setEditingTermId(null);
                                                                    }
                                                                }}
                                                                className="h-8 text-sm"
                                                                autoFocus
                                                            />
                                                        ) : (
                                                            <button
                                                                type="button"
                                                                onClick={() => {
                                                                    setEditingTermId(term.id);
                                                                    setTermNameDraft(term.name);
                                                                }}
                                                                className="text-left"
                                                            >
                                                                <h3 className="text-base font-semibold text-gray-900">
                                                                    {term.name}
                                                                </h3>
                                                                <p className="text-xs text-gray-500">
                                                                    {termCredits} / {term.creditCap ?? 18} credits
                                                                </p>
                                                            </button>
                                                        )}
                                                        <Button
                                                            variant="ghost"
                                                            size="icon"
                                                            onClick={() => actions.removeTerm(activePlan.id, term.id)}
                                                            className="text-gray-400 hover:text-red-600"
                                                        >
                                                            <Trash2 className="h-4 w-4" />
                                                        </Button>
                                                    </div>
                                                    {overCap ? (
                                                        <div className="mt-2 flex items-center gap-2 text-xs text-amber-700">
                                                            <AlertTriangle className="h-3 w-3" />
                                                            Over credit cap for this term
                                                        </div>
                                                    ) : null}

                                                    <TermDropZone
                                                        termId={term.id}
                                                        courses={term.courseIds
                                                            .map((id) => courseById.get(id))
                                                            .filter((course): course is PlanCourse => Boolean(course))}
                                                        isPrereqViolation={isPrereqViolation}
                                                        overCap={overCap}
                                                        onRemoveCourse={handleRemoveCourse}
                                                        highlightCode={state.focus.type === "course" ? state.focus.value : null}
                                                    />
                                                </div>
                                            </motion.div>
                                        );
                                    })}
                                </AnimatePresence>
                            </div>
                        )}
                    </motion.section>
                </div>
            </motion.div>
        </DndContext>
    );
}

function UnscheduledList({
    courses,
    onRemoveCourse,
    highlightCode,
}: {
    courses: PlanCourse[];
    onRemoveCourse: (courseId: string) => void;
    highlightCode: string | null;
}) {
    const { setNodeRef, isOver } = useDropZone("unscheduled");

    return (
        <motion.div
            ref={setNodeRef}
            className={cn(
                "rounded-xl border border-dashed border-brandBorder bg-brandSurface/80 p-3 transition",
                isOver ? "ring-2 ring-brandPrimary/60" : ""
            )}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25, ease: "easeOut" }}
        >
            <SortableContext items={courses.map((course) => course.id)} strategy={verticalListSortingStrategy}>
                <div className="space-y-3">
                    {courses.length === 0 ? (
                        <EmptyState
                            title="All courses scheduled"
                            description="Drag courses back here if you want to reconsider."
                            className="bg-white"
                        />
                    ) : (
                        courses.map((course) => (
                            <PlanCourseCard
                                key={course.id}
                                course={course}
                                dragInstructionId={DRAG_INSTRUCTIONS_ID}
                                onRemove={onRemoveCourse}
                                className={
                                    highlightCode && highlightCode === course.code
                                        ? "ring-2 ring-brandPrimary/70"
                                        : ""
                                }
                            />
                        ))
                    )}
                </div>
            </SortableContext>
        </motion.div>
    );
}

function TermDropZone({
    termId,
    courses,
    isPrereqViolation,
    overCap,
    onRemoveCourse,
    highlightCode,
}: {
    termId: string;
    courses: PlanCourse[];
    isPrereqViolation: (courseId: string, termId: string) => boolean;
    overCap: boolean;
    onRemoveCourse: (courseId: string) => void;
    highlightCode: string | null;
}) {
    const { setNodeRef, isOver } = useDropZone(termId);

    return (
        <motion.div
            ref={setNodeRef}
            className={cn(
                "mt-4 min-h-[140px] rounded-xl border border-dashed border-brandBorder bg-brandSurface/80 p-3 transition",
                isOver ? "ring-2 ring-brandPrimary/60" : ""
            )}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25, ease: "easeOut" }}
        >
            <SortableContext items={courses.map((course) => course.id)} strategy={verticalListSortingStrategy}>
                <div className="space-y-3">
                    {courses.length === 0 ? (
                        <EmptyState
                            title="Drop courses here"
                            description="Drag from the Unscheduled list to fill this term."
                            className="bg-white"
                        />
                    ) : (
                        courses.map((course) => {
                            const prereqWarning = isPrereqViolation(course.id, termId);
                            const warning = prereqWarning
                                ? { label: "Prereq", tone: "rose" as const }
                                : overCap
                                    ? { label: "Over cap", tone: "amber" as const }
                                    : null;
                            return (
                                <PlanCourseCard
                                    key={course.id}
                                    course={course}
                                    warning={warning}
                                    dragInstructionId={DRAG_INSTRUCTIONS_ID}
                                    onRemove={onRemoveCourse}
                                    className={
                                        highlightCode && highlightCode === course.code
                                            ? "ring-2 ring-brandPrimary/70"
                                            : ""
                                    }
                                />
                            );
                        })
                    )}
                </div>
            </SortableContext>
        </motion.div>
    );
}
