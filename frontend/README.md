# Hotel Guest AI Assistant — Frontend

Vue 3 + TypeScript frontend for the Hotel AI Assistant system.


---

## Quick start

### 1. Install dependencies

```bash
npm install
```

### 2a. Run with mock backend (no Go/Python needed)

Open two terminals:

```bash
# Terminal 1 — mock SSE server (Node 18+)
node mock-server.mjs

# Terminal 2 — Vite dev server
npm run dev
```

Open http://localhost:5173

### 2b. Run against real Go API Gateway

```bash
cp .env.example .env.local
# Edit VITE_API_BASE if Go runs on a different port
npm run dev
```

Vite proxies `/api/*` → `http://localhost:8080` automatically.

---


