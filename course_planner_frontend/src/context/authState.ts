import { createContext } from "react";

export interface AuthUser {
    id: number;
    casId: string;
    email: string | null;
    displayName: string;
}

export interface AuthContextValue {
    user: AuthUser | null;
    loading: boolean;
    login: () => void;
    logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
