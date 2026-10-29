// Original examples for the legacy prototype, not an academic program.
export type Requirement =
    | { type: "single"; label: string; courses: string[] }
    | { type: "or"; label: string; courses: string[] }
    | { type: "and"; label: string; courses: string[] };

export interface RequirementGroup {
    name: string;
    items: Requirement[];
    subgroups?: RequirementGroup[];
}

export const degreeRequirements: Record<string, RequirementGroup[]> = {
    demo: [{
        name: "Invented studio pathway",
        items: [
            { type: "single", label: "Start with foundations", courses: ["DEMO 101"] },
            { type: "or", label: "Explore databases or statistics", courses: ["DEMO 210", "DEMO 310"] },
            { type: "and", label: "Complete the project pair", courses: ["DEMO 301", "DEMO 401"] },
        ],
    }],
};
