import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// https://vite.dev/config/
export default defineConfig({
  base: "/tennis/",
  plugins: [react()],
  server: {
    proxy: {
      "/tennis/api": {
        target: process.env.API_PROXY_TARGET ?? "http://127.0.0.1:8081",
        changeOrigin: true,
      },
    },
  },
});
