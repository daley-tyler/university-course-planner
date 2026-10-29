import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogFooter,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Checkbox } from "@/components/ui/checkbox";
import { useEffect, useMemo, useState } from "react";
import { motion } from "framer-motion";
import type { AttributeDto, DepartmentDto } from "@/api/types";

interface FilterProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    filters: {
        departments: string[];
        levels: string[];
        attributes: string[];
        minCredits: string;
        maxCredits: string;
    };
    onApply: (filters: {
        departments: string[];
        levels: string[];
        attributes: string[];
        minCredits: string;
        maxCredits: string;
    }) => void;
    departmentOptions: DepartmentDto[];
    attributeOptions: AttributeDto[];
    levelOptions: number[];
    loading?: boolean;
}

export function CourseSearchFilterModal({
                                            open,
                                            onOpenChange,
                                            filters,
                                            onApply,
                                            departmentOptions,
                                            attributeOptions,
                                            levelOptions,
                                            loading,
                                        }: FilterProps) {
    const [tempFilters, setTempFilters] = useState(filters);
    const [deptSearch, setDeptSearch] = useState("");
    const [attrSearch, setAttrSearch] = useState("");

    useEffect(() => {
        if (open) {
            setTempFilters(filters);
        }
    }, [filters, open]);

    const filteredDepartments = useMemo(() => {
        const query = deptSearch.toLowerCase();
        return departmentOptions.filter((dept) =>
            dept.code.toLowerCase().includes(query) ||
            dept.name.toLowerCase().includes(query)
        );
    }, [deptSearch, departmentOptions]);

    const filteredAttributes = useMemo(() => {
        const query = attrSearch.toLowerCase();
        return attributeOptions.filter((attr) =>
            attr.code.toLowerCase().includes(query) ||
            attr.name.toLowerCase().includes(query)
        );
    }, [attrSearch, attributeOptions]);

    const handleToggle = (field: keyof typeof tempFilters, value: string) => {
        const list = tempFilters[field] as string[];
        const updated = list.includes(value)
            ? list.filter((v) => v !== value)
            : [...list, value];
        setTempFilters({ ...tempFilters, [field]: updated });
    };

    const handleApply = () => {
        onApply(tempFilters);
        onOpenChange(false);
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-2xl">
                <DialogHeader>
                    <DialogTitle className="text-lg font-semibold text-brandPrimary">
                        Filter Courses
                    </DialogTitle>
                </DialogHeader>

                <motion.div
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="grid md:grid-cols-2 gap-6"
                >
                    {/* ---- Departments ---- */}
                    <div>
                        <h3 className="font-semibold text-gray-800 mb-2">Department</h3>
                        <Input
                            placeholder="Search departments..."
                            value={deptSearch}
                            onChange={(e) => setDeptSearch(e.target.value)}
                            className="mb-2"
                        />
                        <ScrollArea className="h-40 border rounded p-2">
                            {loading ? (
                                <p className="text-xs text-gray-500">Loading departments...</p>
                            ) : filteredDepartments.length === 0 ? (
                                <p className="text-xs text-gray-500">No departments found.</p>
                            ) : (
                                filteredDepartments.map((dept) => (
                                    <label
                                        key={dept.code}
                                        className="flex items-center space-x-2 py-1 text-sm cursor-pointer"
                                    >
                                        <Checkbox
                                            checked={tempFilters.departments.includes(dept.code)}
                                            onCheckedChange={() =>
                                                handleToggle("departments", dept.code)
                                            }
                                        />
                                        <span>
                                            {dept.code} · {dept.name}
                                        </span>
                                    </label>
                                ))
                            )}
                        </ScrollArea>
                    </div>

                    {/* ---- Levels ---- */}
                    <div>
                        <h3 className="font-semibold text-gray-800 mb-2">Level</h3>
                        <ScrollArea className="h-40 border rounded p-2">
                            {loading ? (
                                <p className="text-xs text-gray-500">Loading levels...</p>
                            ) : levelOptions.length === 0 ? (
                                <p className="text-xs text-gray-500">No levels found.</p>
                            ) : (
                                levelOptions.map((lvl) => {
                                    const value = String(lvl);
                                    return (
                                        <label
                                            key={value}
                                            className="flex items-center space-x-2 py-1 text-sm cursor-pointer"
                                        >
                                            <Checkbox
                                                checked={tempFilters.levels.includes(value)}
                                                onCheckedChange={() => handleToggle("levels", value)}
                                            />
                                            <span>{value}-level</span>
                                        </label>
                                    );
                                })
                            )}
                        </ScrollArea>
                    </div>

                    {/* ---- Credit Hours ---- */}
                    <div>
                        <h3 className="font-semibold text-gray-800 mb-2">Credit Hours</h3>
                        <div className="flex items-center gap-2">
                            <Input
                                type="number"
                                placeholder="Min"
                                value={tempFilters.minCredits}
                                onChange={(e) =>
                                    setTempFilters({ ...tempFilters, minCredits: e.target.value })
                                }
                                className="w-1/2"
                            />
                            <span className="text-gray-500">–</span>
                            <Input
                                type="number"
                                placeholder="Max"
                                value={tempFilters.maxCredits}
                                onChange={(e) =>
                                    setTempFilters({ ...tempFilters, maxCredits: e.target.value })
                                }
                                className="w-1/2"
                            />
                        </div>
                    </div>

                    {/* ---- Attributes ---- */}
                    <div>
                        <h3 className="font-semibold text-gray-800 mb-2">Attributes</h3>
                        <Input
                            placeholder="Search attributes..."
                            value={attrSearch}
                            onChange={(e) => setAttrSearch(e.target.value)}
                            className="mb-2"
                        />
                        <ScrollArea className="h-40 border rounded p-2">
                            {loading ? (
                                <p className="text-xs text-gray-500">Loading attributes...</p>
                            ) : filteredAttributes.length === 0 ? (
                                <p className="text-xs text-gray-500">No attributes found.</p>
                            ) : (
                                filteredAttributes.map((attr) => (
                                    <label
                                        key={attr.code}
                                        className="flex items-center space-x-2 py-1 text-sm cursor-pointer"
                                    >
                                        <Checkbox
                                            checked={tempFilters.attributes.includes(attr.code)}
                                            onCheckedChange={() =>
                                                handleToggle("attributes", attr.code)
                                            }
                                        />
                                        <span>
                                            {attr.code} · {attr.name}
                                        </span>
                                    </label>
                                ))
                            )}
                        </ScrollArea>
                    </div>
                </motion.div>

                <DialogFooter className="mt-6 flex justify-end gap-2">
                    <Button
                        variant="outline"
                        onClick={() => onOpenChange(false)}
                        className="border-gray-300"
                    >
                        Cancel
                    </Button>
                    <Button
                        onClick={handleApply}
                        className="bg-brandPrimary hover:bg-red-700 text-white"
                    >
                        Apply Filters
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
