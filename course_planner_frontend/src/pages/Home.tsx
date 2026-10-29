import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useReducedMotion } from "framer-motion";
import { ArrowRight, CalendarCheck2, Search, GraduationCap } from "lucide-react";

export default function Home() {
    const video = useRef<HTMLVideoElement>(null);
    const reduceMotion = useReducedMotion();
    const [videoUnavailable, setVideoUnavailable] = useState(false);

    useEffect(() => {
        const element = video.current;
        if (!element || reduceMotion || videoUnavailable) return;

        element.defaultMuted = true;
        element.muted = true;
        const startVideo = () => {
            if (document.visibilityState !== "visible") return;
            void element.play().catch(() => undefined);
        };
        element.addEventListener("canplay", startVideo);
        document.addEventListener("visibilitychange", startVideo);
        startVideo();
        return () => {
            element.removeEventListener("canplay", startVideo);
            document.removeEventListener("visibilitychange", startVideo);
        };
    }, [reduceMotion, videoUnavailable]);
    const features = [
        { title: "Explore the catalog", desc: "Find courses by subject, level and credits. See descriptions and prerequisites before adding them to your plan.", icon: Search, path: "/plan/catalog", link: "Browse courses" },
        { title: "Follow your requirements", desc: "Explore program requirements and compare your course choices against a checklist for your catalog year.", icon: GraduationCap, path: "/plan/requirements", link: "View requirements" },
        { title: "Shape your semesters", desc: "Build a schedule, check prerequisite order and compare what-if plans as your ideas take shape.", icon: CalendarCheck2, path: "/plan/planner", link: "Start planning" },
    ];

    return (
        <div>
            <section className="campus-hero relative flex min-h-[540px] items-center overflow-hidden text-white md:min-h-[580px]">
                <div className="campus-poster" aria-hidden="true" />
                {!reduceMotion && !videoUnavailable && (
                    <video
                        ref={video}
                        autoPlay
                        loop
                        muted
                        playsInline
                        aria-hidden="true"
                        poster="/videos/campus-background.jpg"
                        src="/videos/campus-background.mp4"
                        className="campus-video"
                        onError={() => setVideoUnavailable(true)}
                    />
                )}
                <div className="mx-auto w-full max-w-7xl px-6 py-20 md:py-24">
                    <p className="mb-6 flex items-center gap-3 text-xs font-semibold uppercase tracking-[0.22em] text-brandGold">
                        <span className="h-px w-10 bg-brandGold" aria-hidden="true" />
                        Academic planning
                    </p>
                    <h1 className="max-w-3xl font-display text-4xl leading-[1.12] tracking-tight sm:text-5xl md:text-6xl">
                        Plan your path<br className="hidden sm:block" /> through university.
                    </h1>
                    <p className="mt-6 max-w-xl text-base leading-relaxed text-white/90 md:text-lg">
                        Explore courses, follow program requirements, and build a study plan that works for you.
                    </p>
                    <div className="mt-9 flex flex-wrap gap-3">
                        <Link to="/plan/planner" className="inline-flex items-center gap-3 rounded-sm bg-brandGold px-6 py-3 text-sm font-semibold text-brandPrimary transition hover:bg-[#edd6a8]">
                            Open Planner <ArrowRight className="h-4 w-4" aria-hidden="true" />
                        </Link>
                        <Link to="/plan/catalog" className="inline-flex items-center rounded-sm border border-white/60 px-6 py-3 text-sm font-semibold text-white transition hover:bg-white/10">
                            Explore Courses
                        </Link>
                    </div>
                </div>
            </section>

            <section className="border-b border-brandBorder bg-brandSurface">
                <div className="mx-auto max-w-7xl px-6 py-12 md:py-16">
                    <div className="mb-9 flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                        <h2 className="font-display text-2xl text-brandPrimary md:text-3xl">Bring your plan into focus.</h2>
                        <p className="text-sm text-gray-600">From your first course choice to your next semester.</p>
                    </div>
                    <div className="grid gap-8 md:grid-cols-3 md:gap-0">
                        {features.map((feature, index) => (
                            <article key={feature.title} className="border-t border-brandBorder pt-6 md:border-t-0 md:border-l md:px-8 md:pt-0 md:first:border-l-0 md:first:pl-0 md:last:pr-0">
                                <div className="mb-4 flex items-center justify-between">
                                    <feature.icon className="h-6 w-6 text-brandAccent" strokeWidth={1.5} aria-hidden="true" />
                                    <span className="font-display text-sm text-brandAccent">0{index + 1}</span>
                                </div>
                                <h3 className="mb-3 font-display text-xl text-brandPrimary">{feature.title}</h3>
                                <p className="text-sm leading-7 text-gray-600">{feature.desc}</p>
                                <Link to={feature.path} className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-brandPrimary underline decoration-brandGold underline-offset-4 hover:decoration-brandPrimary">
                                    {feature.link} <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                                </Link>
                            </article>
                        ))}
                    </div>
                </div>
            </section>
            <div className="mx-auto flex max-w-7xl flex-col gap-2 px-6 py-5 text-xs leading-relaxed text-gray-600 sm:flex-row sm:justify-between">
                <p>Developed as a university senior design team project.</p>
                <a href="https://www.pexels.com/video/aerial-view-of-iconic-university-campus-31033176/" target="_blank" rel="noreferrer" className="underline underline-offset-2 hover:text-brandPrimary">Campus footage by David Pickup / Pexels</a>
            </div>
        </div>
    );
}
