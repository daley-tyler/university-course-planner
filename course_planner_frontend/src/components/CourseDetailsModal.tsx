import { mockCatalog } from "@/mockData/mockCatalog";
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
} from "@/components/ui/dialog";
import { type Course } from "@/types";

type CourseDetailsModalProps = {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    course: Course;
};

export function CourseDetailsModal({ open, onOpenChange, course }: CourseDetailsModalProps) {
    // Invented prototype details.
    const mockDescriptions = Object.fromEntries(mockCatalog.map((entry) => [
        entry.code, `Invented ${entry.title.toLowerCase()} exercise for exploring this planner.`,
    ]));

    const description = mockDescriptions[course.code] || "Course description not available.";

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent
                className="sm:max-w-md !bg-white !text-gray-900 !border !border-gray-300 !shadow-xl !rounded-lg data-[state=open]:!opacity-100"
                style={{
                    backgroundColor: "white",
                    color: "#1f1f1f",
                    zIndex: 9999,
                }}
            >

            <DialogHeader>
                    <DialogTitle className="text-lg font-semibold text-brandPrimary">
                        {course.code}: {course.title}
                    </DialogTitle>
                    <DialogDescription>
                        <p className="text-gray-700 mt-2">
                            <strong>Credits:</strong> {course.credits}
                        </p>
                        <p className="text-gray-700 mt-2 leading-relaxed">{description}</p>

                        {/* Optional example attributes */}
                        <div className="mt-3 space-y-1 text-sm text-gray-700">
                            <p>
                                <strong>Attributes:</strong> General Education Quantitative Reasoning
                            </p>
                            <p>
                                <strong>Instructor:</strong> Sample Instructor
                            </p>
                            <p>
                                <strong>Offered:</strong> Fall & Spring Semesters
                            </p>
                        </div>
                    </DialogDescription>
                </DialogHeader>
            </DialogContent>
        </Dialog>
    );
}
