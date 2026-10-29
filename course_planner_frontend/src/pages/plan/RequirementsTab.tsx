import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { EmptyState } from "@/components/EmptyState";
import { AddDegreeModal } from "@/components/AddDegreeModal";
import { Button } from "@/components/ui/button";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { useActivePlan, usePlanStore } from "@/store/planStore";
import { toast } from "sonner";
import { X, Loader2 } from "lucide-react";
import { fetchRequirementTree, fetchCourseDetail } from "@/api/catalog";
import type { RequirementNodeDto } from "@/api/types";
import { ProgressSummary } from "./requirements/ProgressSummary";
import { RequirementSection } from "./requirements/RequirementSection";
import { isNoteNode, isSectionLabelNode, precomputeSectionSummaries, type FilterMode } from "./requirements/requirementStatus";

export function RequirementsTab() {
    const { actions } = usePlanStore();
    const activePlan = useActivePlan();

    const [selectedProgramId, setSelectedProgramId] = useState(
        activePlan?.programs[0]?.id ?? ""
    );
    const [addProgramOpen, setAddProgramOpen] = useState(false);
    const [requirementsLoading, setRequirementsLoading] = useState(false);
    const [requirementsError, setRequirementsError] = useState<string | null>(null);
    const [requirementTree, setRequirementTree] = useState<RequirementNodeDto | null>(null);
    const [filter, setFilter] = useState<FilterMode>("all");

    // Sticky breadcrumb state
    const [stickySection, setStickySection] = useState<string | null>(null);
    const sectionRefs = useRef<Map<string, HTMLDivElement>>(new Map());

    // Sync selected program when plan changes
    useEffect(() => {
        setSelectedProgramId((current) => {
            const programIds = activePlan?.programs.map((p) => p.id) ?? [];
            if (programIds.length === 0) return "";
            if (current && programIds.includes(current)) return current;
            return programIds[0];
        });
    }, [activePlan?.id, activePlan?.programs]);

    const selectedProgram = activePlan?.programs.find(
        (p) => p.id === selectedProgramId
    );

    // Fetch requirement tree
    useEffect(() => {
        if (!selectedProgramId) {
            setRequirementTree(null);
            setRequirementsError(null);
            return;
        }
        const numericId = Number(selectedProgramId);
        if (!Number.isFinite(numericId)) {
            setRequirementTree(null);
            setRequirementsError("Selected program is not available in the catalog.");
            return;
        }

        let active = true;
        setRequirementsLoading(true);
        setRequirementsError(null);

        fetchRequirementTree(numericId, {
            catalogLabel: selectedProgram?.catalogYear,
        })
            .then((response) => {
                if (!active) return;
                setRequirementTree(response.root ?? null);
            })
            .catch((err) => {
                if (!active) return;
                setRequirementTree(null);
                setRequirementsError(
                    err instanceof Error ? err.message : "Failed to load requirements."
                );
            })
            .finally(() => {
                if (!active) return;
                setRequirementsLoading(false);
            });

        return () => {
            active = false;
        };
    }, [selectedProgramId, selectedProgram?.catalogYear]);

    // Derive section nodes (top-level requirement groups)
    const sectionNodes = useMemo(() => {
        if (!requirementTree) return [];
        if (requirementTree.children && requirementTree.children.length > 0) {
            return requirementTree.children.filter(
                (n) => !isNoteNode(n) || isSectionLabelNode(n)
            );
        }
        return [requirementTree];
    }, [requirementTree]);

    // Top-level notes
    const noteNodes = useMemo(() => {
        if (!requirementTree?.children) return [];
        return requirementTree.children.filter(
            (n) => isNoteNode(n) && !isSectionLabelNode(n)
        );
    }, [requirementTree]);

    // Plan course codes set
    const courses = activePlan?.courses;
    const planCourseCodes = useMemo(() => {
        if (!courses) return new Set<string>();
        return new Set(courses.map((c) => c.code));
    }, [courses]);

    // Total plan credits
    const totalPlanCredits = useMemo(() => {
        if (!courses) return 0;
        return courses.reduce((sum, c) => sum + c.credits, 0);
    }, [courses]);

    // Pre-compute section summaries once (fix #6 - avoid redundant traversals)
    const sectionSummaries = useMemo(
        () => precomputeSectionSummaries(sectionNodes, planCourseCodes),
        [sectionNodes, planCourseCodes]
    );

    // Intersection observer for sticky breadcrumb (skip initial fire)
    const hasScrolled = useRef(false);
    useEffect(() => {
        hasScrolled.current = false;
        const onScroll = () => { hasScrolled.current = true; };
        window.addEventListener("scroll", onScroll, { once: true, passive: true });

        const observer = new IntersectionObserver(
            (entries) => {
                if (!hasScrolled.current) return;
                for (const entry of entries) {
                    if (entry.isIntersecting) {
                        setStickySection(entry.target.getAttribute("data-section-name"));
                    }
                }
            },
            { rootMargin: "-80px 0px -60% 0px", threshold: 0 }
        );

        const refs = sectionRefs.current;
        refs.forEach((el) => observer.observe(el));
        return () => {
            observer.disconnect();
            window.removeEventListener("scroll", onScroll);
        };
    }, [sectionNodes]);

    const registerSectionRef = useCallback(
        (id: string, name: string, el: HTMLDivElement | null) => {
            if (el) {
                el.setAttribute("data-section-name", name);
                sectionRefs.current.set(id, el);
            } else {
                sectionRefs.current.delete(id);
            }
        },
        []
    );

    const activePlanId = activePlan?.id;
    const handleAddCourse = useCallback(
        async (code: string, title?: string, credits?: number) => {
            if (!activePlanId) return;
            try {
                const course = await fetchCourseDetail(code);
                actions.addCourseToPlan(activePlanId, code, {
                    title: course.title ?? title,
                    credits: course.credits ?? credits,
                    prereqs: course.prereqs,
                });
            } catch {
                toast.error(`Could not load ${code}. Please try again before adding it.`);
            }
        },
        [activePlanId, actions]
    );

    const handleAddProgram = (program: {
        id: string;
        name: string;
        advisor?: string;
        catalogYear: string;
        programType?: string;
        degreeType?: string | null;
        college?: string | null;
        catalogVersionId?: number | null;
    }) => {
        if (!activePlan) return;
        const alreadyAdded = activePlan.programs.some(
            (item) => item.id === program.id && item.catalogYear === program.catalogYear
        );
        if (alreadyAdded) {
            setSelectedProgramId(program.id);
            return;
        }

        const nextPrograms = [
            ...activePlan.programs,
            {
                id: program.id,
                name: program.name,
                catalogYear: program.catalogYear,
                advisor: program.advisor,
                programType: program.programType,
                degreeType: program.degreeType ?? undefined,
                college: program.college ?? undefined,
                catalogVersionId: program.catalogVersionId ?? undefined,
            },
        ];

        actions.updatePrograms(activePlan.id, nextPrograms);
        setSelectedProgramId(program.id);
    };

    // ── Empty states ──────────────────────────────────────────────────

    if (!activePlan) {
        return (
            <EmptyState
                title="No active plan"
                description="Select or create a plan to review requirements."
            />
        );
    }

    if (activePlan.programs.length === 0) {
        return (
            <>
                <EmptyState
                    title="No programs in this plan"
                    description="Add a degree program to see your requirements checklist."
                    action={
                        <Button
                            variant="default"
                            className="bg-brandPrimary text-white hover:bg-brandPrimary/90"
                            onClick={() => setAddProgramOpen(true)}
                        >
                            Add Program
                        </Button>
                    }
                />
                <AddDegreeModal
                    open={addProgramOpen}
                    onOpenChange={setAddProgramOpen}
                    onAdd={handleAddProgram}
                />
            </>
        );
    }

    // ── Main render ───────────────────────────────────────────────────

    return (
        <div className="space-y-4">
            {/* ── Program selector header ─────────────────────────── */}
            <div className="flex flex-col gap-3 rounded-xl border border-brandBorder bg-brandSurface p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between">
                <div className="flex items-center gap-3">
                    <div>
                        <h2 className="text-base font-semibold text-gray-900 font-display">
                            {selectedProgram?.name ?? "Program Requirements"}
                        </h2>
                        <p className="text-xs text-gray-500">
                            {selectedProgram?.catalogYear && `Catalog ${selectedProgram.catalogYear}`}
                            {selectedProgram?.degreeType && ` · ${selectedProgram.degreeType}`}
                        </p>
                    </div>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                    {activePlan.programs.length > 1 && (
                        <Select
                            value={selectedProgramId}
                            onValueChange={setSelectedProgramId}
                        >
                            <SelectTrigger className="w-[200px]">
                                <SelectValue placeholder="Select program" />
                            </SelectTrigger>
                            <SelectContent>
                                {activePlan.programs.map((p) => (
                                    <SelectItem key={p.id} value={p.id}>
                                        {p.name}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    )}
                    <Button
                        size="sm"
                        variant="outline"
                        className="border-brandPrimary text-brandPrimary"
                        onClick={() => setAddProgramOpen(true)}
                    >
                        Add Program
                    </Button>
                    {activePlan.programs.length > 1 && (
                        <div className="flex flex-wrap gap-1.5">
                            {activePlan.programs.map((p) => (
                                <span
                                    key={`${p.id}-${p.catalogYear}`}
                                    className="flex items-center gap-1 rounded-full border border-brandBorder bg-brandWash/60 px-2 py-0.5 text-[11px] text-gray-500"
                                >
                                    {p.name}
                                    <button
                                        type="button"
                                        className="rounded-full p-0.5 text-gray-400 hover:text-brandPrimary"
                                        aria-label={`Remove ${p.name}`}
                                        onClick={() =>
                                            actions.removeProgramFromPlan(
                                                activePlan.id,
                                                p.id,
                                                p.catalogYear
                                            )
                                        }
                                    >
                                        <X className="h-2.5 w-2.5" />
                                    </button>
                                </span>
                            ))}
                        </div>
                    )}
                </div>
            </div>

            {/* ── Loading / error states ───────────────────────────── */}
            {requirementsLoading && (
                <div className="flex items-center justify-center gap-2 rounded-xl border border-dashed border-brandBorder bg-brandSurface/80 py-12">
                    <Loader2 className="h-4 w-4 animate-spin text-gray-400" />
                    <span className="text-sm text-gray-500">Loading requirements...</span>
                </div>
            )}

            {requirementsError && (
                <EmptyState
                    title="Unable to load requirements"
                    description={requirementsError}
                />
            )}

            {!requirementsLoading &&
                !requirementsError &&
                requirementTree &&
                sectionNodes.length === 0 &&
                noteNodes.length === 0 && (
                    <EmptyState
                        title="No requirement data"
                        description="This program does not have requirement data yet."
                    />
                )}

            {/* ── Progress summary + filter ────────────────────────── */}
            {!requirementsLoading && !requirementsError && requirementTree && (
                <ProgressSummary
                    requirementTree={requirementTree}
                    sectionNodes={sectionNodes}
                    planCourses={planCourseCodes}
                    totalPlanCredits={totalPlanCredits}
                    filter={filter}
                    onFilterChange={setFilter}
                    precomputedSummaries={sectionSummaries}
                />
            )}

            {/* ── Sticky breadcrumb ────────────────────────────────── */}
            {stickySection && !requirementsLoading && (
                <div className="sticky top-0 z-10 -mx-1 px-1">
                    <div className="rounded-lg border border-brandBorder bg-brandSurface px-3 py-1.5 text-xs font-medium text-gray-600 shadow-sm backdrop-blur">
                        {selectedProgram?.name ?? "Requirements"}{" "}
                        <span className="text-gray-400">{">"}</span>{" "}
                        {stickySection}
                    </div>
                </div>
            )}

            {/* ── Requirement sections (accordions) ────────────────── */}
            {!requirementsLoading &&
                !requirementsError &&
                sectionNodes.length > 0 && (
                    <div className="space-y-3">
                        {sectionNodes.map((node) => {
                            const name = node.name?.trim() || "Requirements";
                            return (
                                <div
                                    key={node.id}
                                    ref={(el) =>
                                        registerSectionRef(String(node.id), name, el)
                                    }
                                >
                                    <RequirementSection
                                        node={node}
                                        plan={activePlan}
                                        planCourses={planCourseCodes}
                                        filter={filter}
                                        precomputedSummary={sectionSummaries.get(node.id)}
                                        onAddCourse={handleAddCourse}
                                    />
                                </div>
                            );
                        })}

                        {/* Top-level notes (quiet) */}
                        {noteNodes.length > 0 && (
                            <details className="rounded-xl border border-brandBorder bg-brandWash/60 px-4 py-3 text-xs text-gray-500">
                                <summary className="cursor-pointer text-sm font-medium text-gray-600">
                                    Program Notes ({noteNodes.length})
                                </summary>
                                <div className="mt-3 space-y-2 leading-relaxed">
                                    {noteNodes.map((note) => (
                                        <p key={note.id} className="italic">
                                            {note.notes ?? note.name ?? "Note"}
                                        </p>
                                    ))}
                                </div>
                            </details>
                        )}
                    </div>
                )}

            <AddDegreeModal
                open={addProgramOpen}
                onOpenChange={setAddProgramOpen}
                onAdd={handleAddProgram}
            />
        </div>
    );
}
