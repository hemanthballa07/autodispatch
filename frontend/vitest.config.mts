import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    // globals exposes afterEach, which React Testing Library's auto-cleanup hooks into.
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    include: ["components/**/*.test.tsx"],
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname),
    },
  },
});
