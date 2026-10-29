// Invented courses shared with the local demonstration catalog.
export type MockCatalogCourse = {
    id: string;
    code: string;
    title: string;
    credits: number;
    department: string;
    level: number;
    attributes: string[];
    prereqs: string[];
};

export const mockCatalog: MockCatalogCourse[] = [
    {
        "id": "demo-101",
        "code": "DEMO 101",
        "title": "Programming Foundations",
        "credits": 3,
        "department": "DEMO",
        "level": 100,
        "attributes": [],
        "prereqs": []
    },
    {
        "id": "demo-201",
        "code": "DEMO 201",
        "title": "Data Structures",
        "credits": 3,
        "department": "DEMO",
        "level": 200,
        "attributes": [],
        "prereqs": [
            "DEMO 101"
        ]
    },
    {
        "id": "demo-210",
        "code": "DEMO 210",
        "title": "Database Design",
        "credits": 3,
        "department": "DEMO",
        "level": 200,
        "attributes": [],
        "prereqs": []
    },
    {
        "id": "demo-301",
        "code": "DEMO 301",
        "title": "Software Engineering",
        "credits": 3,
        "department": "DEMO",
        "level": 300,
        "attributes": [],
        "prereqs": [
            "DEMO 201"
        ]
    },
    {
        "id": "demo-310",
        "code": "DEMO 310",
        "title": "Applied Statistics",
        "credits": 3,
        "department": "DEMO",
        "level": 300,
        "attributes": [],
        "prereqs": []
    },
    {
        "id": "demo-401",
        "code": "DEMO 401",
        "title": "Team Project",
        "credits": 3,
        "department": "DEMO",
        "level": 400,
        "attributes": [],
        "prereqs": []
    }
];
