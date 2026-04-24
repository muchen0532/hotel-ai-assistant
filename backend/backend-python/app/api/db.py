from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker
from contextlib import asynccontextmanager
from functools import lru_cache
from typing import AsyncIterator

from app.config import get_settings


# 全局连接池
@lru_cache
def get_engine():
    return create_async_engine(
        get_settings().database_url,
        pool_pre_ping=True
    )


@lru_cache
def get_session_factory():
    return async_sessionmaker(
        get_engine(),
        expire_on_commit=False
    )


async def get_session_dep() -> AsyncIterator[AsyncSession]:
    async with get_session_factory()() as session:
        yield session


@asynccontextmanager
async def get_db_session():
    async with get_session_factory()() as session:
        yield session
