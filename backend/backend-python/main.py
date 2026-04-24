import logging
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.deps import init_graph, cleanup_graph
from app.api.routes import router
from app.config import Settings

logging.basicConfig(level=logging.DEBUG, format="%(asctime)s %(levelname)s %(name)s : %(message)s")
logging.getLogger("asyncio").setLevel(logging.WARNING)
log = logging.getLogger(__name__)

settings = Settings()


@asynccontextmanager
async def lifespan(_app: FastAPI):
    await init_graph()
    yield
    await cleanup_graph()

app = FastAPI(
    title="Hotel AI — LangGraph Backend",
    description="Python LangGraph 版本，与 backend-java 并行运行，通过 feature flag 切换",
    version="0.1.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router)

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=settings.port, reload=True)
