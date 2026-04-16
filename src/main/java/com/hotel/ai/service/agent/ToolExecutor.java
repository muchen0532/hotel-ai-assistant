package com.hotel.ai.service.agent;

import com.hotel.ai.model.dto.*;
import com.hotel.ai.model.entity.*;
import com.hotel.ai.repository.*;
import com.hotel.ai.service.tools.QdrantSearchService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

import static com.hotel.ai.service.agent.IntentClassifier.*;

/**
 * Node 2 — Tool Execution
 *
 * ReAct pattern:  Thought (IntentClassifier) → Action (here) → Observation
 *
 * For each intent:
 *   faq         → PostgreSQL exact match → fuzzy fallback via trigram
 *   facilities  → PostgreSQL facilities query
 *   restaurant  → Qdrant semantic search → filter → PostgreSQL hours → rerank
 *   attractions → Qdrant RAG → PostgreSQL metadata enrichment → rerank
 *
 * Each step is pushed to a traceConsumer so the SSE layer can stream
 * intermediate trace updates to the frontend in real time.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolExecutor {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final double DEFAULT_MIN_RATING  = 4.0;
    private static final int    DEFAULT_RESULT_LIMIT = 3;

    private final FaqRepository         faqRepo;
    private final FacilityRepository    facilityRepo;
    private final RestaurantRepository  restaurantRepo;
    private final AttractionRepository  attractionRepo;
    private final QdrantSearchService   qdrantService;

    // ── Public entry ──────────────────────────────────────────────────────────

    /**
     * @param state         current agent state (intent already set by IntentClassifier)
     * @param traceConsumer called whenever a trace step changes — drives SSE streaming
     */
    public AgentState execute(AgentState state, Consumer<List<TraceStep>> traceConsumer) {
        var trace = new ArrayList<TraceStep>();
        var meta  = new MessageMeta.MessageMetaBuilder();

        try {
            switch (state.getIntent()) {
                case INTENT_FAQ         -> handleFaq(state, trace, traceConsumer, meta);
                case INTENT_FACILITIES  -> handleFacilities(state, trace, traceConsumer, meta);
                case INTENT_RESTAURANT  -> handleRestaurant(state, trace, traceConsumer, meta);
                case INTENT_ATTRACTIONS -> handleAttractions(state, trace, traceConsumer, meta);
                // INTENT_GENERAL: no tools, LLM answers freely in Node 3
            }
        } catch (Exception e) {
            log.error("[Tools] Unexpected error for intent={}: {}", state.getIntent(), e.getMessage(), e);
            errorStep(trace, "Tool error: " + e.getMessage(), traceConsumer);
        }

        state.setToolTrace(trace);
        state.setResponseMeta(meta.agentTrace(List.copyOf(trace)).build());
        return state;
    }

    // ── FAQ ───────────────────────────────────────────────────────────────────

    private void handleFaq(AgentState state, List<TraceStep> trace,
                           Consumer<List<TraceStep>> emit,
                           MessageMeta.MessageMetaBuilder meta) {
        String category = state.getFaqCategory() != null ? state.getFaqCategory() : "general";

        runStep(trace, emit, "PostgreSQL: FAQ lookup · category=" + category, () -> {
            List<Faq> faqs = faqRepo.findByHotelAndCategory(state.getHotelId(), category);
            if (!faqs.isEmpty()) {
                Faq faq = faqs.get(0);
                meta.infoGrid(toInfoGrid(faq.getAnswerGrid()));
                meta.chips(faq.getFollowUpChips());
                state.setToolResults(List.of(faq));
                return;
            }

            // Fuzzy fallback: search all FAQs and find closest match
            runStep(trace, emit, "PostgreSQL: fuzzy FAQ fallback", () -> {
                List<Faq> all = faqRepo.findAllByHotel(state.getHotelId());
                if (!all.isEmpty()) {
                    // Simple keyword match fallback (Qdrant semantic handles deeper search)
                    Faq best = all.stream()
                            .filter(f -> f.getQuestion().toLowerCase()
                                          .contains(state.getUserMessage().toLowerCase().substring(0, Math.min(6, state.getUserMessage().length()))))
                            .findFirst()
                            .orElse(all.get(0));
                    meta.infoGrid(toInfoGrid(best.getAnswerGrid()));
                    meta.chips(best.getFollowUpChips());
                    state.setToolResults(List.of(best));
                }
            });
        });
    }

    // ── Facilities ────────────────────────────────────────────────────────────

    private void handleFacilities(AgentState state, List<TraceStep> trace,
                                   Consumer<List<TraceStep>> emit,
                                   MessageMeta.MessageMetaBuilder meta) {
        String category = state.getFaqCategory();  // reused field for facility category

        String label = "PostgreSQL: facilities query"
                + (category != null ? " · " + category : "");

        runStep(trace, emit, label, () -> {
            List<Facility> facilities = category != null
                    ? facilityRepo.findByHotelAndCategory(state.getHotelId(), category)
                    : facilityRepo.findAllByHotel(state.getHotelId());

            List<InfoGridItem> grid = facilities.stream()
                    .limit(4)
                    .map(f -> InfoGridItem.builder()
                            .label(f.getName())
                            .value(buildHours(f.isOpen24h(), f.getOpenTime(), f.getCloseTime())
                                   + (f.getLocationDesc() != null ? " · " + f.getLocationDesc() : ""))
                            .build())
                    .toList();

            meta.infoGrid(grid);
            meta.chips(state.getHotelWelcomeChips().stream().limit(3).toList());
            state.setToolResults(new ArrayList<>(facilities));
        });
    }

    // ── Restaurant ────────────────────────────────────────────────────────────

    private void handleRestaurant(AgentState state, List<TraceStep> trace,
                                   Consumer<List<TraceStep>> emit,
                                   MessageMeta.MessageMetaBuilder meta) {
        double minRating = state.getMinRating() != null ? state.getMinRating() : DEFAULT_MIN_RATING;
        List<String> ambiance = state.getAmbiance();
        Integer maxPrice = state.getMaxPrice();

        // Step 1: Qdrant semantic search
        List<String> qdrantNames = new ArrayList<>();
        runStep(trace, emit, "Qdrant: semantic search · hotel=" + state.getHotelId(), () -> {
            List<String> names = qdrantService.searchRestaurantNames(
                    state.getHotelId(), state.getUserMessage(), 8);
            qdrantNames.addAll(names);
        });

        // Step 2: Filter
        runStep(trace, emit, "Filter: ambiance=" + (ambiance != null ? ambiance : "any")
                + ", price≤" + (maxPrice != null ? maxPrice : "any")
                + ", rating≥" + minRating, () -> {});

        // Step 3: PostgreSQL for authoritative data
        List<Restaurant> pgResults = new ArrayList<>();
        runStep(trace, emit, "PostgreSQL: hours & availability lookup", () -> {
            String ambianceParam = ambiance != null ? String.join(",", ambiance) : null;
            // Wrap as Postgres array literal if present
            String pgAmbiance = ambianceParam != null ? "{" + ambianceParam + "}" : null;

            List<Restaurant> results = restaurantRepo.findFiltered(
                    state.getHotelId(), minRating, maxPrice, pgAmbiance, DEFAULT_RESULT_LIMIT + 3);
            pgResults.addAll(results);
        });

        // Step 4: Rerank by Qdrant semantic score order
        runStep(trace, emit, "Rerank by semantic score + rating", () -> {
            List<Restaurant> reranked = rerank(pgResults, qdrantNames, DEFAULT_RESULT_LIMIT);
            List<RestaurantCard> cards = reranked.stream()
                    .map(r -> toRestaurantCard(r, qdrantNames))
                    .toList();
            meta.cards(cards);
            meta.chips(List.of("Any vegetarian options?", "Make a reservation?", "Dress code?"));
            state.setToolResults(new ArrayList<>(reranked));
        });
    }

    // ── Attractions ───────────────────────────────────────────────────────────

    private void handleAttractions(AgentState state, List<TraceStep> trace,
                                    Consumer<List<TraceStep>> emit,
                                    MessageMeta.MessageMetaBuilder meta) {
        String category = state.getAttractionCategory();

        // Step 1: Qdrant RAG
        List<String> qdrantNames = new ArrayList<>();
        runStep(trace, emit, "Qdrant: RAG retrieval · hotel=" + state.getHotelId(), () -> {
            List<String> names = qdrantService.searchAttractionNames(
                    state.getHotelId(), state.getUserMessage(), 8);
            qdrantNames.addAll(names);
        });

        // Step 2: Filter
        runStep(trace, emit, "Filter: category=" + (category != null ? category : "all") + ", rating≥4.0", () -> {});

        // Step 3: PostgreSQL metadata enrichment
        List<Attraction> pgResults = new ArrayList<>();
        runStep(trace, emit, "PostgreSQL: metadata enrichment", () -> {
            List<Attraction> results = category != null
                    ? attractionRepo.findByCategory(state.getHotelId(), category, 4.0, DEFAULT_RESULT_LIMIT + 2)
                    : attractionRepo.findTopRated(state.getHotelId(), 4.0, DEFAULT_RESULT_LIMIT + 2);
            pgResults.addAll(results);
        });

        // Step 4: Rerank
        runStep(trace, emit, "Rerank by relevance & rating", () -> {
            List<Attraction> reranked = rerank(pgResults, qdrantNames, DEFAULT_RESULT_LIMIT);
            List<RestaurantCard> cards = reranked.stream()
                    .map(this::toAttractionCard)
                    .toList();
            meta.cards(cards);
            meta.chips(List.of("Get directions?", "Entry fees?", "Opening hours?"));
            state.setToolResults(new ArrayList<>(reranked));
        });
    }

    // ── Step runner helpers ───────────────────────────────────────────────────

    private void runStep(List<TraceStep> trace, Consumer<List<TraceStep>> emit,
                         String label, Runnable action) {
        int idx = trace.size();
        trace.add(TraceStep.builder().label(label).status("running").build());
        emit.accept(List.copyOf(trace));
        try {
            action.run();
            trace.set(idx, TraceStep.builder().label(label).status("done").build());
        } catch (Exception e) {
            trace.set(idx, TraceStep.builder().label(label).status("error").build());
            log.warn("[Tools] Step '{}' failed: {}", label, e.getMessage());
        }
        emit.accept(List.copyOf(trace));
    }

    private void errorStep(List<TraceStep> trace, String msg, Consumer<List<TraceStep>> emit) {
        trace.add(TraceStep.builder().label(msg).status("error").build());
        emit.accept(List.copyOf(trace));
    }

    // ── Conversion helpers ────────────────────────────────────────────────────

    private List<InfoGridItem> toInfoGrid(List<java.util.Map<String, String>> grid) {
        if (grid == null) return List.of();
        return grid.stream()
                .map(m -> InfoGridItem.builder()
                        .label(m.get("label"))
                        .value(m.get("value"))
                        .build())
                .toList();
    }

    private RestaurantCard toRestaurantCard(Restaurant r, List<String> qdrantNames) {
        return RestaurantCard.builder()
                .emoji(r.getEmoji() != null ? r.getEmoji() : "🍽️")
                .name(r.getName())
                .type(r.getCuisineType())
                .rating(r.getRating() != null ? "★ " + r.getRating() : "")
                .hours(buildHours(r.isOpen24h(), r.getOpenTime(), r.getCloseTime()))
                .distance(r.getDistanceText() != null ? r.getDistanceText() : "")
                .tag(r.getTag())
                .tagText(r.getTagText())
                .build();
    }

    private RestaurantCard toAttractionCard(Attraction a) {
        return RestaurantCard.builder()
                .emoji(a.getEmoji() != null ? a.getEmoji() : "📍")
                .name(a.getName())
                .type(a.getCategory() + (a.getDistanceText() != null ? " · " + a.getDistanceText() : ""))
                .rating(a.getRating() != null ? "★ " + a.getRating() : "")
                .hours(buildHours(a.isOpen24h(), a.getOpenTime(), a.getCloseTime()))
                .distance(a.getDistanceText() != null ? a.getDistanceText() : "")
                .tag(a.getTag())
                .tagText(a.getTagText())
                .build();
    }

    private String buildHours(boolean open24h, LocalTime open, LocalTime close) {
        if (open24h) return "24/7";
        if (open != null && close != null) return open.format(TIME_FMT) + "–" + close.format(TIME_FMT);
        return "";
    }

    /** Reorder pgResults to match qdrantNames order (semantic relevance first). */
    private <T> List<T> rerank(List<T> items, List<String> nameOrder, int limit) {
        if (nameOrder.isEmpty()) return items.stream().limit(limit).toList();
        return items.stream()
                .sorted((a, b) -> {
                    int ia = nameOrder.indexOf(getName(a));
                    int ib = nameOrder.indexOf(getName(b));
                    if (ia < 0) ia = 999;
                    if (ib < 0) ib = 999;
                    return Integer.compare(ia, ib);
                })
                .limit(limit)
                .toList();
    }

    private String getName(Object item) {
        if (item instanceof Restaurant r) return r.getName();
        if (item instanceof Attraction a) return a.getName();
        return "";
    }
}
