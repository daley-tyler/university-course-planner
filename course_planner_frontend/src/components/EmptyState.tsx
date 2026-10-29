import { type ReactNode } from "react";
import { cn } from "@/lib/utils";
import { motion } from "framer-motion";

type EmptyStateProps = {
    title: string;
    description?: string;
    action?: ReactNode;
    className?: string;
};

export function EmptyState({ title, description, action, className }: EmptyStateProps) {
    return (
        <motion.div
            className={cn(
                "flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-gray-200 bg-white/80 px-4 py-6 text-center text-sm text-gray-600",
                className
            )}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25, ease: "easeOut" }}
        >
            <p className="font-semibold text-gray-900">{title}</p>
            {description ? <p className="max-w-sm text-gray-600">{description}</p> : null}
            {action ? <div className="pt-2">{action}</div> : null}
        </motion.div>
    );
}
