import { useEffect, useMemo, useState } from "react";
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { fetchBulletins, fetchPrograms } from "@/api/catalog";
import type { BulletinSummaryDto, ProgramSummaryDto } from "@/api/types";

const formatProgramLabel = (program: ProgramSummaryDto) =>
    program.degreeType ? `${program.name} (${program.degreeType})` : program.name;

type AddDegreeModalProps = {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onAdd: (program: {
        id: string;
        name: string;
        advisor?: string;
        catalogYear: string;
        programType?: string;
        degreeType?: string | null;
        college?: string | null;
        catalogVersionId?: number | null;
    }) => void;
};

export function AddDegreeModal({ open, onOpenChange, onAdd }: AddDegreeModalProps) {
    const [search, setSearch] = useState("");
    const [bulletins, setBulletins] = useState<BulletinSummaryDto[]>([]);
    const [selectedCatalogLabel, setSelectedCatalogLabel] = useState("");
    const [programs, setPrograms] = useState<ProgramSummaryDto[]>([]);
    const [loadingBulletins, setLoadingBulletins] = useState(false);
    const [loadingPrograms, setLoadingPrograms] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!open) return;
        let active = true;
        setLoadingBulletins(true);
        setError(null);

        fetchBulletins(5)
            .then((response) => {
                if (!active) return;
                const items = response.items ?? [];
                setBulletins(items);
                const current = items.find((item) => item.isCurrent);
                const fallback = current ?? items[0];
                setSelectedCatalogLabel(fallback?.catalogLabel ?? "");
            })
            .catch((err) => {
                if (!active) return;
                setError(err instanceof Error ? err.message : "Failed to load bulletins");
            })
            .finally(() => {
                if (!active) return;
                setLoadingBulletins(false);
            });

        return () => {
            active = false;
        };
    }, [open]);

    useEffect(() => {
        if (!selectedCatalogLabel) return;
        let active = true;
        setLoadingPrograms(true);
        setError(null);

        fetchPrograms({ catalogLabel: selectedCatalogLabel })
            .then((response) => {
                if (!active) return;
                setPrograms(response.items ?? []);
            })
            .catch((err) => {
                if (!active) return;
                setError(err instanceof Error ? err.message : "Failed to load programs");
            })
            .finally(() => {
                if (!active) return;
                setLoadingPrograms(false);
            });

        return () => {
            active = false;
        };
    }, [selectedCatalogLabel]);

    const filtered = useMemo(() => {
        const query = search.trim().toLowerCase();
        if (!query) return programs;
        return programs.filter((program) =>
            formatProgramLabel(program).toLowerCase().includes(query)
        );
    }, [programs, search]);

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-lg">
                <DialogHeader>
                    <DialogTitle className="text-lg font-semibold text-brandPrimary">
                        Add Degree Program
                    </DialogTitle>
                </DialogHeader>

                <div className="space-y-3">
                    <div>
                        <label className="text-xs uppercase tracking-widest text-gray-500">
                            Bulletin Year
                        </label>
                        <Select
                            value={selectedCatalogLabel}
                            onValueChange={setSelectedCatalogLabel}
                            disabled={loadingBulletins}
                        >
                            <SelectTrigger>
                                <SelectValue placeholder="Select bulletin" />
                            </SelectTrigger>
                            <SelectContent>
                                {bulletins.map((bulletin) => (
                                    <SelectItem
                                        key={bulletin.id}
                                        value={bulletin.catalogLabel ?? String(bulletin.catalogYear)}
                                    >
                                        {bulletin.catalogLabel ?? bulletin.catalogYear}
                                        {bulletin.isCurrent ? " (Current)" : ""}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>

                    <Input
                        placeholder="Search degree programs..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                    />

                    {error ? (
                        <p className="text-sm text-red-600">{error}</p>
                    ) : null}
                </div>

                <div className="max-h-72 overflow-y-auto space-y-3">
                    {loadingPrograms ? (
                        <p className="text-sm text-gray-500">Loading programs...</p>
                    ) : filtered.length === 0 ? (
                        <p className="text-gray-500 text-center text-sm">
                            No programs found.
                        </p>
                    ) : (
                        filtered.map((program) => (
                            <div
                                key={program.id}
                                className="border rounded-md p-3 flex flex-col gap-2 bg-white shadow-sm"
                            >
                                <div className="flex justify-between items-start">
                                    <div>
                                        <p className="font-medium">
                                            {formatProgramLabel(program)}
                                        </p>
                                        <p className="text-sm text-gray-500">
                                            {program.college ?? "College not listed"}
                                        </p>
                                    </div>
                                </div>

                                <Button
                                    size="sm"
                                    disabled={!selectedCatalogLabel || loadingPrograms}
                                    className="bg-brandPrimary hover:bg-red-700 text-white w-full"
                                    onClick={() => {
                                        onAdd({
                                            id: String(program.id),
                                            name: formatProgramLabel(program),
                                            catalogYear: selectedCatalogLabel,
                                            programType: program.programType,
                                            degreeType: program.degreeType,
                                            college: program.college,
                                            catalogVersionId: program.catalogVersionId ?? undefined,
                                        });
                                        setSearch("");
                                        onOpenChange(false);
                                    }}
                                >
                                    Add Program ({selectedCatalogLabel || "Select Bulletin"})
                                </Button>
                            </div>
                        ))
                    )}
                </div>
            </DialogContent>
        </Dialog>
    );
}
