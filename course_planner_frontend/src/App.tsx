import { Routes, Route, Navigate } from "react-router-dom";
import { Toaster } from "sonner";
import Navbar from "@/components/Navbar";
import ProtectedRoute from "@/components/ProtectedRoute";
import Home from "@/pages/Home";
import Login from "@/pages/Login";
import { PlanLayout } from "@/layouts/PlanLayout";
import { RequirementsTab } from "@/pages/plan/RequirementsTab";
import { CatalogTab } from "@/pages/plan/CatalogTab";
import { PlannerTab } from "@/pages/plan/PlannerTab";
import { AuditTab } from "@/pages/plan/AuditTab";

export default function App() {
    return (
        <div className="flex flex-col min-h-screen">
            <Navbar />
            <main className="flex-grow">
                <Routes>
                    <Route path="/" element={<Home />} />
                    <Route path="/login" element={<Login />} />
                    <Route
                        path="/plan"
                        element={
                            <ProtectedRoute>
                                <PlanLayout />
                            </ProtectedRoute>
                        }
                    >
                        <Route index element={<Navigate to="/plan/requirements" replace />} />
                        <Route path="requirements" element={<RequirementsTab />} />
                        <Route path="catalog" element={<CatalogTab />} />
                        <Route path="planner" element={<PlannerTab />} />
                        <Route path="audit" element={<AuditTab />} />
                    </Route>
                    <Route path="/planner" element={<Navigate to="/plan/planner" replace />} />
                    <Route path="/search" element={<Navigate to="/plan/catalog" replace />} />
                    <Route path="*" element={<Navigate to="/" />} />
                </Routes>
            </main>

            <Toaster position="bottom-left" richColors closeButton />

            <footer className="border-t border-brandBorder bg-brandWash py-5 text-center text-xs text-gray-600">
                © {new Date().getFullYear()} University Course Planner
            </footer>
        </div>
    );
}
