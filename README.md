# hotel-ai-assistant

An AI-powered hotel service agent system with RAG, tool routing, and conversational UI.

面向酒店场景的 AI 智能服务系统，支持 RAG 检索增强、工具调用编排与对话式交互。

---

## 🌟 Project Overview / 项目概览

Hotel AI Assistant is an AI dialogue system designed for hotel service scenarios.
It supports users in obtaining restaurant recommendations, attraction information, frequently asked questions, room service, and concierge services through natural language.

System Integration:
- Enhanced generation of RAG retrieval based on Qdrant
- Intent recognition and tool routing (FAQ / facilities / restaurants / attractions / guest room control / delivery / concierge)
- Two interchangeable backends sharing the same frontend; one can be activated as needed
- Rich interactive frontend using Vue 3 + TypeScript

---

Hotel AI Assistant 是面向酒店服务场景的 AI 对话系统。  
支持用户通过自然语言获取餐厅推荐、景点信息、常见问题、客房服务及礼宾服务等能力。

系统融合：
- 基于 Qdrant 的 RAG 检索增强生成
- 意图识别与工具路由（FAQ / 设施 / 餐厅 / 景点 / 客控 / 送物 / 礼宾）
- 两套可互换后端，共用同一前端，按需启动其中一套
- Vue 3 + TypeScript 富交互前端

---

## 🛠️ Tech Stack / 技术栈

### Frontend / 前端
- Vue 3 + TypeScript + Vite

### Backend A — Java / 后端 A（LangChain4j）
- Spring Boot 3.3.5 · Java 21
- Spring Data JPA · PostgreSQL · Lombok
- LangChain4j（AI 编排层）
- Qdrant（向量数据库 · RAG）
- Jina Embeddings v3

### Backend B — Python / 后端 B（LangGraph）
- Python 3.11+
- FastAPI · uvicorn
- LangGraph 0.2+（StateGraph · conditional edges · human-in-the-loop · checkpointer）
- LangChain OpenAI-compatible（DeepSeek）
- SQLAlchemy async · asyncpg
- Qdrant async client

---

## 🔀 Dual Backend / 双后端架构

Both backends listen on the same port (`:8080`) and have identical interface paths. Only one of them needs to be started, and no changes are required to the frontend.
两套后端监听同一端口（`:8080`），接口路径完全一致，按需启动其中一套即可，前端无需任何改动。

| | backend-java | backend-python |
|---|---|---|
| 框架 | Spring Boot + LangChain4j | FastAPI + LangGraph |
| 流式 SSE | ✅ | ✅ |
| Human-in-the-loop | ❌ | ✅ |
| 对话持久化 | 自建 chat_messages 表 | LangGraph Checkpointer |



---

## ⚙️ Configuration / 配置

### Java（`application.yml`）
```yaml
spring.datasource.url: jdbc:postgresql://localhost:5432/hotel_ai
qdrant.host: localhost
qdrant.port: 6334
qdrant.score-cutoff: 0.3
```

### Python（`.env`）
```env
DEEPSEEK_API_KEY=your_key
DATABASE_URL=postgresql+asyncpg://user:password@localhost:5432/hotel_ai
QDRANT_HOST=localhost
QDRANT_PORT=6334
QDRANT_SCORE_CUTOFF=0.3
LANGGRAPH_CHECKPOINTER_URL=postgresql://user:password@localhost:5432/hotel_ai
PORT=8080
```

---

## 🤖 Agent Pipeline / 智能体流程

```
用户输入
   │
   ▼
Node 1: Intent Classification（意图识别）
   规则匹配 → LLM fallback
   │
   ├─ 操作类（客控 / 送物 / 礼宾）
   │      │
   │      ▼  [仅 Python 版]
   │   Human-in-the-loop（interrupt 挂起 · 等待确认）
   │      │
   ▼      ▼
Node 2: Tool Execution（工具执行）
   FAQ → PostgreSQL
   设施 → PostgreSQL
   餐厅 → Qdrant + PostgreSQL + Rerank
   景点 → Qdrant RAG + PostgreSQL + Rerank
   客控 / 送物 / 礼宾 → API（对接）
   │
   ▼
Node 3: Response Generation（回复生成）
   操作类 → 模板回复（确认 + 预计时间）
   查询类 → LLM 结合 tool_results 生成
```

### Supported Intents / 支持的意图

| Intent | 描述 | 示例 |
|---|---|---|
| `faq` | 基础信息 | WiFi 密码、早餐时间、退房时间 |
| `facilities` | 设施查询 | 泳池、健身房、SPA |
| `restaurant` | 餐厅推荐 | 浪漫晚餐、家庭聚餐 |
| `attractions` | 景点推荐 | 文化景点、自然公园 |
| `room_control` | 客房控制 | 空调、灯光、窗帘、勿扰 |
| `delivery` | 客房送物 | 毛巾、矿泉水、牙刷、送餐 |
| `concierge` | 礼宾服务 | 叫醒、叫车、行李寄存、门票 |
| `general` | 通用对话 | 其他问题，LLM 自由回答 |

---

## 📸 Screenshots / 项目截图

**Index / 首页**  
![Index](docs/screenshot/index.png)

**Room Control / 房间控制**  
![Room Control](docs/screenshot/room_control.png)

**Restaurant / 餐厅推荐**  
![Restaurant](docs/screenshot/restaurant.png)  
![Restaurant Romantic](docs/screenshot/restaurant-romantic.png)

**Attractions / 景点推荐**  
![Attractions](docs/screenshot/attractions.png)

**Facilities / 酒店设施**  
![Facilities](docs/screenshot/facilities.png)

**Delivery / 客房服务**  
![Delivery](docs/screenshot/delivery.png)  
![Extra Bed](docs/screenshot/delivery-extra_bed.png)

**Concierge Wakeup / 礼宾叫醒**  
![Concierge Wakeup](docs/screenshot/concierge-wakeup.png)

**FAQ WiFi / 常见问题-网络**  
![FAQ WiFi](docs/screenshot/faq-wifi.png)

**Human-in-the-loop (HITL) / 人工审批流程**  
![Human in the loop](docs/screenshot/human-in.png)
