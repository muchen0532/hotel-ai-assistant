from __future__ import annotations

from typing import Any, Optional
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

_AMBIANCE_DB_MAP = {
    "romantic": ["浪漫", "约会", "情侣"],
    "family": ["家庭", "亲子"],
    "business": ["商务", "聚会"],
    "quiet": ["安静", "私密"],
    "lively": ["热闹"],
    "casual": ["休闲"],
}


async def faq_find_by_category(db: AsyncSession, hotel_id: str, category: str) -> list[dict]:
    result = await db.execute(
        text("SELECT * FROM faqs WHERE hotel_id=:h AND category=:c AND is_active=true ORDER BY sort_order"),
        {"h": hotel_id, "c": category}
    )
    return [dict(r) for r in result.mappings().all()]


async def faq_find_all(db: AsyncSession, hotel_id: str) -> list[dict]:
    result = await db.execute(
        text("SELECT * FROM faqs WHERE hotel_id=:h AND is_active=true ORDER BY sort_order"),
        {"h": hotel_id}
    )
    return [dict(r) for r in result.mappings().all()]


async def facility_find(db: AsyncSession, hotel_id: str, category: Optional[str]) -> list[dict]:
    if category:
        result = await db.execute(
            text("SELECT * FROM facilities WHERE hotel_id=:h AND category=:c AND is_active=true"),
            {"h": hotel_id, "c": category}
        )
    else:
        result = await db.execute(
            text("SELECT * FROM facilities WHERE hotel_id=:h AND is_active=true"),
            {"h": hotel_id}
        )
    return [dict(r) for r in result.mappings().all()]


async def restaurant_find_filtered(
        db: AsyncSession,
        hotel_id: str,
        min_rating: float,
        max_price: Optional[int],
        ambiance: Optional[list[str]],
        limit: int,
) -> list[dict]:
    sql = """
        SELECT * FROM restaurants
        WHERE hotel_id = :h
          AND is_active = true
          AND rating >= :r
          {price_filter}
          {ambiance_filter}
        ORDER BY rating DESC
        FETCH FIRST :lim ROWS ONLY
    """
    params: dict[str, Any] = {"h": hotel_id, "r": min_rating, "lim": limit}

    price_filter = "AND price_level <= :p" if max_price else ""
    ambiance_filter = ""
    if ambiance:
        expanded = []
        for a in ambiance:
            expanded.extend(_AMBIANCE_DB_MAP.get(a, []))
        ambiance_filter = "AND ambiance && :a"
        params["a"] = expanded
    if max_price:
        params["p"] = max_price

    result = await db.execute(
        text(sql.format(price_filter=price_filter, ambiance_filter=ambiance_filter)),
        params
    )
    return [dict(r) for r in result.mappings().all()]


async def attraction_find(
        db: AsyncSession,
        hotel_id: str,
        category: Optional[str],
        min_rating: float,
        limit: int,
) -> list[dict]:
    if category:
        result = await db.execute(
            text("""
                SELECT * FROM attractions
                WHERE hotel_id=:h AND category=:c AND is_active=true AND rating>=:r
                ORDER BY rating DESC FETCH FIRST :lim ROWS ONLY
            """),
            {"h": hotel_id, "c": category, "r": min_rating, "lim": limit}
        )
    else:
        result = await db.execute(
            text("""
                SELECT * FROM attractions
                WHERE hotel_id=:h AND is_active=true AND rating>=:r
                ORDER BY rating DESC FETCH FIRST :lim ROWS ONLY
            """),
            {"h": hotel_id, "r": min_rating, "lim": limit}
        )
    return [dict(r) for r in result.mappings().all()]


async def attraction_find_by_names(
        db: AsyncSession, hotel_id: str, names: list[str]
) -> list[dict]:
    result = await db.execute(
        text("SELECT * FROM attractions WHERE hotel_id=:h AND name = ANY(:names) AND is_active=true"),
        {"h": hotel_id, "names": names}
    )
    return [dict(r) for r in result.mappings().all()]
