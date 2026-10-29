import { createContext } from "react";
import type { PlanState, PlanActions } from "./planTypes";
export const PlanStoreContext = createContext<{ state: PlanState; actions: PlanActions } | null>(null);
