import { useNavigate, useSearchParams } from "react-router-dom";
import { useEffect } from "react";
import { motion, type Variants } from "framer-motion";
import { useAuth } from "@/context/useAuth";

const pageVariants: Variants = {
    hidden: { opacity: 0, y: 12 },
    show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: "easeOut" } },
};

export default function Login() {
    const { user, loading, login } = useAuth();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const error = searchParams.get("error");

    // If already authenticated, redirect to the planner
    useEffect(() => {
        if (!loading && user) {
            navigate("/plan/requirements", { replace: true });
        }
    }, [user, loading, navigate]);

    return (
        <motion.div
            className="flex justify-center items-center min-h-[80vh] px-4"
            variants={pageVariants}
            initial="hidden"
            animate="show"
        >
            <motion.div
                className="w-full max-w-md bg-white/90 border border-gray-200 rounded-lg shadow-lg p-8"
                whileHover={{ y: -2 }}
            >
                <h2 className="text-2xl font-bold text-center text-brandPrimary mb-2 font-display">
                    Sign In
                </h2>
                <p className="text-center text-gray-500 text-sm mb-6">
                    Use a configured institutional account.
                </p>

                {error === "auth_failed" && (
                    <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded text-red-700 text-sm">
                        Authentication failed. Please try again.
                    </div>
                )}

                <button
                    onClick={login}
                    className="w-full bg-brandPrimary text-white font-semibold py-2 rounded hover:bg-blue-700 transition"
                >
                    Sign in with CAS
                </button>
            </motion.div>
        </motion.div>
    );
}
