import { useState } from "react";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ChevronDown, ChevronUp } from "lucide-react";
import { degreeRequirements } from "@/mockData/mockDegreeRequirements";
import { cn } from "@/lib/utils";
import { EmptyState } from "@/components/EmptyState";

interface DegreeRequirementsCardProps {
    onFilterByCourses: (label: string, courseCodes: string[], programName: string) => void;
    programId: string;
    programName: string;
    catalogYear: string;
    activeRequirementLabel?: string | null;
    activeRequirementProgramName?: string | null;
}

type Requirement =
    | { type: "single"; label: string; courses: string[] }
    | { type: "or"; label: string; courses: string[] }
    | { type: "and"; label: string; courses: string[] };

interface RequirementGroup {
    name: string;
    items: Requirement[];
    subgroups?: RequirementGroup[];
}

const requirementMeta = {
    single: { label: "Required", className: "text-brandPrimary" },
    or: { label: "Any of", className: "text-amber-700" },
    and: { label: "All of", className: "text-emerald-700" },
};

export function DegreeRequirementsCard({
    onFilterByCourses,
    programId,
    programName,
    catalogYear,
    activeRequirementLabel,
    activeRequirementProgramName,
}: DegreeRequirementsCardProps) {
    const [collapsed, setCollapsed] = useState(false);
    const requirements: RequirementGroup[] = degreeRequirements[programId] || [];

    const renderRequirementButton = (req: Requirement, key: string) => {
        const isActive =
            activeRequirementLabel === req.label && activeRequirementProgramName === programName;
        const meta = requirementMeta[req.type];

        return (
            <Button
                key={key}
                variant="outline"
                className={cn(
                    "h-auto w-full items-start justify-between gap-3 whitespace-normal px-3 py-3 text-left",
                    isActive
                        ? "border-brandPrimary bg-brandPrimary text-white hover:bg-brandPrimary/90"
                        : "border-gray-200 bg-white/90 hover:border-brandPrimary/40"
                )}
                onClick={() => onFilterByCourses(req.label, req.courses, programName)}
            >
                <div className="flex flex-col gap-1 text-left">
                    <span
                        className={cn(
                            "text-[11px] uppercase tracking-widest",
                            isActive ? "text-white/80" : meta.className
                        )}
                    >
                        {meta.label}
                    </span>
                    <span className={cn("text-sm font-semibold", isActive ? "text-white" : "text-gray-900")}>
                        {req.label}
                    </span>
                </div>
                <span className={cn("text-xs font-medium", isActive ? "text-white/80" : "text-gray-500")}>
                    {req.courses.length} course{req.courses.length === 1 ? "" : "s"}
                </span>
            </Button>
        );
    };

    return (
        <Card className="bg-white/90 border border-gray-200 shadow-sm">
            <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-3">
                    <div>
                        <CardTitle className="text-brandPrimary text-lg font-bold font-display">
                            {programName}
                        </CardTitle>
                        <p className="text-xs text-gray-500">{catalogYear} catalog</p>
                    </div>
                    <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => setCollapsed((prev) => !prev)}
                        className="text-gray-600 hover:text-brandPrimary"
                        aria-label={collapsed ? "Expand requirements" : "Collapse requirements"}
                    >
                        {collapsed ? <ChevronDown className="h-5 w-5" /> : <ChevronUp className="h-5 w-5" />}
                    </Button>
                </div>
            </CardHeader>

            {!collapsed && (
                <CardContent className="space-y-5">
                    {requirements.length === 0 ? (
                        <EmptyState
                            title="No requirement groups found"
                            description="Add requirement data for this program to enable filtering."
                        />
                    ) : (
                        requirements.map((group) => (
                            <div key={group.name} className="rounded-xl border border-gray-200 bg-brandWash/40 p-4">
                                <div className="flex items-center justify-between gap-2">
                                    <h3 className="text-sm font-semibold text-gray-800">{group.name}</h3>
                                    <span className="text-xs text-gray-500">
                                        {group.items.length + (group.subgroups?.length || 0)} sets
                                    </span>
                                </div>

                                {group.items.length > 0 && (
                                    <div className="mt-3 grid gap-2">
                                        {group.items.map((req) =>
                                            renderRequirementButton(req, `${group.name}-${req.label}`)
                                        )}
                                    </div>
                                )}

                                {group.subgroups?.map((sub) => (
                                    <div
                                        key={sub.name}
                                        className="mt-4 border-t border-dashed border-gray-200 pt-4"
                                    >
                                        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
                                            {sub.name}
                                        </p>
                                        <div className="mt-2 grid gap-2">
                                            {sub.items.map((req) =>
                                                renderRequirementButton(req, `${sub.name}-${req.label}`)
                                            )}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ))
                    )}
                </CardContent>
            )}
        </Card>
    );
}
