/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Minimal scaffold for the fallain frontend (Vite + React + TS).
// React plugin is registered now so App.tsx/main.tsx can be dropped in
// without touching this file in a later round.
export default defineConfig({
  plugins: [react()],
  server: {
    // 백엔드(Spring Boot, :8080)와 프론트(Vite, :5173)가 별도 오리진으로 뜨고
    // 백엔드에는 CORS 설정이 없으므로, 개발 서버에서만 /api, /ws를 동일 오리진처럼
    // 프록시한다. src/api/client.ts와 src/api/ws.ts는 상대 경로("")를 기본값으로
    // 쓰기 때문에 이 프록시를 그대로 탄다. 운영 배포는 별도 origin 구성에 맞춰
    // VITE_API_BASE_URL로 오버라이드하면 된다.
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/ws": {
        target: "http://localhost:8080",
        changeOrigin: true,
        ws: true,
        // Spring의 STOMP 핸드셰이크(WebSocketConfig)는 Origin 허용 목록을 별도로
        // 열어두지 않아 브라우저가 보낸 Origin(:5173/:5183 등)을 그대로 넘기면
        // 403이 난다. 프록시가 백엔드로 보내는 요청의 Origin을 백엔드 자기 자신의
        // origin으로 바꿔치기해서 same-origin처럼 보이게 한다 (백엔드 코드는 건드리지
        // 않는다).
        configure: (proxy) => {
          proxy.on("proxyReqWs", (proxyReq) => {
            proxyReq.setHeader("origin", "http://localhost:8080");
          });
        },
      },
    },
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
    globals: false,
  },
});
