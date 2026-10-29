import { useCallback, useEffect, useState } from "react";
import {
    DndContext,
    DragOverlay,
    KeyboardSensor,
    PointerSensor,
    closestCenter,
    type DragEndEvent,
    useDroppable,
    useSensor,
    useSensors,
} from "@dnd-kit/core";
import {
    SortableContext,
    arrayMove,
    rectSortingStrategy,
    sortableKeyboardCoordinates,
} from "@dnd-kit/sortable";
import { type Course, type Semester } from "@/types";
import { SemesterCard } from "@/components/SemesterCard";
import { RemainingCoursesCard } from "@/components/RemainingCoursesCard";
import { CourseCard } from "@/components/CourseCard";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { createPortal } from "react-dom";
import { toast } from "sonner";
import { CourseSearchModal } from "@/components/CourseSearchModal";
import { AddDegreeModal } from "@/components/AddDegreeModal";
import { AlertTriangle, CheckCircle2, Redo2, Undo2 } from "lucide-react";
import { EmptyState } from "@/components/EmptyState";
import { useAriaAnnouncer } from "@/hooks/useAriaAnnouncer";
import { cn } from "@/lib/utils";

type DegreeProgram = {
    id: string;
    name: string;
    advisor?: string;
    catalogYear: string;
};

type CoursePlan = {
    id: string;
    name: string;
    catalogYear: string;
    degreePrograms: DegreeProgram[];
    remaining: Course[];
    completed: Course[];
    semesters: Semester[];
};

type PlannerSnapshot = {
    plans: CoursePlan[];
    activePlanId: string;
};

type PlannerHistory = {
    past: PlannerSnapshot[];
    present: PlannerSnapshot;
    future: PlannerSnapshot[];
};

type PlannerStorageV1 = PlannerSnapshot & { version: 1 };

type ValidationSummary = {
    status: "idle" | "valid" | "issues";
    checkedAt: string | null;
    summary: string;
    remainingCourses: string[];
};

const PLANNER_STORAGE_KEY = "course-planner/planner-state";
const PLANNER_STORAGE_VERSION = 1;
const MAX_HISTORY = 40;
const DRAG_INSTRUCTIONS_ID = "planner-dnd-instructions";

const defaultPlans: CoursePlan[] = [{
    id: "demo-plan",
    name: "Demonstration Plan",
    catalogYear: "2025-2026",
    degreePrograms: [{ id: "demo", name: "Invented Studio Pathway", advisor: "Demo Advisor", catalogYear: "2025-2026" }],
    remaining: [
        { id: "demo-201", code: "DEMO 201", title: "Data Structures", credits: 3 },
        { id: "demo-210", code: "DEMO 210", title: "Database Design", credits: 3 },
    ],
    completed: [{ id: "demo-101", code: "DEMO 101", title: "Programming Foundations", credits: 3 }],
    semesters: [
        { id: "fall2025", name: "Fall 2025", courses: [] },
        { id: "spring2026", name: "Spring 2026", courses: [] },
    ],
}];

const defaultValidationSummary: ValidationSummary = {
    status: "idle",
    checkedAt: null,
    summary: "Run validation to see requirement feedback.",
    remainingCourses: [],
};

const normalizeSnapshot = (plans: CoursePlan[], activePlanId?: string): PlannerSnapshot | null => {
    if (plans.length === 0) return null;
    const resolvedId =
        activePlanId && plans.some((plan) => plan.id === activePlanId)
            ? activePlanId
            : plans[0].id;
    return { plans, activePlanId: resolvedId };
};

const migratePlannerSnapshot = (value: unknown): PlannerSnapshot | null => {
    if (!value || typeof value !== "object") return null;
    const record = value as Partial<PlannerStorageV1>;
    const plans = Array.isArray(record.plans) ? record.plans : null;
    if (!plans || plans.length === 0) return null;
    if (record.version !== undefined && record.version !== PLANNER_STORAGE_VERSION) return null;

    return normalizeSnapshot(
        plans,
        typeof record.activePlanId === "string" ? record.activePlanId : undefined
    );
};

const createDefaultSnapshot = (): PlannerSnapshot => ({
    plans: structuredClone(defaultPlans),
    activePlanId: defaultPlans[0].id,
});

