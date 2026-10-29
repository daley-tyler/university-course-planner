import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { AlertTriangle, CheckCircle2, ShieldAlert } from "lucide-react";
import { type ValidationIssue, useActivePlan, usePlanStore } from "@/store/planStore";
import { motion, type Variants } from "framer-motion";

const statusStyles = {
    PASS: "bg-emerald-100 text-emerald-700",
    WARN: "bg-amber-100 text-amber-700",
    FAIL: "bg-rose-100 text-rose-700",
} as const;

const statusIcons = {
    PASS: CheckCircle2,
    WARN: AlertTriangle,
    FAIL: ShieldAlert,
} as const;

const pageVariants: Variants = {
    hidden: { opacity: 0, y: 12 },
    show: {
        opacity: 1,
        y: 0,
        transition: { duration: 0.35, ease: "easeOut", when: "beforeChildren", staggerChildren: 0.08 },
    },
};

const cardVariants: Variants = {
    hidden: { opacity: 0, y: 10 },
    show: { opacity: 1, y: 0, transition: { duration: 0.25, ease: "easeOut" } },
};

export function AuditTab() {
    const { actions, state } = usePlanStore();
    const activePlan = useActivePlan();
    const navigate = useNavigate();

    const validation = activePlan ? state.validationByPlan[activePlan.id] : undefined;

    const issuesByKind = useMemo(() => {
        if (!validation) return { REQUIREMENT: [], COURSE: [], SCHEDULE: [] };
        return validation.issues.reduce(
            (acc, issue) => {
                acc[issue.kind].push(issue);
                return acc;
            },
            {
                REQUIREMENT: [] as typeof validation.issues,
                COURSE: [] as typeof validation.issues,
                SCHEDULE: [] as typeof validation.issues,
            }
        );
    }, [validation]);

    if (!activePlan) {
        return (
            <EmptyState
                title="No active plan"
                description="Select or create a plan before running an audit."
            />
        );
    }

    if (!validation) {
        return (
            <EmptyState
                title="No audit results yet"
                description="Check unscheduled courses and term credit caps for this plan."
                action={
                    <Button
                        variant="default"
                        className="bg-brandPrimary text-white hover:bg-brandPrimary/90"
                        onClick={() => actions.validatePlan(activePlan.id)}
                    >
                        Check plan
                    </Button>
                }
            />
        );
    }

    const StatusIcon = statusIcons[validation.status];

    const handleIssueClick = (issue: typeof validation.issues[number]) => {
        if (issue.kind === "COURSE" && issue.courseCode) {
            actions.setFocus({ type: "course", value: issue.courseCode });
            navigate("/plan/planner");
            return;
        }

        if (issue.kind === "REQUIREMENT" && issue.requirementLabel) {
            actions.setFocus({ type: "requirement", value: issue.requirementLabel });
            navigate("/plan/requirements");
            return;
        }

        navigate("/plan/planner");
    };

    return (
        <motion.div className="space-y-6" variants={pageVariants} initial="hidden" animate="show">
            <motion.div variants={cardVariants}>
                <Card className="border border-gray-200 bg-white/90 shadow-sm">
                <CardContent className="p-6 space-y-3">
                    <div className="flex flex-wrap items-center gap-3">
                        <span
                            className={cn(
                                "inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-widest",
                                statusStyles[validation.status]
                            )}
                        >
                            <StatusIcon className="h-4 w-4" />
                            {validation.status === "PASS" ? "Basic checks passed" : validation.status}
                        </span>
                        <p className="text-sm text-gray-600">
                            Last run: {new Date(validation.checkedAt).toLocaleString()}
                        </p>
                    </div>
                    <div className="grid gap-3 md:grid-cols-3">
                        <p className="text-sm text-gray-600">Basic planning checks only. This is not a graduation eligibility assessment.</p>
                    <SummaryCard title="Requirements" description={validation.requirementSummary} />
                        <SummaryCard title="Schedule" description={validation.scheduleSummary} />
                        <SummaryCard title="Credits" description={validation.creditSummary} />
                    </div>
                </CardContent>
                </Card>
            </motion.div>

            <motion.section className="grid gap-4 lg:grid-cols-3" variants={cardVariants}>
                <IssueColumn
                    title="Requirement Checks"
                    issues={issuesByKind.REQUIREMENT}
                    onIssueClick={handleIssueClick}
                />
                <IssueColumn
                    title="Schedule Issues"
                    issues={issuesByKind.SCHEDULE}
                    onIssueClick={handleIssueClick}
                />
                <IssueColumn
                    title="Course Issues"
                    issues={issuesByKind.COURSE}
                    onIssueClick={handleIssueClick}
                />
            </motion.section>
        </motion.div>
    );
}

function SummaryCard({ title, description }: { title: string; description: string }) {
    return (
        <motion.div
            className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md"
            variants={cardVariants}
            whileHover={{ y: -2 }}
        >
            <p className="text-xs uppercase tracking-widest text-gray-500">{title}</p>
            <p className="mt-2 text-sm font-semibold text-gray-900">{description}</p>
        </motion.div>
    );
}

function IssueColumn({
    title,
    issues,
    onIssueClick,
}: {
    title: string;
    issues: ValidationIssue[];
    onIssueClick: (issue: ValidationIssue) => void;
}) {
    return (
        <motion.div variants={cardVariants}>
            <Card className="border border-gray-200 bg-white/90 shadow-sm">
                <CardContent className="p-5 space-y-3">
                <div>
                    <p className="text-xs uppercase tracking-widest text-gray-500">{title}</p>
                    <p className="text-sm text-gray-600">
                        {issues.length} issue{issues.length === 1 ? "" : "s"}
                    </p>
                </div>
                {issues.length === 0 ? (
                    <p className="text-sm text-gray-500">{title === "Requirement Checks" ? "Degree requirements are not evaluated by this check." : "No issues found by the basic checks."}</p>
                ) : (
                    <div className="space-y-2">
                        {issues.map((issue) => (
                            <motion.button
                                key={issue.id}
                                type="button"
                                onClick={() => onIssueClick(issue)}
                                className="w-full rounded-xl border border-gray-200 bg-white px-3 py-3 text-left transition hover:border-brandPrimary/40"
                                whileHover={{ y: -2 }}
                                whileTap={{ scale: 0.98 }}
                            >
                                <p className="text-sm font-semibold text-gray-900">{issue.title}</p>
                                <p className="text-xs text-gray-500">{issue.description}</p>
                            </motion.button>
                        ))}
                    </div>
                )}
                </CardContent>
            </Card>
        </motion.div>
    );
}
