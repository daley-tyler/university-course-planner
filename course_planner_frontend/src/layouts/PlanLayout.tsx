import { useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ChevronDown, Pencil, Sparkles, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { useActivePlan, usePlanStore } from "@/store/planStore";
import { motion } from "framer-motion";

const tabs = [
    { label: "Requirements", path: "/plan/requirements" },
    { label: "Catalog", path: "/plan/catalog" },
    { label: "Planner", path: "/plan/planner" },
    { label: "Audit", path: "/plan/audit" },
];

export function PlanLayout() {
    const { state, actions } = usePlanStore();
    const activePlan = useActivePlan();
    const navigate = useNavigate();
    const location = useLocation();
    const [editingPlanId, setEditingPlanId] = useState<string | null>(null);
    const [planNameDraft, setPlanNameDraft] = useState("");

    const currentPlan = state.plans.find((plan) => plan.id === state.currentPlanId);
    const whatIfPlans = state.plans.filter((plan) => plan.kind === "WHAT_IF");

    const activeBadge = activePlan?.kind === "CURRENT" ? "Current" : "What-If";

    const handleValidate = () => {
        if (!activePlan) return;
        actions.validatePlan(activePlan.id);
        if (location.pathname !== "/plan/audit") {
            navigate("/plan/audit");
        }
    };

    const handleCreateWhatIf = () => {
        if (!currentPlan) return;
        actions.duplicatePlanAsWhatIf(currentPlan.id);
        navigate("/plan/planner");
    };

    const handlePromote = () => {
        if (!activePlan || activePlan.kind === "CURRENT") return;
        actions.promotePlanToCurrent(activePlan.id);
    };

    const handleDeletePlan = () => {
        if (!activePlan || activePlan.kind === "CURRENT") return;
        const confirmed = window.confirm(`Delete "${activePlan.name}"? This cannot be undone.`);
        if (!confirmed) return;
        actions.removePlan(activePlan.id);
    };

    const startRename = () => {
        if (!activePlan) return;
        setEditingPlanId(activePlan.id);
        setPlanNameDraft(activePlan.name);
    };

    const commitRename = () => {
        if (!activePlan) return;
        const trimmed = planNameDraft.trim();
        if (trimmed.length > 0 && trimmed !== activePlan.name) {
            actions.renamePlan(activePlan.id, trimmed);
        }
        setEditingPlanId(null);
    };

    const cancelRename = () => {
        setEditingPlanId(null);
        setPlanNameDraft("");
    };

    return (
        <motion.div
            className="max-w-7xl mx-auto px-6 py-8 space-y-6"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: "easeOut" }}
        >
            <motion.header
                className="plan-summary rounded-lg border border-brandBorder bg-brandSurface p-6 shadow-sm"
                initial={{ opacity: 0, y: 18 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.45, ease: "easeOut" }}
            >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-center">
                    <div className="flex-1">
                        <p className="text-xs uppercase tracking-[0.3em] text-brandPrimary/70">
                            Active Plan
                        </p>
                        <div className="mt-2 flex flex-wrap items-center gap-3">
                            {editingPlanId === activePlan?.id ? (
                                <Input
                                    value={planNameDraft}
                                    onChange={(event) => setPlanNameDraft(event.target.value)}
                                    onBlur={commitRename}
                                    onKeyDown={(event) => {
                                        if (event.key === "Enter") {
                                            commitRename();
                                        }
                                        if (event.key === "Escape") {
                                            cancelRename();
                                        }
                                    }}
                                    className="h-9 max-w-xs text-base font-semibold"
                                    autoFocus
                                />
                            ) : (
                                <div className="flex items-center gap-2">
                                    <h1 className="text-2xl font-bold text-brandPrimary font-display">
                                        {activePlan?.name ?? "Select a plan"}
                                    </h1>
                                    {activePlan ? (
                                        <Button
                                            variant="ghost"
                                            size="icon"
                                            className="h-8 w-8 text-gray-500 hover:text-brandPrimary"
                                            onClick={startRename}
                                            aria-label="Rename plan"
                                        >
                                            <Pencil className="h-4 w-4" />
                                        </Button>
                                    ) : null}
                                </div>
                            )}
                            <span
                                className={cn(
                                    "rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-widest",
                                    activePlan?.kind === "CURRENT"
                                        ? "bg-emerald-100 text-emerald-700"
                                        : "bg-amber-100 text-amber-700"
                                )}
                            >
                                {activeBadge}
                            </span>
                        </div>
                        <p className="text-sm text-gray-600">
                            Plan-scoped tabs keep requirements, catalog, scheduling, and audit in sync.
                        </p>
                    </div>

                    <div className="flex flex-wrap gap-2">
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
                            <DropdownMenuContent align="end" className="min-w-[220px]">
                                <DropdownMenuLabel>Current</DropdownMenuLabel>
                                {currentPlan ? (
                                    <DropdownMenuItem
                                        onClick={() => actions.setActivePlanId(currentPlan.id)}
                                        className={
                                            currentPlan.id === state.activePlanId
                                                ? "bg-brandPrimary/10"
                                                : ""
                                        }
                                    >
                                        {currentPlan.name}
                                    </DropdownMenuItem>
                                ) : (
                                    <DropdownMenuItem disabled>None</DropdownMenuItem>
                                )}
                                <DropdownMenuSeparator />
                                <DropdownMenuLabel>What-If</DropdownMenuLabel>
                                {whatIfPlans.length === 0 ? (
                                    <DropdownMenuItem disabled>No scenarios yet</DropdownMenuItem>
                                ) : (
                                    whatIfPlans.map((plan) => (
                                        <DropdownMenuItem
                                            key={plan.id}
                                            onClick={() => actions.setActivePlanId(plan.id)}
                                            className={
                                                plan.id === state.activePlanId
                                                    ? "bg-brandPrimary/10"
                                                    : ""
                                            }
                                        >
                                            {plan.name}
                                        </DropdownMenuItem>
                                    ))
                                )}
                                <DropdownMenuSeparator />
                                <DropdownMenuItem onClick={handleCreateWhatIf}>
                                    + New What-If Plan
                                </DropdownMenuItem>
                            </DropdownMenuContent>
                        </DropdownMenu>

                        {activePlan?.kind === "WHAT_IF" ? (
                            <>
                                <Button
                                    variant="outline"
                                    className="border-amber-200 text-amber-700 hover:bg-amber-50"
                                    onClick={handlePromote}
                                >
                                    Make Current
                                </Button>
                                <Button
                                    variant="outline"
                                    className="border-brandBorder text-gray-600 hover:text-brandPrimary hover:border-brandPrimary/40"
                                    onClick={handleDeletePlan}
                                >
                                    <Trash2 className="mr-2 h-4 w-4" />
                                    Delete Plan
                                </Button>
                            </>
                        ) : null}

                        <Button
                            variant="default"
                            className="bg-brandPrimary text-white hover:bg-brandPrimary/90"
                            onClick={handleValidate}
                        >
                            <Sparkles className="mr-2 h-4 w-4" />
                            Check Plan
                        </Button>
                    </div>
                </div>

                <nav className="mt-6 flex flex-wrap gap-2">
                    {tabs.map((tab) => (
                        <motion.div
                            key={tab.path}
                            whileHover={{ y: -2 }}
                            whileTap={{ scale: 0.98 }}
                        >
                            <NavLink
                                to={tab.path}
                                className={({ isActive }) =>
                                    cn(
                                        "inline-flex rounded-sm px-4 py-2 text-sm font-semibold transition",
                                        isActive
                                            ? "bg-brandPrimary text-white"
                                            : "border border-brandBorder bg-brandSurface text-gray-600 hover:border-brandPrimary/40"
                                    )
                                }
                            >
                                {tab.label}
                            </NavLink>
                        </motion.div>
                    ))}
                </nav>
            </motion.header>

            <motion.div
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.35, ease: "easeOut" }}
            >
                <Outlet />
            </motion.div>
        </motion.div>
    );
}
