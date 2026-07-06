/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        mono: ["ui-monospace", "SFMono-Regular", "Menlo", "Consolas", "monospace"],
        sans: ["Inter", "ui-sans-serif", "system-ui", "-apple-system", "Segoe UI", "sans-serif"],
      },
      colors: {
        ink: {
          DEFAULT: "#1e2230",
          soft: "#5b6472",
          faint: "#93a0b4",
        },
        line: "#e3e6ef",
        surface: "#ffffff",
        canvas: "#f5f6fc",
        panel: "#eef1f9",
        gutter: "#f3f4fb",
        accent: { DEFAULT: "#6d5efc", ink: "#4b3fd6", soft: "#eeecff" },
        cyan: { DEFAULT: "#06b6d4" },
        // Clone family accents (light dev-tool palette).
        t1: { DEFAULT: "#1a7f37", soft: "#dafbe1" },
        t2: { DEFAULT: "#0969da", soft: "#ddf4ff" },
        t3: { DEFAULT: "#9a6700", soft: "#fff8c5" },
        t4: { DEFAULT: "#8250df", soft: "#fbefff" },
      },
    },
  },
  plugins: [],
};
