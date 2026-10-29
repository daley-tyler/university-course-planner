import { Link, useLocation } from "react-router-dom";
import { LogIn, LogOut, GraduationCap, User } from "lucide-react";
import { useAuth } from "@/context/useAuth";

export default function Navbar() {
    const { pathname } = useLocation();
    const { user, login, logout } = useAuth();
    const navItems = [
        { name: "Home", path: "/", active: pathname === "/" },
        { name: "Plan", path: "/plan/requirements", active: pathname.startsWith("/plan") },
    ];

    return (
        <header className="sticky top-0 z-50 border-b-2 border-brandGold bg-brandPrimary text-white shadow-sm">
            <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-6 py-4">
                <Link to="/" aria-label="University Course Planner" className="flex items-center gap-3 transition-opacity hover:opacity-90">
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-sm border border-brandGold/60 text-brandGold">
                        <GraduationCap className="h-6 w-6" strokeWidth={1.5} aria-hidden="true" />
                    </span>
                    <span>
                        <span className="block text-[10px] font-medium uppercase leading-4 tracking-[0.2em] text-brandGold">University</span>
                        <span className="block font-display text-lg leading-6 sm:text-xl">Course Planner</span>
                    </span>
                </Link>
                <div className="flex items-center gap-8">
                    <nav aria-label="Main navigation" className="hidden items-center gap-7 md:flex">
                        {navItems.map(({ name, path, active }) => (
                            <Link key={path} to={path} aria-current={active ? "page" : undefined}
                                className={`border-b-2 py-2 text-sm font-medium transition ${active ? "border-brandGold text-white" : "border-transparent text-white/80 hover:border-brandGold/60 hover:text-white"}`}>
                                {name}
                            </Link>
                        ))}
                    </nav>
                    <div className="flex items-center gap-4">
                        {user && <span className="hidden items-center gap-1.5 text-xs text-white/75 lg:flex"><User className="h-3.5 w-3.5" aria-hidden="true" />{user.displayName}</span>}
                        <button type="button" onClick={user ? logout : login} aria-label={user ? "Sign Out" : "Sign In"}
                            className="flex items-center gap-2 rounded-sm border border-white/40 px-3 py-2 text-xs font-medium text-white transition hover:border-brandGold hover:bg-white/10">
                            {user ? <LogOut className="h-4 w-4" aria-hidden="true" /> : <LogIn className="h-4 w-4" aria-hidden="true" />}
                            <span className="hidden min-[400px]:inline">{user ? "Sign Out" : "Sign In"}</span>
                        </button>
                    </div>
                </div>
            </div>
            <nav aria-label="Mobile navigation" className="flex justify-center gap-8 border-t border-white/15 md:hidden">
                {navItems.map(({ name, path, active }) => (
                    <Link key={path} to={path} aria-current={active ? "page" : undefined}
                        className={`border-b-2 px-2 py-2 text-xs font-medium ${active ? "border-brandGold text-white" : "border-transparent text-white/75 hover:text-white"}`}>
                        {name}
                    </Link>
                ))}
            </nav>
        </header>
    );
}
