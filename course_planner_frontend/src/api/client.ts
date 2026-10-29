const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

type QueryValue = string | number | boolean | null | undefined;

type QueryParams = Record<string, QueryValue | QueryValue[]>;

type RequestOptions = {
    signal?: AbortSignal;
};

const cache = new Map<string, Promise<unknown>>();

const buildQueryString = (params?: QueryParams) => {
    if (!params) return "";
    const search = new URLSearchParams();

    Object.entries(params).forEach(([key, value]) => {
        if (value === null || value === undefined) return;
        if (Array.isArray(value)) {
            value.forEach((item) => {
                if (item === null || item === undefined) return;
                search.append(key, String(item));
            });
            return;
        }
        search.append(key, String(value));
    });

    const query = search.toString();
    return query ? `?${query}` : "";
};

function getCsrfToken(): string | null {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
}

async function handleResponse<T>(response: Response): Promise<T> {
    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || `Request failed with status ${response.status}`);
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
}

export async function apiGet<T>(
    path: string,
    params?: QueryParams,
    options?: RequestOptions
): Promise<T> {
    const url = `${API_BASE}${path}${buildQueryString(params)}`;
    const response = await fetch(url, {
        method: "GET",
        credentials: "include",
        headers: {
            Accept: "application/json",
        },
        signal: options?.signal,
    });

    return handleResponse<T>(response);
}

export async function apiPost<T>(
    path: string,
    body?: unknown,
    options?: RequestOptions
): Promise<T> {
    const url = `${API_BASE}${path}`;
    const csrf = getCsrfToken();
    const response = await fetch(url, {
        method: "POST",
        credentials: "include",
        headers: {
            "Content-Type": "application/json",
            Accept: "application/json",
            ...(csrf ? { "X-XSRF-TOKEN": csrf } : {}),
        },
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: options?.signal,
    });

    return handleResponse<T>(response);
}

export async function apiPut<T>(
    path: string,
    body?: unknown,
    options?: RequestOptions
): Promise<T> {
    const url = `${API_BASE}${path}`;
    const csrf = getCsrfToken();
    const response = await fetch(url, {
        method: "PUT",
        credentials: "include",
        headers: {
            "Content-Type": "application/json",
            Accept: "application/json",
            ...(csrf ? { "X-XSRF-TOKEN": csrf } : {}),
        },
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: options?.signal,
    });

    return handleResponse<T>(response);
}

export async function apiDelete<T>(
    path: string,
    options?: RequestOptions
): Promise<T> {
    const url = `${API_BASE}${path}`;
    const csrf = getCsrfToken();
    const response = await fetch(url, {
        method: "DELETE",
        credentials: "include",
        headers: {
            Accept: "application/json",
            ...(csrf ? { "X-XSRF-TOKEN": csrf } : {}),
        },
        signal: options?.signal,
    });

    return handleResponse<T>(response);
}

export async function cachedGet<T>(
    key: string,
    fetcher: () => Promise<T>
): Promise<T> {
    const existing = cache.get(key);
    if (existing) {
        return existing as Promise<T>;
    }
    const promise = fetcher();
    cache.set(key, promise);
    try {
        return await promise;
    } catch (error) {
        cache.delete(key);
        throw error;
    }
}
