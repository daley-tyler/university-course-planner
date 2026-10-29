// src/types.ts
export type Course = {
    id: string;
    code: string;
    title: string;
    credits: number;
};

export type Semester = {
    id: string;
    name: string;
    courses: Course[];
};
