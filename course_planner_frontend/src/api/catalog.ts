import { apiGet, cachedGet } from "@/api/client";
import type {
    BulletinListResponse,
    CourseDetailDto,
    CourseFiltersResponse,
    CourseSearchResponse,
    ProgramListResponse,
    RequirementTreeResponse,
} from "@/api/types";

type CatalogParams = {
    catalogYear?: number;
    catalogLabel?: string;
};

type CourseSearchParams = {
    q?: string;
    department?: string[];
    level?: number[];
    attribute?: string[];
    minCredits?: number | null;
    maxCredits?: number | null;
    requirementId?: number | null;
    catalogYear?: number | null;
    catalogLabel?: string | null;
    page?: number;
    pageSize?: number;
};

export const fetchBulletins = (limit = 5) =>
    cachedGet(`bulletins:${limit}`, () =>
        apiGet<BulletinListResponse>("/api/bulletins", { limit })
    );

export const fetchPrograms = (params: CatalogParams) => {
    const key = `programs:${params.catalogYear ?? "na"}:${params.catalogLabel ?? "na"}`;
    return cachedGet(key, () =>
        apiGet<ProgramListResponse>("/api/programs", {
            catalogYear: params.catalogYear,
            catalogLabel: params.catalogLabel,
        })
    );
};

export const fetchRequirementTree = (
    programId: number,
    params: CatalogParams
) => {
    const key = `requirements:${programId}:${params.catalogYear ?? "na"}:${params.catalogLabel ?? "na"}`;
    return cachedGet(key, () =>
        apiGet<RequirementTreeResponse>(`/api/programs/${programId}/requirements`, {
            catalogYear: params.catalogYear,
            catalogLabel: params.catalogLabel,
        })
    );
};

export const searchCourses = (params: CourseSearchParams, signal?: AbortSignal) =>
    apiGet<CourseSearchResponse>(
        "/api/courses",
        {
            q: params.q,
            department: params.department,
            level: params.level,
            attribute: params.attribute,
            minCredits: params.minCredits,
            maxCredits: params.maxCredits,
            requirementId: params.requirementId,
            catalogYear: params.catalogYear,
            catalogLabel: params.catalogLabel,
            page: params.page,
            pageSize: params.pageSize,
        },
        { signal }
    );

export const fetchCourseFilters = (params: CatalogParams) => {
    const key = `filters:${params.catalogYear ?? "na"}:${params.catalogLabel ?? "na"}`;
    return cachedGet(key, () =>
        apiGet<CourseFiltersResponse>("/api/courses/filters", {
            catalogYear: params.catalogYear,
            catalogLabel: params.catalogLabel,
        })
    );
};

export const fetchCourseDetail = (courseCode: string) =>
    cachedGet(`course:${courseCode}`, () =>
        apiGet<CourseDetailDto>(`/api/courses/${encodeURIComponent(courseCode)}`)
    );
