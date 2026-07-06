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
          DEFAULT: "#1f2328",
          soft: "#57606a",
          faint: "#8c959f",
        },
        line: "#d1d9e0",
        surface: "#ffffff",
        canvas: "#f6f8fa",
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
