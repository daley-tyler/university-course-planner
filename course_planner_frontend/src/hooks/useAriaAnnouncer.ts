import { useCallback, useState } from "react";

export function useAriaAnnouncer() {
    const [message, setMessage] = useState("");

    const announce = useCallback((nextMessage: string) => {
        setMessage("");
        if (typeof window === "undefined") {
            setMessage(nextMessage);
            return;
        }
        window.requestAnimationFrame(() => {
            setMessage(nextMessage);
        });
    }, []);

    return { message, announce };
}
