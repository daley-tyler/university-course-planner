import { mockCatalog } from "@/mockData/mockCatalog";
import { useEffect, useState } from "react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Search, SlidersHorizontal, Info, Plus, ChevronDown, X } from "lucide-react";
import { CourseSearchFilterModal } from "@/components/CourseSearchFilterModal";
import { DegreeRequirementsCard } from "@/components/DegreeRequirementsCard";
import { Card, CardContent } from "@/components/ui/card";
import { motion } from "framer-motion";
import { toast } from "sonner";
import { EmptyState } from "@/components/EmptyState";
import {
    DropdownMenu,
    DropdownMenuTrigger,
    DropdownMenuContent,
    DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import { CourseDetailsModal } from "@/components/CourseDetailsModal";
import { useAriaAnnouncer } from "@/hooks/useAriaAnnouncer";

// Invented prototype plan; the routed application loads its catalog from the API.
const mockPlans = [{
    id: "demo-plan",
    name: "Demonstration Plan",
    degrees: [{ id: "demo", name: "Invented Studio Pathway", advisor: "Demo Advisor", catalogYear: "2025-2026" }],
}];

interface Course {
    id: string;
    code: string;
    title: string;
    credits: number;
    department: string;
    level: number;
    attributes: string[];
}

type RequirementFilter = {
    label: string;
    programName: string;
};

export default function CourseSearch() {
    const { message: ariaMessage, announce } = useAriaAnnouncer();

    const [plans] = useState(mockPlans);
    const [activePlanId, setActivePlanId] = useState(plans[0].id);
    const activePlan = plans.find((p) => p.id === activePlanId);

    const [query, setQuery] = useState("");
    const [filters, setFilters] = useState({
        departments: [] as string[],
        levels: [] as string[],
        attributes: [] as string[],
        minCredits: "",
        maxCredits: "",
    });
    const [filterOpen, setFilterOpen] = useState(false);
    const [remainingCourses, setRemainingCourses] = useState<Course[]>([]);
    const [completedCourses, setCompletedCourses] = useState<Course[]>([]);
    const [detailsOpen, setDetailsOpen] = useState(false);
    const [selectedCourse, setSelectedCourse] = useState<Course | null>(null);
    const [selectedCourseCodes, setSelectedCourseCodes] = useState<string[]>([]);
    const [selectedRequirement, setSelectedRequirement] = useState<RequirementFilter | null>(null);

    useEffect(() => {
        setSelectedCourseCodes([]);
        setSelectedRequirement(null);
    }, [activePlanId]);

    const courses: Course[] = mockCatalog;

    const handleFilterByCourses = (label: string, courseCodes: string[], programName: string) => {
        const isSameSelection =
            selectedRequirement?.label === label && selectedRequirement.programName === programName;

        if (isSameSelection) {
            clearRequirementFilter();
            return;
        }

        setSelectedCourseCodes(courseCodes);
        setSelectedRequirement({ label, programName });
        setQuery("");
        announce(`Filtering by ${label} from ${programName}.`);
    };

    const clearRequirementFilter = () => {
        setSelectedCourseCodes([]);
        setSelectedRequirement(null);
        announce("Requirement filter cleared.");
    };

    const matchesText = (course: Course, text: string) => {
        const lower = text.toLowerCase();
        return (
            course.code.toLowerCase().includes(lower) ||
            course.title.toLowerCase().includes(lower)
        );
    };

    const hasQuery = query.trim().length > 0;
    const hasRequirementFilter = selectedCourseCodes.length > 0;

    const filteredCourses = courses.filter((course) => {
        const matchesQuery = hasQuery && matchesText(course, query);
        const matchesSelectedCodes = hasRequirementFilter && selectedCourseCodes.includes(course.code);
        const matchesDepartments =
            filters.departments.length === 0 || filters.departments.includes(course.department);
        const matchesLevels =
            filters.levels.length === 0 ||
            filters.levels.includes(String(Math.floor(course.level / 100) * 100));
        const matchesCredits =
            (!filters.minCredits || course.credits >= Number(filters.minCredits)) &&
            (!filters.maxCredits || course.credits <= Number(filters.maxCredits));
        const matchesAttributes =
            filters.attributes.length === 0 ||
            filters.attributes.some((attr) => course.attributes.includes(attr));

        const hasSearchIntent = hasQuery || hasRequirementFilter;
        if (!hasSearchIntent) return false;

        return (
            (matchesQuery || matchesSelectedCodes) &&
            matchesDepartments &&
            matchesLevels &&
            matchesCredits &&
            matchesAttributes
        );
    });

    const hasFilters =
        hasQuery ||
        hasRequirementFilter ||
        filters.departments.length > 0 ||
        filters.levels.length > 0 ||
        filters.attributes.length > 0 ||
        Boolean(filters.minCredits) ||
        Boolean(filters.maxCredits);

    const addToRemaining = (course: Course) => {
        if (remainingCourses.some((r) => r.id === course.id)) {
            toast.error("Course already in Remaining");
            announce(`${course.code} is already in Remaining Courses.`);
            return;
        }
        setRemainingCourses([...remainingCourses, course]);
        toast.success(`${course.code} added to Remaining Courses`);
        announce(`${course.code} added to Remaining Courses.`);
    };

    const addToCompleted = (course: Course) => {
        if (completedCourses.some((r) => r.id === course.id)) {
            toast.error("Course already in Completed");
            announce(`${course.code} is already in Completed Courses.`);
            return;
        }
        setCompletedCourses([...completedCourses, course]);
        toast.success(`${course.code} added to Completed Courses`);
        announce(`${course.code} added to Completed Courses.`);
    };

    const emptyResultsMessage = () => {
        if (!hasQuery && !hasRequirementFilter) {
            return {
                title: "Start with a search or requirement",
                description:
                    "Type a course code, or choose a degree requirement to filter the catalog.",
            };
        }

        return {
            title: "No courses found",
            description: "Try adjusting your query or clearing active filters.",
        };
    };

    return (
        <div className="max-w-7xl mx-auto px-6 py-10 space-y-10">
            <div className="sr-only" aria-live="polite" aria-atomic="true">
                {ariaMessage}
            </div>

            <section className="grid gap-6 lg:grid-cols-[1.3fr_0.7fr]">
                <div className="rounded-2xl border border-gray-200 bg-white/90 p-6 shadow-lg">
                    <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-4">
                        <div>
                            <p className="text-xs uppercase tracking-[0.2em] text-brandPrimary/80">
                                Course Catalog
                            </p>
                            <h1 className="text-3xl font-bold text-brandPrimary font-display">
                                Course Search
                            </h1>
                            <p className="text-sm text-gray-600">
                                Filter by requirement sets or search across the catalog.
                            </p>
                        </div>
                        {selectedRequirement ? (
                            <div className="rounded-xl border border-brandPrimary/20 bg-brandPrimary/10 px-4 py-2 text-xs text-brandPrimary">
                                <div className="text-[10px] uppercase tracking-widest text-brandPrimary/80">
                                    {selectedRequirement.programName}
                                </div>
                                <div className="flex items-center gap-2">
                                    <span
                                        className="max-w-[220px] truncate font-semibold"
                                        title={selectedRequirement.label}
                                    >
                                        {selectedRequirement.label}
                                    </span>
                                    <Button
                                        variant="ghost"
                                        size="icon"
                                        className="h-6 w-6 text-brandPrimary hover:bg-brandPrimary/20"
                                        onClick={clearRequirementFilter}
                                        aria-label="Clear requirement filter"
                                    >
                                        <X className="h-3 w-3" />
                                    </Button>
                                </div>
                            </div>
                        ) : null}
                    </div>

                    <div className="mt-5 flex flex-col lg:flex-row gap-3">
                        <div className="flex flex-1 items-center gap-2">
                            <Input
                                value={query}
                                onChange={(e) => setQuery(e.target.value)}
                                placeholder="Search by course code or title..."
                                className="bg-white shadow-sm"
                            />
                            <Button
                                variant="outline"
                                className="border-brandPrimary text-brandPrimary hover:bg-brandPrimary hover:text-white"
                            >
                                <Search className="w-4 h-4 mr-2" />
                                Search
                            </Button>
                        </div>
                        <div className="flex gap-2">
                            <Button
                                onClick={() => setFilterOpen(true)}
                                className="bg-brandPrimary hover:bg-red-700 text-white"
                            >
                                <SlidersHorizontal className="w-4 h-4 mr-2" />
                                Filters
                            </Button>
                        </div>
                    </div>

                    {hasFilters && (
                        <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-gray-600">
                            <span className="rounded-full border border-gray-200 bg-gray-50 px-3 py-1">
                                Filters active
                            </span>
                            {hasRequirementFilter && (
                                <span className="rounded-full border border-brandPrimary/20 bg-brandPrimary/10 px-3 py-1 text-brandPrimary">
                                    Requirement set selected
                                </span>
                            )}
                        </div>
                    )}
                </div>

                <div className="rounded-2xl border border-gray-200 bg-brandWash/80 p-6 shadow-sm">
                    <p className="text-xs uppercase tracking-[0.2em] text-gray-600">Active Plan</p>
                    <div className="mt-2 flex items-center justify-between gap-2">
                        <h2 className="text-lg font-semibold text-brandPrimary font-display">
                            {activePlan?.name || "Select Plan"}
                        </h2>
                        <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                                <Button
                                    variant="outline"
                                    className="border-brandPrimary text-brandPrimary hover:bg-brandPrimary hover:text-white"
                                >
                                    Switch Plan
                                    <ChevronDown className="ml-2 h-4 w-4" />
                                </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                                {plans.map((plan) => (
                                    <DropdownMenuItem
                                        key={plan.id}
                                        onClick={() => setActivePlanId(plan.id)}
                                        className={plan.id === activePlanId ? "bg-brandPrimary/10" : ""}
                                    >
                                        {plan.name}
                                    </DropdownMenuItem>
                                ))}
                            </DropdownMenuContent>
                        </DropdownMenu>
                    </div>

                    <div className="mt-4 space-y-2">
                        {activePlan?.degrees.map((degree) => (
                            <div
                                key={degree.id}
                                className="rounded-lg border border-white/70 bg-white/80 px-3 py-2"
                            >
                                <p className="text-sm font-semibold text-gray-800">{degree.name}</p>
                                <p className="text-xs text-gray-500">
                                    {degree.catalogYear} • {degree.advisor}
                                </p>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            <section className="grid gap-6 lg:grid-cols-[360px_1fr]">
                <aside className="space-y-4 lg:sticky lg:top-24 self-start">
                    <div className="flex items-center justify-between">
                        <h2 className="text-lg font-semibold text-brandPrimary font-display">
                            Requirement Filters
                        </h2>
                        {selectedRequirement ? (
                            <Button
                                variant="ghost"
                                size="sm"
                                className="text-brandPrimary"
                                onClick={clearRequirementFilter}
                            >
                                Clear
                            </Button>
                        ) : null}
                    </div>

                    {activePlan ? (
                        activePlan.degrees.length > 0 ? (
                            <div className="space-y-4">
                                {activePlan.degrees.map((degree) => (
                                    <DegreeRequirementsCard
                                        key={degree.id}
                                        onFilterByCourses={handleFilterByCourses}
                                        programId={degree.id}
                                        programName={degree.name}
                                        catalogYear={degree.catalogYear}
                                        activeRequirementLabel={selectedRequirement?.label}
                                        activeRequirementProgramName={selectedRequirement?.programName}
                                    />
                                ))}
                            </div>
                        ) : (
                            <EmptyState
                                title="No degree programs added"
                                description="Add a program in Planner to see requirement groups here."
                            />
                        )
                    ) : (
                        <EmptyState
                            title="Select a plan to view requirements"
                            description="Choose an academic plan to unlock requirement filters."
                        />
                    )}
                </aside>

                <main className="space-y-4">
                    <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-2">
                        <div>
                            <h2 className="text-lg font-semibold text-brandPrimary font-display">Results</h2>
                            <p className="text-sm text-gray-600">
                                {filteredCourses.length} course{filteredCourses.length === 1 ? "" : "s"} found
                            </p>
                        </div>
                        {selectedRequirement ? (
                            <div className="text-xs text-gray-600">
                                Filtering by: <span className="font-semibold">{selectedRequirement.label}</span>
                            </div>
                        ) : null}
                    </div>

                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        transition={{ delay: 0.2 }}
                        className="grid md:grid-cols-2 xl:grid-cols-3 gap-6"
                    >
                        {filteredCourses.length > 0 ? (
                            filteredCourses.map((course) => (
                                <Card
                                    key={course.id}
                                    className="hover:shadow-lg transition bg-white/90 border border-gray-200 relative"
                                >
                                    <CardContent className="p-4">
                                        <div className="flex justify-between items-start mb-2">
                                            <h3 className="text-lg font-semibold text-brandPrimary">
                                                {course.code}
                                            </h3>

                                            <div className="flex gap-2">
                                                <Button
                                                    variant="ghost"
                                                    size="icon"
                                                    onClick={() => {
                                                        setSelectedCourse(course);
                                                        setDetailsOpen(true);
                                                    }}
                                                    className="text-gray-600 hover:text-brandPrimary"
                                                >
                                                    <Info className="w-4 h-4" />
                                                </Button>

                                                <DropdownMenu>
                                                    <DropdownMenuTrigger asChild>
                                                        <Button
                                                            variant="ghost"
                                                            size="icon"
                                                            className="text-gray-600 hover:text-brandPrimary"
                                                        >
                                                            <Plus className="w-4 h-4" />
                                                        </Button>
                                                    </DropdownMenuTrigger>
                                                    <DropdownMenuContent align="end" className="w-48">
                                                        <DropdownMenuItem
                                                            onClick={() => addToRemaining(course)}
                                                            className="cursor-pointer"
                                                        >
                                                            ➕ Add to Remaining
                                                        </DropdownMenuItem>
                                                        <DropdownMenuItem
                                                            onClick={() => addToCompleted(course)}
                                                            className="cursor-pointer"
                                                        >
                                                            Add to Completed
                                                        </DropdownMenuItem>
                                                    </DropdownMenuContent>
                                                </DropdownMenu>
                                            </div>
                                        </div>

                                        <p className="text-gray-800 mb-2">{course.title}</p>
                                        <div className="flex flex-wrap text-sm text-gray-600 gap-x-4">
                                            <span>{course.department}</span>
                                            <span>Level {course.level}</span>
                                            <span>{course.credits} cr</span>
                                        </div>
                                    </CardContent>
                                </Card>
                            ))
                        ) : (
                            <div className="col-span-full">
                                <EmptyState {...emptyResultsMessage()} />
                            </div>
                        )}
                    </motion.div>
                </main>
            </section>

            <CourseSearchFilterModal
                open={filterOpen}
                onOpenChange={setFilterOpen}
                filters={filters}
                onApply={setFilters}
                departmentOptions={[]}
                attributeOptions={[]}
                levelOptions={[]}
                loading={false}
            />

            {selectedCourse && (
                <CourseDetailsModal
                    open={detailsOpen}
                    onOpenChange={setDetailsOpen}
                    course={selectedCourse}
                />
            )}
        </div>
    );
}
