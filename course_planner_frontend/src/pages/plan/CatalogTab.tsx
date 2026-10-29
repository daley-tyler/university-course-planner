import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { CourseSearchFilterModal } from "@/components/CourseSearchFilterModal";
import { EmptyState } from "@/components/EmptyState";
import { Search, SlidersHorizontal } from "lucide-react";
import { planHasCourse, useActivePlan, usePlanStore } from "@/store/planStore";
import { cn } from "@/lib/utils";
import { AnimatePresence, motion, type Variants } from "framer-motion";
import {
    fetchCourseFilters,
    fetchRequirementTree,
    searchCourses,
} from "@/api/catalog";
import type {
    CourseFiltersResponse,
    CourseSummaryDto,
    RequirementNodeDto,
} from "@/api/types";

type RequirementFilter = {
    id: string;
    requirementId: number;
    label: string;
    groupName: string;
    programName: string;
};

type RequirementCollectionContext = {
    programName: string;
    includeProgramName: boolean;
    path: string[];
    filters: RequirementFilter[];
    index: Map<string, string[]>;
};

const useDebouncedValue = <T,>(value: T, delayMs: number) => {
    const [debounced, setDebounced] = useState(value);

    useEffect(() => {
        const handle = window.setTimeout(() => setDebounced(value), delayMs);
        return () => window.clearTimeout(handle);
    }, [value, delayMs]);

    return debounced;
};

const normalizeLabel = (value: string | null | undefined, fallback: string) => {
    const trimmed = value?.trim();
    return trimmed ? trimmed : fallback;
};

const buildBadgeLabel = (label: string, programName: string, includeProgramName: boolean) =>
    includeProgramName ? `${programName}: ${label}` : label;

const addIndexLabel = (index: Map<string, string[]>, code: string, label: string) => {
    const existing = index.get(code) ?? [];
    index.set(code, [...existing, label]);
};

const collectRequirementFilters = (
    node: RequirementNodeDto,
    context: RequirementCollectionContext
) => {
    const label = normalizeLabel(node.name, "Requirement");
    const hasCourses = (node.courseOptions?.length ?? 0) > 0;

    if (hasCourses) {
        const groupName =
            context.path.length > 0 ? context.path.join(" / ") : "Program Requirements";
        context.filters.push({
            id: String(node.id),
            requirementId: node.id,
            label,
            groupName,
            programName: context.programName,
        });

        const badgeLabel = buildBadgeLabel(label, context.programName, context.includeProgramName);
        node.courseOptions.forEach((course) => {
            if (!course.code) return;
            addIndexLabel(context.index, course.code, badgeLabel);
        });
    }

    const nextPath = label ? [...context.path, label] : context.path;
    (node.children ?? []).forEach((child) => {
        collectRequirementFilters(child, {
            ...context,
            path: nextPath,
        });
    });
};

const parseCatalogYear = (label?: string | null) => {
    if (!label) return undefined;
    const match = label.match(/\d{4}/);
    return match ? Number(match[0]) : undefined;
};

const pageVariants: Variants = {
    hidden: { opacity: 0, y: 12 },
    show: {
        opacity: 1,
        y: 0,
        transition: { duration: 0.4, ease: "easeOut", when: "beforeChildren", staggerChildren: 0.08 },
    },
};

const sectionVariants: Variants = {
    hidden: { opacity: 0, y: 10 },
    show: { opacity: 1, y: 0, transition: { duration: 0.3, ease: "easeOut" } },
};

const listVariants: Variants = {
    hidden: {},
    show: { transition: { staggerChildren: 0.05 } },
};

const itemVariants: Variants = {
    hidden: { opacity: 0, y: 10 },
    show: { opacity: 1, y: 0, transition: { duration: 0.25, ease: "easeOut" } },
};

