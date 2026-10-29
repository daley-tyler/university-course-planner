export type BulletinSummaryDto = {
    id: number;
    catalogYear: number | null;
    catalogLabel: string | null;
    bulletinRoot: string | null;
    scrapedAt: string | null;
    isCurrent: boolean;
};

export type BulletinListResponse = {
    items: BulletinSummaryDto[];
};

export type ProgramSummaryDto = {
    id: number;
    code: string;
    name: string;
    programType: string;
    degreeType: string | null;
    college: string | null;
    catalogVersionId: number | null;
};

export type ProgramListResponse = {
    catalogYear: number | null;
    catalogLabel: string | null;
    items: ProgramSummaryDto[];
};

export type RequirementSourceDto = {
    url: string | null;
    ref: string | null;
};

export type RequirementCourseOptionDto = {
    code: string;
    title: string | null;
    credits: number | null;
};

export type RequirementAttributeOptionDto = {
    code: string;
    name: string | null;
    category: string | null;
};

export type RequirementNodeDto = {
    id: number;
    name: string | null;
    kind: string | null;
    nRequired: number | null;
    creditMin: number | null;
    creditMax: number | null;
    notes: string | null;
    levelMin: number | null;
    levelCap100: number | null;
    levelCap200: number | null;
    levelCap300: number | null;
    levelCap400: number | null;
    levelCap100Courses: number | null;
    levelCap200Courses: number | null;
    levelCap300Courses: number | null;
    levelCap400Courses: number | null;
    gradeMin: string | null;
    residencyCredits: number | null;
    doubleCountAllowed: boolean | null;
    doubleCountCap: number | null;
    footnoteText: string | null;
    source: RequirementSourceDto | null;
    courseOptions: RequirementCourseOptionDto[];
    attributeOptions: RequirementAttributeOptionDto[];
    children: RequirementNodeDto[];
};

export type RequirementTreeResponse = {
    programId: number;
    catalogYear: number | null;
    catalogLabel: string | null;
    catalogVersionId: number | null;
    root: RequirementNodeDto | null;
};

export type CourseSummaryDto = {
    id: number;
    code: string;
    title: string | null;
    credits: number | null;
    department: string | null;
    level: number | null;
    attributes: string[];
    prereqs: string[];
};

export type CourseDetailDto = {
    id: number;
    code: string;
    title: string | null;
    credits: number | null;
    department: string | null;
    level: number | null;
    attributes: string[];
    prereqs: string[];
    description: string | null;
    prereqText: string | null;
    coreqText: string | null;
    restrictionText: string | null;
    attributeText: string | null;
};

export type CourseSearchResponse = {
    items: CourseSummaryDto[];
    page: number;
    pageSize: number;
    total: number;
    nextPage: number | null;
};

export type DepartmentDto = {
    code: string;
    name: string;
};

export type AttributeDto = {
    code: string;
    name: string;
    category: string | null;
};

export type CourseFiltersResponse = {
    departments: DepartmentDto[];
    levels: number[];
    attributes: AttributeDto[];
};
