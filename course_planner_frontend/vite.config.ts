import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": "/src",
    },
  },
  server: {
    proxy: {
      "/api": {
        target: process.env.COURSE_PLANNER_API_URL ?? "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    },
  },
});
