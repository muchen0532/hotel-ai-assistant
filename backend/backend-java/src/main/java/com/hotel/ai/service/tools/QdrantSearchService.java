package com.hotel.ai.service.tools;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.*;
import io.qdrant.client.grpc.Points.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static io.qdrant.client.ConditionFactory.matchKeyword;

/**
 * Qdrant semantic search — one collection per hotel.
 *
 * Each point in Qdrant has payload:
 *   { hotel_id, doc_type, name, ... }
 *
 * We always filter by hotel_id + doc_type to keep results hotel-scoped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QdrantSearchService {

    private final QdrantClient qdrantClient;
    private final EmbeddingModel embeddingModel;

    @Value("${agent.rag.top-k:5}")
    private int defaultTopK;

    @Value("${agent.rag.score-cutoff:0.35}")
    private float scoreCutoff;


    public List<String> searchRestaurantNames(String hotelId, String query, int topK) {
        return searchNames(hotelId, query, "restaurant", topK);
    }

    public List<String> searchAttractionNames(String hotelId, String query, int topK) {
        return searchNames(hotelId, query, "attraction", topK);
    }

    public List<Map<String, Object>> searchFaq(String hotelId, String query, int topK) {
        return searchPayloads(hotelId, query, "faq", topK);
    }


    private List<String> searchNames(String hotelId, String query, String docType, int topK) {
        return searchPayloads(hotelId, query, docType, topK).stream()
                .map(p -> (String) p.getOrDefault("name", ""))
                .filter(name -> !name.isBlank())
                .toList();
    }

    private List<Map<String, Object>> searchPayloads(String hotelId, String query,
                                                      String docType, int topK) {
        String collection = hotelId + "_knowledge";

        try {
            // Embed the query
            Embedding embedding = embeddingModel.embed(query).content();
            log.info("[Qdrant] collection={}, vector dim={}", collection, embedding.vector().length);
            List<Float> vector  = toFloatList(embedding.vector());

            // Build filter: hotel_id AND doc_type
            Filter filter = Filter.newBuilder()
                    .addMust(matchKeyword("hotel_id", hotelId))
                    .addMust(matchKeyword("doc_type", docType))
                    .build();


            // Search
            List<ScoredPoint> results = qdrantClient
                    .searchAsync(
                            SearchPoints.newBuilder()
                                    .setCollectionName(collection)
                                    .addAllVector(vector)
                                    .setFilter(filter)
                                    .setLimit(topK)
                                    //.setScoreThreshold(scoreCutoff)
                                    .setWithPayload(WithPayloadSelector.newBuilder()
                                            .setEnable(true).build())
                                    .build()
                    )
                    .get();

            log.debug("[Qdrant] hotel={} docType={} query='{}' hits={}",
                    hotelId, docType, query, results.size());

            return results.stream()
                    .map(p -> convertPayload(p.getPayloadMap()))
                    .toList();

        } catch (Exception e) {
            log.warn("[Qdrant] Search failed for collection={}: {}", collection, e.getMessage());
            return List.of();
        }
    }


    private List<Float> toFloatList(float[] floats) {
        var list = new java.util.ArrayList<Float>(floats.length);
        for (float f : floats) list.add(f);
        return list;
    }

    private Map<String, Object> convertPayload(
            Map<String, io.qdrant.client.grpc.JsonWithInt.Value> raw) {
        var result = new java.util.HashMap<String, Object>();
        raw.forEach((k, v) -> {
            if (v.hasStringValue())  result.put(k, v.getStringValue());
            else if (v.hasDoubleValue()) result.put(k, v.getDoubleValue());
            else if (v.hasIntegerValue()) result.put(k, v.getIntegerValue());
            else if (v.hasBoolValue()) result.put(k, v.getBoolValue());
        });
        return result;
    }
}
