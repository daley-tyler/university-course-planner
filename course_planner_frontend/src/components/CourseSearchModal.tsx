import { mockCatalog } from "@/mockData/mockCatalog";
import { useState, useMemo } from "react";
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Plus } from "lucide-react";

type Course = {
    id: string;
    code: string;
    title: string;
    credits: number;
    instructor: string;
};

type CourseSearchModalProps = {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onAddCourse: (course: Course) => void; // callback to parent (Planner)
};

const mockCourses: Course[] = mockCatalog.map(({ id, code, title, credits }) => ({
    id, code, title, credits, instructor: "Demo Advisor",
}));

export function CourseSearchModal({ open, onOpenChange, onAddCourse }: CourseSearchModalProps) {
    const [query, setQuery] = useState("");

    // Filtered results
    const filteredCourses = useMemo(() => {
        if (!query.trim()) return mockCourses.slice(0, 10);
        const lower = query.toLowerCase();
        return mockCourses
            .filter(
                (c) =>
                    c.code.toLowerCase().includes(lower) ||
                    c.title.toLowerCase().includes(lower) ||
                    c.instructor.toLowerCase().includes(lower)
            )
            .slice(0, 10);
    }, [query]);

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent
                className="sm:max-w-lg bg-white text-gray-900 p-6"
                style={{ height: "500px" }} // fixed modal height
            >
                <DialogHeader>
                    <DialogTitle className="text-lg font-semibold text-brandPrimary">
                        Search Courses
                    </DialogTitle>
                    <DialogDescription>Type to find courses by code, title, or instructor.</DialogDescription>
                </DialogHeader>

                {/* Search Bar */}
                <div className="mt-4">
                    <Input
                        placeholder="Search courses..."
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        className="border-gray-300 focus-visible:ring-brandPrimary"
                    />
                </div>

                {/* 🧾 Results List */}
                <ScrollArea className="mt-4 max-h-[350px] pr-2">
                    <div className="space-y-2">
                        {filteredCourses.length > 0 ? (
                            filteredCourses.map((c) => (
                                <Card
                                    key={c.id}
                                    className="p-3 border border-gray-200 flex justify-between items-center hover:border-brandPrimary transition"
                                >
                                    <div>
                                        <p className="font-semibold text-gray-800">{c.code}</p>
                                        <p className="text-sm text-gray-700">{c.title}</p>
                                        <p className="text-xs text-gray-500 mt-1">
                                            {c.credits} credits • {c.instructor}
                                        </p>
                                    </div>
                                    {/* ➕ Add button */}
                                    <Button
                                        size="icon"
                                        variant="outline"
                                        className="border-gray-300 hover:bg-brandPrimary hover:text-white"
                                        onClick={() => onAddCourse(c)}
                                    >
                                        <Plus className="w-4 h-4" />
                                    </Button>
                                </Card>
                            ))
                        ) : (
                            <p className="text-gray-500 italic text-sm mt-4 text-center">
                                No matching courses found.
                            </p>
                        )}
                    </div>
                </ScrollArea>
            </DialogContent>
        </Dialog>
    );
}
