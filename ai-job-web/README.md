# AI Job Web

Vue3 + TypeScript + Vite 前端，对接 `ai-job-server`。

## 启动

```bash
cd ai-job-web
npm install
npm run dev
```

浏览器打开：http://localhost:5173

请同时启动：

- Java：`http://localhost:8080`
- Python（AI 分析时需要）：`http://localhost:8001`

开发环境通过 Vite 代理 `/api` → `http://localhost:8080`，无需改后端 CORS。

## 账号

使用后端已有账号登录，例如：`myq` / `123456`
