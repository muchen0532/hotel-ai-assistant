package com.hotel.ai.service.tools;

import com.hotel.ai.model.entity.*;
import com.hotel.ai.repository.*;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.*;
import io.qdrant.client.grpc.Points.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.*;
import static io.qdrant.client.VectorsFactory.vectors;

/**
 * Run with:  java -jar hotel-ai.jar --spring.profiles.active=seed
 *
 * Seeds Qdrant with embeddings for all active hotels:
 *   - FAQs
 *   - Restaurants
 *   - Attractions
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
@Slf4j
public class QdrantSeeder implements CommandLineRunner {

    private static final int    VECTOR_SIZE = 1536;
    private static final String DISTANCE    = "Cosine";

    private final QdrantClient       qdrantClient;
    private final EmbeddingModel     embeddingModel;
    private final HotelRepository    hotelRepo;
    private final FaqRepository      faqRepo;
    private final RestaurantRepository  restaurantRepo;
    private final AttractionRepository  attractionRepo;

    @Override
    public void run(String... args) throws Exception {
        List<Hotel> hotels = hotelRepo.findAll().stream()
                .filter(Hotel::isActive)
                .toList();

        log.info("[Seed] Seeding {} hotels into Qdrant", hotels.size());

        for (Hotel hotel : hotels) {
            seedHotel(hotel);
        }

        log.info("[Seed] Done.");
        System.exit(0);
    }

    // ── Per-hotel seed ────────────────────────────────────────────────────────

    private void seedHotel(Hotel hotel) throws Exception {
        String collection = hotel.getQdrantCollection();
        log.info("[Seed] Hotel: {} → collection: {}", hotel.getName(), collection);

        ensureCollection(collection);

        List<PointStruct> points = new ArrayList<>();

        // FAQs
        List<Faq> faqs = faqRepo.findAllByHotel(hotel.getId());
        for (Faq faq : faqs) {
            String text = "Q: " + faq.getQuestion() + "\nA: " + faq.getAnswer();
            float[] vec  = embed(text);
            points.add(PointStruct.newBuilder()
                    .setId(id(UUID.randomUUID()))
                    .setVectors(vectors(vec))
                    .putAllPayload(Map.of(
                            "hotel_id",  value(hotel.getId()),
                            "doc_type",  value("faq"),
                            "faq_id",    value(faq.getId()),
                            "category",  value(faq.getCategory()),
                            "question",  value(faq.getQuestion()),
                            "answer",    value(faq.getAnswer())
                    ))
                    .build());
        }
        log.info("[Seed]   FAQs: {}", faqs.size());

        // Restaurants
        List<Restaurant> restaurants = restaurantRepo.findTopRated(hotel.getId(), 0.0, 100);
        for (Restaurant r : restaurants) {
            String text = r.getName() + " — " + r.getCuisineType() + ". "
                    + "Rating: " + r.getRating() + ". "
                    + "Distance: " + r.getDistanceText() + ". "
                    + "Ambiance: " + Arrays.toString(r.getAmbiance()) + ".";
            float[] vec = embed(text);

            var payload = new HashMap<String, Value>();
            payload.put("hotel_id",     value(hotel.getId()));
            payload.put("doc_type",     value("restaurant"));
            payload.put("restaurant_id", value(r.getId()));
            payload.put("name",         value(r.getName()));
            payload.put("cuisine_type", value(r.getCuisineType()));
            payload.put("distance",     value(r.getDistanceText() != null ? r.getDistanceText() : ""));
            payload.put("tag",          value(r.getTag()));
            payload.put("tag_text",     value(r.getTagText()));
            if (r.getRating() != null) {
                payload.put("rating", value(r.getRating().doubleValue()));
            }

            points.add(PointStruct.newBuilder()
                    .setId(id(UUID.randomUUID()))
                    .setVectors(vectors(vec))
                    .putAllPayload(payload)
                    .build());
        }
        log.info("[Seed]   Restaurants: {}", restaurants.size());

        // Attractions
        List<Attraction> attractions = attractionRepo.findTopRated(hotel.getId(), 0.0, 100);
        for (Attraction a : attractions) {
            String text = a.getName() + " — " + a.getCategory() + ". "
                    + "Rating: " + a.getRating() + ". "
                    + "Distance: " + a.getDistanceText() + ". "
                    + "Entry: " + (a.getEntryFee() != null ? a.getEntryFee() : "Unknown") + ".";
            float[] vec = embed(text);

            var payload = new HashMap<String, Value>();
            payload.put("hotel_id",      value(hotel.getId()));
            payload.put("doc_type",      value("attraction"));
            payload.put("attraction_id", value(a.getId()));
            payload.put("name",          value(a.getName()));
            payload.put("category",      value(a.getCategory()));
            payload.put("distance",      value(a.getDistanceText() != null ? a.getDistanceText() : ""));
            payload.put("tag",           value(a.getTag()));
            payload.put("tag_text",      value(a.getTagText()));
            if (a.getRating() != null) {
                payload.put("rating", value(a.getRating().doubleValue()));
            }

            points.add(PointStruct.newBuilder()
                    .setId(id(UUID.randomUUID()))
                    .setVectors(vectors(vec))
                    .putAllPayload(payload)
                    .build());
        }
        log.info("[Seed]   Attractions: {}", attractions.size());

        // Upsert in batches of 100
        for (int i = 0; i < points.size(); i += 100) {
            List<PointStruct> batch = points.subList(i, Math.min(i + 100, points.size()));
            qdrantClient.upsertAsync(collection,
                    batch, null).get();
        }
        log.info("[Seed]   Upserted {} points → '{}'", points.size(), collection);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureCollection(String name) throws Exception {
        Set<String> existing = new HashSet<>();
        qdrantClient.listCollectionsAsync().get()
                .forEach(c -> existing.add(c.getName()));

        if (existing.contains(name)) {
            log.info("[Seed]   Collection '{}' already exists", name);
            return;
        }

        qdrantClient.createCollectionAsync(name,
                VectorsConfig.newBuilder()
                        .setParams(VectorParams.newBuilder()
                                .setSize(VECTOR_SIZE)
                                .setDistance(Distance.Cosine)
                                .build())
                        .build())
                .get();
        log.info("[Seed]   Created collection '{}'", name);
    }

    private float[] embed(String text) {
        Embedding emb = embeddingModel.embed(text).content();
        return emb.vector();
    }

    // Value factories (Qdrant protobuf helpers)
    private static Value value(String s)  { return Value.newBuilder().setStringValue(s).build(); }
    private static Value value(double d)  { return Value.newBuilder().setDoubleValue(d).build(); }
    private static Value value(long l)    { return Value.newBuilder().setIntegerValue(l).build(); }
}