const loadPlannerSnapshot = (): PlannerSnapshot => {
    if (typeof window === "undefined") return createDefaultSnapshot();

    try {
        const raw = window.localStorage.getItem(PLANNER_STORAGE_KEY);
        if (!raw) return createDefaultSnapshot();
        const parsed = JSON.parse(raw) as unknown;
        return migratePlannerSnapshot(parsed) ?? createDefaultSnapshot();
    } catch {
        return createDefaultSnapshot();
    }
};

const formatCheckedAt = (iso: string | null) => {
    if (!iso) return "Not run yet";
    return new Intl.DateTimeFormat("en-US", {
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
    }).format(new Date(iso));
};

const validationStatusMeta = {
    idle: {
        label: "Not run",
        tone: "bg-gray-100 text-gray-600",
        Icon: AlertTriangle,
    },
    valid: {
        label: "All clear",
        tone: "bg-emerald-100 text-emerald-700",
        Icon: CheckCircle2,
    },
    issues: {
        label: "Needs attention",
        tone: "bg-amber-100 text-amber-700",
        Icon: AlertTriangle,
    },
} as const;

function CompletedShelf({
    courses,
    dragInstructionId,
}: {
    courses: Course[];
    dragInstructionId?: string;
}) {
    const { setNodeRef, isOver } = useDroppable({ id: "completed" });

    return (
        <section
            ref={setNodeRef}
            aria-label="Completed courses"
            className={cn(
                "rounded-2xl border border-dashed border-gray-300 bg-white/70 p-5 transition",
                isOver && "ring-2 ring-brandPrimary/60 bg-brandPrimary/5"
            )}
        >
            <div className="flex items-start justify-between gap-3">
                <div>
                    <p className="text-xs uppercase tracking-[0.2em] text-gray-500">Completed</p>
                    <h3 className="text-lg font-semibold text-gray-900 font-display">
                        Completed Courses
                    </h3>
                    <p className="text-sm text-gray-600">
                        Drop finished courses here to lock them in.
                    </p>
                </div>
                <span className="text-xs text-gray-500">
                    {courses.length} course{courses.length === 1 ? "" : "s"}
                </span>
            </div>

            <SortableContext
                items={courses.map((course) => course.id)}
                strategy={rectSortingStrategy}
            >
                <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                    {courses.length === 0 ? (
                        <EmptyState
                            title="No completed courses yet"
                            description="Drag courses here once they are finished."
                            className="col-span-full bg-white/80"
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
        </section>
    );
}

export default function Planner() {
    const { message: ariaMessage, announce } = useAriaAnnouncer();

    const [history, setHistory] = useState<PlannerHistory>(() => ({
        past: [],
        present: loadPlannerSnapshot(),
        future: [],
    }));

    const { plans, activePlanId } = history.present;
    const activePlan = plans.find((plan) => plan.id === activePlanId) ?? plans[0];

    const studentProfile = {
        degrees: [
            { id: "cs", name: "B.S. in Computer Science", catalogYear: "2025–2026" },
            { id: "math", name: "B.S. in Mathematics", catalogYear: "2025–2026" },
        ],
        advisor: "Sample Instructor",
    };

    const [searchOpen, setSearchOpen] = useState(false);
    const [showDegreeModal, setShowDegreeModal] = useState(false);

    const [editingPlanId, setEditingPlanId] = useState<string | null>(null);
    const [tempPlanName, setTempPlanName] = useState("");
    const [validationSummary, setValidationSummary] = useState<ValidationSummary>(
        defaultValidationSummary
    );

    const commitSnapshot = useCallback(
        (
            updater: (snapshot: PlannerSnapshot) => PlannerSnapshot,
            options?: { replace?: boolean }
        ) => {
            setHistory((current) => {
                const next = updater(current.present);
                if (options?.replace) {
                    return {
                        past: current.past,
                        present: next,
                        future: current.future,
                    };
                }
                return {
                    past: [...current.past, current.present].slice(-MAX_HISTORY),
                    present: next,
                    future: [],
                };
            });
        },
        []
    );

    const undo = useCallback(() => {
        setHistory((current) => {
            if (current.past.length === 0) return current;
            const previous = current.past[current.past.length - 1];
            const newPast = current.past.slice(0, -1);
            return {
                past: newPast,
                present: previous,
                future: [current.present, ...current.future],
            };
        });
    }, []);

    const redo = useCallback(() => {
        setHistory((current) => {
            if (current.future.length === 0) return current;
            const next = current.future[0];
            const newFuture = current.future.slice(1);
            return {
                past: [...current.past, current.present].slice(-MAX_HISTORY),
                present: next,
                future: newFuture,
            };
        });
    }, []);

    const canUndo = history.past.length > 0;
    const canRedo = history.future.length > 0;

    useEffect(() => {
        if (plans.length === 0) {
            commitSnapshot(() => createDefaultSnapshot(), { replace: true });
            return;
        }

        if (!plans.some((plan) => plan.id === activePlanId)) {
            commitSnapshot(
                (snapshot) => ({
                    ...snapshot,
                    activePlanId: snapshot.plans[0].id,
                }),
                { replace: true }
            );
        }
    }, [plans, activePlanId, commitSnapshot]);

    useEffect(() => {
        if (typeof window === "undefined") return;
        const payload: PlannerStorageV1 = {
            version: PLANNER_STORAGE_VERSION,
            plans,
            activePlanId,
        };
        window.localStorage.setItem(PLANNER_STORAGE_KEY, JSON.stringify(payload));
    }, [plans, activePlanId]);

    useEffect(() => {
        setValidationSummary(defaultValidationSummary);
    }, [activePlanId]);

    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            const target = event.target as HTMLElement | null;
            if (
                target &&
                (target.isContentEditable ||
                    ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName))
            ) {
                return;
            }

            const isMac = navigator.platform.toLowerCase().includes("mac");
            const modifier = isMac ? event.metaKey : event.ctrlKey;
            if (!modifier) return;

            if (event.key.toLowerCase() === "z") {
                event.preventDefault();
                if (event.shiftKey) {
                    if (canRedo) redo();
                } else if (canUndo) {
                    undo();
                }
            }

            if (event.key.toLowerCase() === "y") {
                event.preventDefault();
                if (canRedo) redo();
            }
        };

        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [canRedo, canUndo, redo, undo]);

    const updateActivePlan = (updated: Partial<CoursePlan>) => {
        if (!activePlan) return;
        commitSnapshot((snapshot) => ({
            ...snapshot,
            plans: snapshot.plans.map((plan) =>
                plan.id === snapshot.activePlanId ? { ...plan, ...updated } : plan
            ),
        }));
    };

    const addDegreeToPlan = (program: DegreeProgram) => {
        if (!activePlan) return;
        if (
            activePlan.degreePrograms.some(
                (plan) => plan.id === program.id && plan.catalogYear === program.catalogYear
            )
        ) {
            toast.warning("That degree and catalog year are already added.");
            announce("That degree and catalog year are already added.");
            return;
        }
        updateActivePlan({ degreePrograms: [...activePlan.degreePrograms, program] });
        toast.success(`${program.name} (${program.catalogYear}) added to ${activePlan.name}`);
        announce(`${program.name} added to ${activePlan.name}.`);
    };

    const addPlan = () => {
        const newId = `plan${plans.length + 1}`;
        const nextPlan: CoursePlan = {
            id: newId,
            name: `Plan ${plans.length + 1}`,
            catalogYear: "2025–2026",
            degreePrograms: [],
            remaining: [],
            completed: [],
            semesters: [],
        };
        commitSnapshot((snapshot) => ({
            plans: [...snapshot.plans, nextPlan],
            activePlanId: newId,
        }));
    };

    const copyPlan = () => {
        if (!activePlan) return;
        const newId = `plan${plans.length + 1}`;
        const copy = structuredClone(activePlan);
        copy.id = newId;
        copy.name = `${activePlan.name} Copy`;
        commitSnapshot((snapshot) => ({
            plans: [...snapshot.plans, copy],
            activePlanId: newId,
        }));
    };

    const handleAddCourse = (course: Course) => {
        if (!activePlan) return;
        if (activePlan.remaining.some((current) => current.id === course.id)) {
            toast.warning("Course already in Remaining Courses.");
            announce(`${course.code} is already in Remaining Courses.`);
            return;
        }
        updateActivePlan({ remaining: [...activePlan.remaining, course] });
        toast.success(`Added ${course.code}`);
        announce(`${course.code} added to Remaining Courses.`);
    };

    const addSemester = () => {
        if (!activePlan) return;
        const nextNumber = activePlan.semesters.length + 1;
        const newSemester: Semester = {
            id: `sem${nextNumber}`,
            name: `Semester ${nextNumber}`,
            courses: [],
        };
        updateActivePlan({ semesters: [...activePlan.semesters, newSemester] });
        toast.success(`Added ${newSemester.name}`);
        announce(`${newSemester.name} added.`);
    };

    const handlePlanNameEdit = (planId: string, newName: string) => {
        commitSnapshot((snapshot) => ({
            ...snapshot,
            plans: snapshot.plans.map((plan) =>
                plan.id === planId ? { ...plan, name: newName } : plan
            ),
        }));
    };

    const selectPlan = (planId: string) => {
        commitSnapshot((snapshot) => ({ ...snapshot, activePlanId: planId }), { replace: true });
    };

    const runValidation = () => {
        if (!activePlan) return;
        const remainingCourses = activePlan.remaining.map((course) => course.code);
        const hasRemaining = remainingCourses.length > 0;
        const checkedAt = new Date().toISOString();

        setValidationSummary({
            status: hasRemaining ? "issues" : "valid",
            checkedAt,
            summary: hasRemaining
                ? "Some required courses are still unplanned."
                : "All required courses are scheduled in semesters.",
            remainingCourses,
        });

        if (!hasRemaining) {
            toast.success("Plan validated successfully. No remaining courses left.");
            announce("Plan validated successfully. No remaining courses left.");
        } else {
            const remainingList = remainingCourses.join(", ");
            toast.error(
                `${remainingCourses.length} course${remainingCourses.length > 1 ? "s" : ""} remaining: ${remainingList}`
            );
            announce("Plan validation failed. Remaining courses still need to be planned.");
        }
    };

    const findContainer = (courseId: string): string | undefined => {
        if (!activePlan) return undefined;
        if (activePlan.remaining.find((course) => course.id === courseId)) return "remaining";
        if (activePlan.completed.find((course) => course.id === courseId)) return "completed";
        const semester = activePlan.semesters.find((sem) =>
            sem.courses.some((course) => course.id === courseId)
        );
        return semester?.id;
    };

    const getContainerFor = (id: string | undefined): string | undefined => {
        if (!id || !activePlan) return undefined;
        if (id === "remaining" || id === "completed") return id;
        if (activePlan.semesters.some((sem) => sem.id === id)) return id;
        if (activePlan.remaining.some((course) => course.id === id)) return "remaining";
        if (activePlan.completed.some((course) => course.id === id)) return "completed";
        const semester = activePlan.semesters.find((sem) =>
            sem.courses.some((course) => course.id === id)
        );
        return semester?.id;
    };

    const insertCourse = (courses: Course[], course: Course, overId?: string) => {
        if (!overId) return [...courses, course];
        const index = courses.findIndex((current) => current.id === overId);
        if (index === -1) return [...courses, course];
        const next = [...courses];
        next.splice(index, 0, course);
        return next;
    };

    const [activeCourse, setActiveCourse] = useState<Course | null>(null);

    const sensors = useSensors(
        useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
        useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
    );

    const handleDragEnd = (event: DragEndEvent) => {
        const { active, over } = event;
        const course = active.data.current?.course as Course | undefined;
        setActiveCourse(null);

        if (!activePlan || !course || !over) return;

        const overId = over.id as string | undefined;
        const sourceId = findContainer(course.id);
        const destId = getContainerFor(overId);

        if (!sourceId || !destId) return;

        let { remaining, completed, semesters } = activePlan;

        if (sourceId === destId) {
            const currentCourses =
                sourceId === "remaining"
                    ? remaining
                    : sourceId === "completed"
                        ? completed
                        : semesters.find((sem) => sem.id === sourceId)?.courses || [];

            if (!overId || !currentCourses.some((current) => current.id === overId)) return;

            const oldIndex = currentCourses.findIndex((current) => current.id === course.id);
            const newIndex = currentCourses.findIndex((current) => current.id === overId);

            if (oldIndex === newIndex) return;

            const reordered = arrayMove(currentCourses, oldIndex, newIndex);

            if (sourceId === "remaining") {
                updateActivePlan({ remaining: reordered });
            } else if (sourceId === "completed") {
                updateActivePlan({ completed: reordered });
            } else {
                semesters = semesters.map((sem) =>
                    sem.id === sourceId ? { ...sem, courses: reordered } : sem
                );
                updateActivePlan({ semesters });
            }
            return;
        }

        if (sourceId === "remaining") {
            remaining = remaining.filter((current) => current.id !== course.id);
        } else if (sourceId === "completed") {
            completed = completed.filter((current) => current.id !== course.id);
        } else {
            semesters = semesters.map((sem) =>
                sem.id === sourceId
                    ? { ...sem, courses: sem.courses.filter((current) => current.id !== course.id) }
                    : sem
            );
        }

        if (destId === "remaining") {
            remaining = insertCourse(remaining, course, overId);
        } else if (destId === "completed") {
            completed = insertCourse(completed, course, overId);
        } else if (semesters.some((sem) => sem.id === destId)) {
            semesters = semesters.map((sem) =>
                sem.id === destId
                    ? { ...sem, courses: insertCourse(sem.courses, course, overId) }
                    : sem
            );
        }

        updateActivePlan({ remaining, completed, semesters });
    };

    if (!activePlan) {
        return (
            <div className="max-w-5xl mx-auto px-6 py-10">
                <EmptyState
                    title="No plans available"
                    description="Create a new plan to start organizing your semesters."
                    action={
                        <Button
                            variant="default"
                            className="bg-brandPrimary text-white hover:bg-brandPrimary/90"
                            onClick={addPlan}
                        >
                            + New Plan
                        </Button>
                    }
                />
            </div>
        );
    }

    const validationStatus = validationStatusMeta[validationSummary.status];
    const validationChecks = [
        {
            label: "Required courses scheduled",
            status:
                validationSummary.status === "idle"
                    ? "pending"
                    : validationSummary.status === "valid"
                        ? "pass"
                        : "fail",
        },
        { label: "Prerequisite order", status: "pending" },
        { label: "Double-counting rules", status: "pending" },
    ] as const;

    const remainingPreview = validationSummary.remainingCourses.slice(0, 6);
    const remainingOverflow =
        validationSummary.remainingCourses.length - remainingPreview.length;

    return (
        <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragStart={(event) => setActiveCourse(event.active.data.current?.course || null)}
            onDragEnd={handleDragEnd}
            onDragCancel={() => setActiveCourse(null)}
        >
            <div className="max-w-7xl mx-auto px-6 py-10 space-y-8">
                <div className="sr-only" aria-live="polite" aria-atomic="true">
                    {ariaMessage}
                </div>
                <div id={DRAG_INSTRUCTIONS_ID} className="sr-only">
                    Use space or enter to lift a course, arrow keys to move, and space to drop.
                </div>

                <header className="rounded-3xl border border-brandPrimary/20 bg-white/90 p-6 shadow-lg">
                    <div className="flex flex-col lg:flex-row lg:items-center gap-4">
                        <div>
                            <p className="text-xs uppercase tracking-[0.3em] text-brandPrimary/70">
                                Academic Planner
                            </p>
                            <h1 className="text-3xl font-bold text-brandPrimary font-display">
                                Plan Your Semesters
                            </h1>
                            <p className="text-sm text-gray-600">
                                Drag courses into terms, then validate progress when you are ready.
                            </p>
                            <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-gray-600">
                                <span className="rounded-full border border-gray-200 bg-white px-3 py-1">
                                    Active plan: {activePlan.name}
                                </span>
                                <span className="rounded-full border border-gray-200 bg-white px-3 py-1">
                                    Catalog year: {activePlan.catalogYear}
                                </span>
                            </div>
                        </div>
                        <div className="ml-auto flex flex-wrap gap-2">
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={undo}
                                disabled={!canUndo}
                                aria-label="Undo"
                                title="Undo (Ctrl/Cmd+Z)"
                            >
                                <Undo2 className="h-4 w-4" />
                                Undo
                            </Button>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={redo}
                                disabled={!canRedo}
                                aria-label="Redo"
                                title="Redo (Ctrl/Cmd+Shift+Z)"
                            >
                                <Redo2 className="h-4 w-4" />
                                Redo
                            </Button>
                            <Button
                                variant="default"
                                className="bg-brandPrimary text-white hover:bg-brandPrimary/90"
                                onClick={addSemester}
                            >
                                + Add Semester
                            </Button>
                            <Button
                                variant="outline"
                                className="border-brandPrimary text-brandPrimary hover:bg-brandPrimary hover:text-white"
                                onClick={runValidation}
                            >
                                Validate Plan
                            </Button>
                        </div>
                    </div>

                    <div className="mt-5 grid gap-3 sm:grid-cols-3">
                        <div className="rounded-2xl border border-gray-200 bg-white/90 px-4 py-3">
                            <p className="text-xs uppercase tracking-widest text-gray-500">Semesters</p>
                            <p className="text-2xl font-semibold text-gray-900">
                                {activePlan.semesters.length}
                            </p>
                            <p className="text-xs text-gray-500">Terms planned</p>
                        </div>
                        <div className="rounded-2xl border border-gray-200 bg-white/90 px-4 py-3">
                            <p className="text-xs uppercase tracking-widest text-gray-500">Remaining</p>
                            <p className="text-2xl font-semibold text-gray-900">
                                {activePlan.remaining.length}
                            </p>
                            <p className="text-xs text-gray-500">Courses left</p>
                        </div>
                        <div className="rounded-2xl border border-gray-200 bg-white/90 px-4 py-3">
                            <p className="text-xs uppercase tracking-widest text-gray-500">Completed</p>
                            <p className="text-2xl font-semibold text-gray-900">
                                {activePlan.completed.length}
                            </p>
                            <p className="text-xs text-gray-500">Courses finished</p>
                        </div>
                    </div>
                </header>

                <section className="grid gap-6 lg:grid-cols-[280px_1fr]">
                    <aside className="space-y-4">
                        <div className="rounded-2xl border border-gray-200 bg-white/90 p-4 shadow-sm">
                            <div className="flex items-center justify-between">
                                <div>
                                    <p className="text-xs uppercase tracking-widest text-gray-500">Plans</p>
                                    <h2 className="text-base font-semibold text-gray-900">Your plans</h2>
                                </div>
                            </div>
                            <div className="mt-3 space-y-2">
                                {plans.map((plan) => (
                                    <div
                                        key={plan.id}
                                        className={cn(
                                            "rounded-xl border px-3 py-2 transition",
                                            plan.id === activePlanId
                                                ? "border-brandPrimary bg-brandPrimary/5"
                                                : "border-gray-200 bg-white"
                                        )}
                                    >
                                        {editingPlanId === plan.id ? (
                                            <Input
                                                value={tempPlanName}
                                                onChange={(event) => setTempPlanName(event.target.value)}
                                                onBlur={() => {
                                                    handlePlanNameEdit(plan.id, tempPlanName || plan.name);
                                                    setEditingPlanId(null);
                                                }}
                                                onKeyDown={(event) => {
                                                    if (event.key === "Enter") {
                                                        handlePlanNameEdit(plan.id, tempPlanName || plan.name);
                                                        setEditingPlanId(null);
                                                    }
                                                }}
                                                className="h-8 text-sm"
                                                autoFocus
                                            />
                                        ) : (
                                            <button
                                                type="button"
                                                className="w-full text-left"
                                                onClick={() => selectPlan(plan.id)}
                                                onDoubleClick={() => {
                                                    setEditingPlanId(plan.id);
                                                    setTempPlanName(plan.name);
                                                }}
                                            >
                                                <div className="flex items-center justify-between">
                                                    <span className="text-sm font-semibold text-gray-900">
                                                        {plan.name}
                                                    </span>
                                                    {plan.id === activePlanId ? (
                                                        <span className="text-[10px] uppercase tracking-widest text-brandPrimary">
                                                            Active
                                                        </span>
                                                    ) : null}
                                                </div>
                                                <p className="text-xs text-gray-500">{plan.catalogYear}</p>
                                            </button>
                                        )}
                                    </div>
                                ))}
                            </div>
                            <div className="mt-3 flex flex-wrap gap-2">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    className="border-gray-200"
                                    onClick={addPlan}
                                >
                                    + New Plan
                                </Button>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    className="border-gray-200"
                                    onClick={copyPlan}
                                >
                                    Copy Active
                                </Button>
                            </div>
                        </div>

                        <Card className="border border-gray-200 shadow-sm bg-white/90">
                            <CardHeader>
                                <CardTitle className="text-base font-semibold text-gray-900">
                                    Student Profile
                                </CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-3">
                                <div>
                                    <p className="text-xs uppercase tracking-widest text-gray-500">Advisor</p>
                                    <p className="text-sm text-gray-800">{studentProfile.advisor}</p>
                                </div>
                                <div>
                                    <p className="text-xs uppercase tracking-widest text-gray-500">Catalog Year</p>
                                    <p className="text-sm text-gray-800">
                                        {[...new Set(studentProfile.degrees.map((deg) => deg.catalogYear))].join(", ")}
                                    </p>
                                </div>
                                <div>
                                    <p className="text-xs uppercase tracking-widest text-gray-500">Programs</p>
                                    <ul className="mt-2 space-y-1">
                                        {studentProfile.degrees.map((deg) => (
                                            <li key={deg.id} className="text-sm text-gray-800">
                                                <span className="font-medium text-brandPrimary">{deg.name}</span>
                                                <span className="text-xs text-gray-500"> ({deg.catalogYear})</span>
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            </CardContent>
                        </Card>

                        <Card className="border border-gray-200 shadow-sm bg-white/90">
                            <CardHeader>
                                <div className="flex items-center justify-between">
                                    <CardTitle className="text-base font-semibold text-gray-900">
                                        Degree Programs
                                    </CardTitle>
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        onClick={() => setShowDegreeModal(true)}
                                    >
                                        + Add
                                    </Button>
                                </div>
                            </CardHeader>
                            <CardContent className="space-y-2">
                                {activePlan.degreePrograms.length === 0 ? (
                                    <EmptyState
                                        title="No degree programs yet"
                                        description="Add a program to see its requirements and progress."
                                        className="bg-white/80"
                                    />
                                ) : (
                                    activePlan.degreePrograms.map((deg) => (
                                        <div
                                            key={`${deg.id}-${deg.catalogYear}`}
                                            className="flex justify-between items-center border border-gray-200 rounded-lg p-2 bg-white"
                                        >
                                            <div>
                                                <p className="text-sm font-medium">{deg.name}</p>
                                                <p className="text-xs text-gray-500">
                                                    {deg.catalogYear} • {deg.advisor}
                                                </p>
                                            </div>
                                            <Button
                                                size="sm"
                                                variant="ghost"
                                                onClick={() =>
                                                    updateActivePlan({
                                                        degreePrograms: activePlan.degreePrograms.filter(
                                                            (program) =>
                                                                program.id !== deg.id ||
                                                                program.catalogYear !== deg.catalogYear
                                                        ),
                                                    })
                                                }
                                            >
                                                Remove
                                            </Button>
                                        </div>
                                    ))
                                )}
                            </CardContent>
                        </Card>

                        <div className="rounded-2xl border border-gray-200 bg-white/90 p-4 shadow-sm">
                            <div className="flex items-start justify-between gap-3">
                                <div>
                                    <p className="text-xs uppercase tracking-widest text-gray-500">Validation</p>
                                    <h3 className="text-base font-semibold text-gray-900">
                                        Plan Feedback
                                    </h3>
                                </div>
                                <span
                                    className={cn(
                                        "inline-flex items-center gap-1 rounded-full px-2 py-1 text-[11px] uppercase tracking-widest",
                                        validationStatus.tone
                                    )}
                                >
                                    <validationStatus.Icon className="h-3 w-3" />
                                    {validationStatus.label}
                                </span>
                            </div>
                            <p className="mt-2 text-xs text-gray-500">
                                Last run: {formatCheckedAt(validationSummary.checkedAt)}
                            </p>

                            <div className="mt-3 rounded-xl border border-gray-200 bg-gray-50 px-3 py-3">
                                <p className="text-sm font-semibold text-gray-900">
                                    {validationSummary.summary}
                                </p>
                                {validationSummary.status === "issues" ? (
                                    <div className="mt-3 flex flex-wrap gap-2">
                                        {remainingPreview.map((code) => (
                                            <span
                                                key={code}
                                                className="rounded-full border border-amber-200 bg-amber-50 px-2 py-1 text-xs text-amber-700"
                                            >
                                                {code}
                                            </span>
                                        ))}
                                        {remainingOverflow > 0 ? (
                                            <span className="text-xs text-gray-500">
                                                +{remainingOverflow} more
                                            </span>
                                        ) : null}
                                    </div>
                                ) : null}
                            </div>

                            <div className="mt-4 space-y-2">
                                {validationChecks.map((check) => (
                                    <div
                                        key={check.label}
                                        className="flex items-center justify-between rounded-lg border border-gray-200 bg-white px-3 py-2"
                                    >
                                        <div className="flex items-center gap-2">
                                            <span
                                                className={cn(
                                                    "h-2 w-2 rounded-full",
                                                    check.status === "pass"
                                                        ? "bg-emerald-500"
                                                        : check.status === "fail"
                                                            ? "bg-amber-500"
                                                            : "bg-gray-300"
                                                )}
                                            />
                                            <span className="text-xs font-medium text-gray-700">
                                                {check.label}
                                            </span>
                                        </div>
                                        <span className="text-[10px] uppercase tracking-widest text-gray-500">
                                            {check.status}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </aside>

                    <main className="space-y-6">
                        <div className="grid gap-6 lg:grid-cols-[320px_1fr]">
                            <div>
                                <RemainingCoursesCard
                                    courses={activePlan.remaining}
                                    onSearchClick={() => setSearchOpen(true)}
                                    dragInstructionId={DRAG_INSTRUCTIONS_ID}
                                />
                            </div>

                            <section>
                                <div className="flex items-center justify-between mb-4">
                                    <h2 className="text-xl font-semibold text-brandPrimary font-display">
                                        Semesters
                                    </h2>
                                    <p className="text-sm text-gray-600">
                                        {activePlan.semesters.length} term{
                                            activePlan.semesters.length === 1 ? "" : "s"
                                        }
                                    </p>
                                </div>
                                {activePlan.semesters.length === 0 ? (
                                    <EmptyState
                                        title="No semesters yet"
                                        description="Add a semester to start organizing your course plan."
                                        action={
                                            <Button
                                                variant="default"
                                                className="bg-brandPrimary text-white hover:bg-brandPrimary/90"
                                                onClick={addSemester}
                                            >
                                                + Add Semester
                                            </Button>
                                        }
                                    />
                                ) : (
                                    <div className="grid md:grid-cols-2 xl:grid-cols-3 gap-6">
                                        {activePlan.semesters.map((sem) => (
                                            <SemesterCard
                                                key={sem.id}
                                                id={sem.id}
                                                name={sem.name}
                                                courses={sem.courses}
                                                totalCredits={sem.courses.reduce(
                                                    (acc, course) => acc + course.credits,
                                                    0
                                                )}
                                                onDelete={(id) =>
                                                    updateActivePlan({
                                                        semesters: activePlan.semesters.filter(
                                                            (semester) => semester.id !== id
                                                        ),
                                                    })
                                                }
                                                onRename={(id, newName) =>
                                                    updateActivePlan({
                                                        semesters: activePlan.semesters.map((semester) =>
                                                            semester.id === id
                                                                ? { ...semester, name: newName }
                                                                : semester
                                                        ),
                                                    })
                                                }
                                                dragInstructionId={DRAG_INSTRUCTIONS_ID}
                                            />
                                        ))}
                                    </div>
                                )}
                            </section>
                        </div>

                        <CompletedShelf
                            courses={activePlan.completed}
                            dragInstructionId={DRAG_INSTRUCTIONS_ID}
                        />
                    </main>
                </section>

                <AddDegreeModal
                    open={showDegreeModal}
                    onOpenChange={setShowDegreeModal}
                    onAdd={addDegreeToPlan}
                />
                <CourseSearchModal
                    open={searchOpen}
                    onOpenChange={setSearchOpen}
                    onAddCourse={handleAddCourse}
                />
            </div>

            {createPortal(
                <DragOverlay>
                    {activeCourse ? (
                        <Card className="bg-white/95 border border-gray-200 shadow-xl p-3 w-64 scale-105">
                            <p className="font-semibold text-gray-800">{activeCourse.code}</p>
                            <p className="text-sm text-gray-600">{activeCourse.title}</p>
                        </Card>
                    ) : null}
                </DragOverlay>,
                document.body
            )}
        </DndContext>
    );
}