export function CatalogTab() {
    const { actions } = usePlanStore();
    const activePlan = useActivePlan();
    const activePlanId = activePlan?.id;
    const programs = activePlan?.programs;
    const catalogLabel = programs?.[0]?.catalogYear ?? null;

    const [query, setQuery] = useState("");
    const [filters, setFilters] = useState({
        departments: [] as string[],
        levels: [] as string[],
        attributes: [] as string[],
        minCredits: "",
        maxCredits: "",
    });
    const [selectedRequirementId, setSelectedRequirementId] = useState<string | null>(null);
    const [filterOpen, setFilterOpen] = useState(false);

    const [courseFilters, setCourseFilters] = useState<CourseFiltersResponse | null>(null);
    const [filtersLoading, setFiltersLoading] = useState(false);
    const [filtersError, setFiltersError] = useState<string | null>(null);

    const [requirementFilters, setRequirementFilters] = useState<RequirementFilter[]>([]);
    const [requirementIndex, setRequirementIndex] = useState<Map<string, string[]>>(new Map());
    const [requirementsLoading, setRequirementsLoading] = useState(false);
    const [requirementsError, setRequirementsError] = useState<string | null>(null);

    const [courses, setCourses] = useState<CourseSummaryDto[]>([]);
    const [total, setTotal] = useState(0);
    const [nextPage, setNextPage] = useState<number | null>(null);
    const [searchLoading, setSearchLoading] = useState(false);
    const [searchError, setSearchError] = useState<string | null>(null);
    const [loadingMore, setLoadingMore] = useState(false);

    const debouncedQuery = useDebouncedValue(query, 300);

    useEffect(() => {
        setSelectedRequirementId(null);
    }, [activePlan?.id]);

    useEffect(() => {
        if (!programs) {
            setRequirementFilters([]);
            setRequirementIndex(new Map());
            setRequirementsError(null);
            setRequirementsLoading(false);
            return;
        }

        const programEntries = programs
            .map((program) => ({ program, id: Number(program.id) }))
            .filter((entry) => Number.isFinite(entry.id));

        if (programEntries.length === 0) {
            setRequirementFilters([]);
            setRequirementIndex(new Map());
            setRequirementsError(null);
            setRequirementsLoading(false);
            return;
        }

        let active = true;
        setRequirementsLoading(true);
        setRequirementsError(null);

        Promise.all(
            programEntries.map((entry) =>
                fetchRequirementTree(entry.id, { catalogLabel: entry.program.catalogYear })
            )
        )
            .then((responses) => {
                if (!active) return;

                const filters: RequirementFilter[] = [];
                const index = new Map<string, string[]>();
                const includeProgramName = programs.length > 1;

                responses.forEach((response, idx) => {
                    const root = response.root;
                    if (!root) return;

                    collectRequirementFilters(root, {
                        programName: programEntries[idx].program.name,
                        includeProgramName,
                        path: [],
                        filters,
                        index,
                    });
                });

                filters.sort((a, b) =>
                    a.programName.localeCompare(b.programName) ||
                    a.groupName.localeCompare(b.groupName) ||
                    a.label.localeCompare(b.label)
                );

                index.forEach((labels, code) => {
                    index.set(code, Array.from(new Set(labels)));
                });

                setRequirementFilters(filters);
                setRequirementIndex(index);
            })
            .catch((err) => {
                if (!active) return;
                setRequirementFilters([]);
                setRequirementIndex(new Map());
                setRequirementsError(
                    err instanceof Error
                        ? err.message
                        : "Failed to load requirement filters."
                );
            })
            .finally(() => {
                if (!active) return;
                setRequirementsLoading(false);
            });

        return () => {
            active = false;
        };
    }, [programs]);

    useEffect(() => {
        if (!selectedRequirementId) return;
        if (!requirementFilters.some((filter) => filter.id === selectedRequirementId)) {
            setSelectedRequirementId(null);
        }
    }, [requirementFilters, selectedRequirementId]);

    useEffect(() => {
        if (!activePlanId) {
            setCourseFilters(null);
            return;
        }

        let active = true;
        setFiltersLoading(true);
        setFiltersError(null);

        const catalogYear = parseCatalogYear(catalogLabel ?? undefined);

        fetchCourseFilters({ catalogYear, catalogLabel: catalogLabel ?? undefined })
            .then((response) => {
                if (!active) return;
                setCourseFilters(response);
            })
            .catch((err) => {
                if (!active) return;
                setFiltersError(err instanceof Error ? err.message : "Failed to load filters.");
                setCourseFilters(null);
            })
            .finally(() => {
                if (!active) return;
                setFiltersLoading(false);
            });

        return () => {
            active = false;
        };
    }, [activePlanId, catalogLabel]);

    const selectedRequirement = useMemo(
        () =>
            requirementFilters.find((filter) => filter.id === selectedRequirementId) ??
            null,
        [requirementFilters, selectedRequirementId]
    );

    useEffect(() => {
        if (!activePlanId) return;

        const controller = new AbortController();
        setSearchLoading(true);
        setSearchError(null);

        const levelValues = filters.levels
            .map((value) => Number(value))
            .filter((value) => Number.isFinite(value));
        const rawMin = filters.minCredits ? Number(filters.minCredits) : null;
        const rawMax = filters.maxCredits ? Number(filters.maxCredits) : null;
        const minCredits = rawMin !== null && !Number.isNaN(rawMin) ? rawMin : null;
        const maxCredits = rawMax !== null && !Number.isNaN(rawMax) ? rawMax : null;
        const catalogYear = parseCatalogYear(catalogLabel ?? undefined);

        searchCourses(
            {
                q: debouncedQuery || undefined,
                department: filters.departments,
                level: levelValues,
                attribute: filters.attributes,
                minCredits,
                maxCredits,
                requirementId: selectedRequirement?.requirementId ?? null,
                catalogYear,
                catalogLabel,
                page: 1,
                pageSize: 24,
            },
            controller.signal
        )
            .then((response) => {
                setCourses(response.items ?? []);
                setTotal(response.total ?? 0);
                setNextPage(response.nextPage ?? null);
            })
            .catch((err) => {
                if (err instanceof DOMException && err.name === "AbortError") return;
                setSearchError(err instanceof Error ? err.message : "Failed to load courses.");
                setCourses([]);
                setTotal(0);
                setNextPage(null);
            })
            .finally(() => {
                setSearchLoading(false);
            });

        return () => {
            controller.abort();
        };
    }, [activePlanId, catalogLabel, debouncedQuery, filters, selectedRequirement?.requirementId]);

    const handleLoadMore = () => {
        if (!activePlan || !nextPage || loadingMore) return;

        setLoadingMore(true);

        const levelValues = filters.levels
            .map((value) => Number(value))
            .filter((value) => Number.isFinite(value));
        const rawMin = filters.minCredits ? Number(filters.minCredits) : null;
        const rawMax = filters.maxCredits ? Number(filters.maxCredits) : null;
        const minCredits = rawMin !== null && !Number.isNaN(rawMin) ? rawMin : null;
        const maxCredits = rawMax !== null && !Number.isNaN(rawMax) ? rawMax : null;
        const catalogYear = parseCatalogYear(catalogLabel ?? undefined);

        searchCourses({
            q: debouncedQuery || undefined,
            department: filters.departments,
            level: levelValues,
            attribute: filters.attributes,
            minCredits,
            maxCredits,
            requirementId: selectedRequirement?.requirementId ?? null,
            catalogYear,
            catalogLabel,
            page: nextPage,
            pageSize: 24,
        })
            .then((response) => {
                setCourses((prev) => [...prev, ...(response.items ?? [])]);
                setTotal(response.total ?? 0);
                setNextPage(response.nextPage ?? null);
            })
            .catch((err) => {
                setSearchError(err instanceof Error ? err.message : "Failed to load courses.");
            })
            .finally(() => {
                setLoadingMore(false);
            });
    };

    const departmentOptions = courseFilters?.departments ?? [];
    const attributeOptions = courseFilters?.attributes ?? [];
    const levelOptions = courseFilters?.levels ?? [];

    if (!activePlan) {
        return (
            <EmptyState
                title="No active plan"
                description="Select or create a plan to browse the catalog."
            />
        );
    }

    return (
        <motion.div className="space-y-6" variants={pageVariants} initial="hidden" animate="show">
            <motion.div variants={sectionVariants}>
                <Card className="border border-brandBorder bg-brandSurface shadow-sm">
                <CardContent className="p-6 space-y-4">
                    <div>
                        <h2 className="text-xl font-semibold text-brandPrimary font-display">Catalog</h2>
                        <p className="text-sm text-gray-600">
                            Search courses and add them directly to the active plan.
                        </p>
                    </div>

                    <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
                        <div className="flex flex-1 items-center gap-2">
                            <Input
                                value={query}
                                onChange={(event) => setQuery(event.target.value)}
                                placeholder="Search by course code or title..."
                                className="bg-brandSurface shadow-sm"
                            />
                            <Button
                                variant="outline"
                                className="border-brandPrimary text-brandPrimary hover:bg-brandPrimary hover:text-white"
                            >
                                <Search className="w-4 h-4 mr-2" />
                                Search
                            </Button>
                        </div>
                        <Button
                            onClick={() => setFilterOpen(true)}
                            className="bg-brandPrimary hover:bg-brandPrimary/90 text-white"
                        >
                            <SlidersHorizontal className="w-4 h-4 mr-2" />
                            Filters
                        </Button>
                    </div>
                    {filtersError ? (
                        <p className="text-xs text-red-600">{filtersError}</p>
                    ) : null}
                </CardContent>
                </Card>
            </motion.div>

            <motion.div variants={sectionVariants}>
                <Card className="border border-brandBorder bg-brandSurface shadow-sm">
                <CardContent className="p-6 space-y-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                        <div>
                            <p className="text-xs uppercase tracking-widest text-gray-500">
                                Requirement Filters
                            </p>
                            <h3 className="text-base font-semibold text-gray-900">
                                Filter by requirement set
                            </h3>
                        </div>
                        {selectedRequirement ? (
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setSelectedRequirementId(null)}
                                className="text-gray-500"
                            >
                                Clear
                            </Button>
                        ) : null}
                    </div>
                    <p className="text-xs text-gray-500">
                        Tap a requirement to narrow the catalog to those courses.
                    </p>
                    {requirementsError ? (
                        <p className="text-xs text-red-600">{requirementsError}</p>
                    ) : null}

                    {requirementsLoading ? (
                        <div className="rounded-lg border border-dashed border-brandBorder bg-brandWash/60 px-3 py-2 text-xs text-gray-500">
                            Loading requirement filters...
                        </div>
                    ) : requirementFilters.length === 0 ? (
                        <div className="rounded-lg border border-dashed border-brandBorder bg-brandWash/60 px-3 py-2 text-xs text-gray-500">
                            No requirement filters are available for this plan.
                        </div>
                    ) : (
                        <div className="max-h-48 overflow-y-auto pr-1">
                            <motion.div className="flex flex-wrap gap-2" variants={listVariants}>
                                {requirementFilters.map((filter) => {
                                    const active = filter.id === selectedRequirementId;
                                    return (
                                        <motion.button
                                            key={filter.id}
                                            type="button"
                                            onClick={() =>
                                                setSelectedRequirementId(active ? null : filter.id)
                                            }
                                            variants={itemVariants}
                                            whileHover={{ y: -2 }}
                                            whileTap={{ scale: 0.98 }}
                                            className={cn(
                                                "flex min-w-[180px] flex-col items-start gap-1 rounded-xl border px-3 py-2 text-left text-xs transition",
                                                active
                                                    ? "border-brandPrimary bg-brandPrimary/10"
                                                    : "border-brandBorder bg-brandSurface hover:border-brandPrimary/40"
                                            )}
                                        >
                                            <span className="text-[10px] uppercase tracking-widest text-gray-500">
                                                {filter.programName}
                                            </span>
                                            <span
                                                className={cn(
                                                    "text-sm font-semibold",
                                                    active ? "text-brandPrimary" : "text-gray-900"
                                                )}
                                            >
                                                {filter.label}
                                            </span>
                                            <span className="text-[11px] text-gray-500">
                                                {filter.groupName}
                                            </span>
                                        </motion.button>
                                    );
                                })}
                            </motion.div>
                        </div>
                    )}
                </CardContent>
                </Card>
            </motion.div>

            <motion.div className="flex flex-wrap items-center justify-between gap-2" variants={sectionVariants}>
                <div>
                    <p className="text-sm text-gray-600">
                        {searchLoading
                            ? "Searching courses..."
                            : `${total} course${total === 1 ? "" : "s"} found`}
                    </p>
                    {selectedRequirement ? (
                        <p className="text-xs text-gray-500">
                            Filter: {selectedRequirement.label} ({selectedRequirement.programName})
                        </p>
                    ) : null}
                </div>
                <span className="text-xs uppercase tracking-widest text-gray-400">
                    Active plan: {activePlan.name}
                </span>
            </motion.div>

            {searchError ? (
                <EmptyState
                    title="Unable to load courses"
                    description={searchError}
                />
            ) : courses.length === 0 && !searchLoading ? (
                <EmptyState
                    title="No courses match your filters"
                    description="Try adjusting your search text or filters."
                />
            ) : (
                <motion.div className="space-y-5" variants={listVariants}>
                    <motion.div className="grid gap-5 lg:grid-cols-2" variants={listVariants}>
                        <AnimatePresence mode="popLayout">
                            {courses.map((course) => {
                                const inPlan = planHasCourse(activePlan, course.code);
                                const requirementLabels = requirementIndex.get(course.code) ?? [];
                                const prereqsMet = course.prereqs.every((code) =>
                                    planHasCourse(activePlan, code)
                                );

                                return (
                                    <motion.div
                                        key={course.id}
                                        layout
                                        initial={{ opacity: 0, y: 12 }}
                                        animate={{ opacity: 1, y: 0 }}
                                        exit={{ opacity: 0, y: -12 }}
                                        transition={{ duration: 0.25, ease: "easeOut" }}
                                    >
                                        <Card
                                            className={cn(
                                                "border border-brandBorder bg-brandSurface shadow-sm transition-shadow hover:shadow-md",
                                                inPlan && "border-brandPrimary/40"
                                            )}
                                        >
                                            <CardContent className="p-5 space-y-3">
                                                <div className="flex items-start justify-between gap-3">
                                                    <div>
                                                        <p className="text-sm font-semibold text-brandPrimary">
                                                            {course.code}
                                                        </p>
                                                        <p className="text-base font-semibold text-gray-900">
                                                            {course.title ?? "Course title"}
                                                        </p>
                                                        <p className="text-xs text-gray-500">
                                                            {course.department ?? "Department"} •{" "}
                                                            {course.level ? `Level ${course.level}` : "Level"} •{" "}
                                                            {course.credits ?? "?"} cr
                                                        </p>
                                                    </div>
                                                    <Button
                                                        size="sm"
                                                        variant={inPlan ? "secondary" : "outline"}
                                                        disabled={inPlan}
                                                        onClick={() =>
                                                            actions.addCourseToPlan(activePlan.id, course.code, {
                                                                title: course.title ?? course.code,
                                                                credits: course.credits ?? undefined,
                                                                prereqs: course.prereqs,
                                                            })
                                                        }
                                                    >
                                                        {inPlan ? "In Plan" : "Add to Plan"}
                                                    </Button>
                                                </div>

                                                <div className="flex flex-wrap items-center gap-2 text-xs">
                                                    <span className="rounded-full border border-brandBorder bg-brandWash/60 px-3 py-1 text-gray-600">
                                                        Counts toward:{" "}
                                                        {requirementLabels.length > 0
                                                            ? requirementLabels.slice(0, 2).join(", ")
                                                            : "No matched requirement"}
                                                        {requirementLabels.length > 2
                                                            ? ` +${requirementLabels.length - 2} more`
                                                            : ""}
                                                    </span>
                                                    {course.prereqs.length > 0 ? (
                                                        <span
                                                            className={cn(
                                                                "rounded-full border px-3 py-1",
                                                                prereqsMet
                                                                    ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                                                                    : "border-amber-200 bg-amber-50 text-amber-700"
                                                            )}
                                                        >
                                                            Prereq: {course.prereqs.join(", ")} ({
                                                                prereqsMet ? "met" : "unmet"
                                                            })
                                                        </span>
                                                    ) : (
                                                        <span className="rounded-full border border-brandBorder bg-brandWash/60 px-3 py-1 text-gray-500">
                                                            Prereq: none
                                                        </span>
                                                    )}
                                                </div>
                                            </CardContent>
                                        </Card>
                                    </motion.div>
                                );
                            })}
                        </AnimatePresence>
                    </motion.div>

                    {nextPage ? (
                        <motion.div className="flex justify-center" variants={itemVariants}>
                            <Button
                                variant="outline"
                                onClick={handleLoadMore}
                                disabled={loadingMore}
                                className="border-brandPrimary text-brandPrimary"
                            >
                                {loadingMore ? "Loading..." : "Load more courses"}
                            </Button>
                        </motion.div>
                    ) : null}
                </motion.div>
            )}

            <CourseSearchFilterModal
                open={filterOpen}
                onOpenChange={setFilterOpen}
                filters={filters}
                onApply={setFilters}
                departmentOptions={departmentOptions}
                attributeOptions={attributeOptions}
                levelOptions={levelOptions}
                loading={filtersLoading}
            />
        </motion.div>
    );
}
