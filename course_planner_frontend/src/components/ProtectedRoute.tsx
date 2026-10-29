import { Navigate } from "react-router-dom";
import { type ReactNode } from "react";
import { useAuth } from "@/context/useAuth";

export default function ProtectedRoute({ children }: { children: ReactNode }) {
    const { user, loading } = useAuth();

    if (loading) {
        return (
            <div className="flex justify-center items-center min-h-[80vh]">
                <span className="text-gray-500 text-sm">Loading...</span>
            </div>
        );
    }

    if (!user) {
        return <Navigate to="/login" replace />;
    }

    return <>{children}</>;
}
