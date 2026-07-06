import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
// Dev server proxies /api to the Java backend (run `RegionWebServer` on :8080).
// `npm run build` emits static assets into ../src/main/resources/webui/ so the
// Java server can serve the SPA in production without a Node runtime.
export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        proxy: {
            "/api": "http://localhost:8080",
        },
    },
    build: {
        outDir: "dist",
        emptyOutDir: true,
    },
});
