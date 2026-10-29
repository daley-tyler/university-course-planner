import { cn } from "@/lib/utils";
import type { RequirementStatus } from "./requirementStatus";

const styles: Record<RequirementStatus, { ring: string; fill: string }> = {
    DONE: { ring: "border-emerald-500", fill: "bg-emerald-500" },
    IN_PROGRESS: { ring: "border-amber-400", fill: "bg-amber-400" },
    NEEDED: { ring: "border-gray-300", fill: "bg-transparent" },
};

export function StatusIcon({
    status,
    size = "sm",
}: {
    status: RequirementStatus;
    size?: "sm" | "md";
}) {
    const s = styles[status];
    const dim = size === "md" ? "h-4 w-4" : "h-3 w-3";
    const inner = size === "md" ? "h-2 w-2" : "h-1.5 w-1.5";

    if (status === "DONE") {
        return (
            <span
                className={cn(dim, "flex-shrink-0 rounded-full flex items-center justify-center", s.fill)}
                aria-label="Covered in plan"
            >
                <svg
                    viewBox="0 0 12 12"
                    className={cn(inner, "text-white")}
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2.5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                >
                    <path d="M2.5 6.5L5 9L9.5 3.5" />
                </svg>
            </span>
        );
    }

    if (status === "IN_PROGRESS") {
        return (
            <span
                className={cn(
                    dim,
                    "flex-shrink-0 rounded-full border-2 flex items-center justify-center",
                    s.ring
                )}
                aria-label="In progress"
            >
                <span className={cn("rounded-full", inner, s.fill)} />
            </span>
        );
    }

    return (
        <span
            className={cn(dim, "flex-shrink-0 rounded-full border-2", s.ring)}
            aria-label="Needed"
        />
    );
}
