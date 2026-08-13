/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Minimal scaffold for the fallain frontend (Vite + React + TS).
// React plugin is registered now so App.tsx/main.tsx can be dropped in
// without touching this file in a later round.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "node",
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
    globals: false,
  },
});
