from __future__ import annotations

import logging
from typing import Any

from qdrant_client import AsyncQdrantClient
from qdrant_client.http.models import Filter, FieldCondition, MatchValue

from app.tools.embedding_tools import embed

log = logging.getLogger(__name__)


async def search_names(
    client: AsyncQdrantClient,
    hotel_id: str,
    query: str,
    doc_type: str,
    top_k: int,
    score_cutoff: float | None = None,
) -> list[str]:
    payloads = await search_payloads(
        client,
        hotel_id,
        query,
        doc_type,
        top_k,
        score_cutoff
    )
    return [p.get("name") for p in payloads if p.get("name")]


async def search_payloads(
        client: AsyncQdrantClient,
        hotel_id: str,
        query: str,
        doc_type: str,
        top_k: int,
        score_cutoff: float | None = None,
) -> list[dict[str, Any]]:
    collection = f"{hotel_id}_knowledge"

    try:
        vector = await embed(query)

        results = await client.query_points(
            collection_name=collection,
            query=vector,
            limit=top_k,
            score_threshold=score_cutoff,
            with_payload=True,
            query_filter=Filter(
                must=[
                    FieldCondition(key="hotel_id", match=MatchValue(value=hotel_id)),
                    FieldCondition(key="doc_type", match=MatchValue(value=doc_type)),
                ]
            ),
        )

        return [dict(p.payload) for p in results.points]

    except Exception as e:
        log.warning("[Qdrant] Search failed: %s", e)
        return []
