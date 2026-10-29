import { useEffect, useState, type ReactNode } from "react";
import { apiGet } from "@/api/client";
import { AuthContext, type AuthUser } from "./authState";

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        apiGet<AuthUser>("/api/auth/me")
            .then(setUser)
            .catch(() => setUser(null))
            .finally(() => setLoading(false));
    }, []);

    const login = () => {
        window.location.href = "/api/auth/login";
    };

    const logout = () => {
        const form = document.createElement("form");
        form.method = "POST";
        form.action = "/api/auth/logout";
        document.body.appendChild(form);
        form.submit();
    };

    return (
        <AuthContext.Provider value={{ user, loading, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
}
