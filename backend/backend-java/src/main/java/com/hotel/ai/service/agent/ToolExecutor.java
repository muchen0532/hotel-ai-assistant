package com.hotel.ai.service.agent;

import com.hotel.ai.constants.IntentTypes;
import com.hotel.ai.model.dto.agent.*;
import com.hotel.ai.model.entity.*;
import com.hotel.ai.repository.*;
import com.hotel.ai.service.tools.QdrantSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class ToolExecutor {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final double DEFAULT_MIN_RATING = 4.0;
    private static final int DEFAULT_RESULT_LIMIT = 3;

    private final FaqRepository faqRepo;
    private final FacilityRepository facilityRepo;
    private final RestaurantRepository restaurantRepo;
    private final AttractionRepository attractionRepo;
    private final QdrantSearchService qdrantService;


    public AgentState execute(AgentState state, Consumer<List<AgentTraceStep>> traceConsumer) {
        var trace = new ArrayList<AgentTraceStep>();
        var meta = AgentMessageMeta.builder();

        try {
            switch (state.getIntent()) {
                case IntentTypes.FAQ -> handleFaq(state, trace, traceConsumer, meta);
                case IntentTypes.FACILITIES -> handleFacilities(state, trace, traceConsumer, meta);
                case IntentTypes.RESTAURANT -> handleRestaurant(state, trace, traceConsumer, meta);
                case IntentTypes.ATTRACTIONS -> handleAttractions(state, trace, traceConsumer, meta);
                case IntentTypes.ROOM_CONTROL -> handleRoomControl(state, trace, traceConsumer, meta);
                case IntentTypes.DELIVERY -> handleDelivery(state, trace, traceConsumer, meta);
                case IntentTypes.CONCIERGE -> handleConcierge(state, trace, traceConsumer, meta);
                // IntentTypes.GENERAL: 不调工具
            }
        } catch (Exception e) {
            log.error("[Tools] Unexpected error intent={}: {}", state.getIntent(), e.getMessage(), e);
            errorStep(trace, "Tool error: " + e.getMessage(), traceConsumer);
        }

        state.setToolTrace(trace);
        state.setResponseMeta(meta.agentTrace(List.copyOf(trace)).build());
        return state;
    }


    private void handleFaq(AgentState state, List<AgentTraceStep> trace,
                           Consumer<List<AgentTraceStep>> emit,
                           AgentMessageMeta.AgentMessageMetaBuilder meta) {
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
            runStep(trace, emit, "PostgreSQL: fuzzy FAQ fallback", () -> {
                List<Faq> all = faqRepo.findAllByHotel(state.getHotelId());
                if (!all.isEmpty()) {
                    String prefix = state.getUserMessage()
                            .toLowerCase()
                            .substring(0, Math.min(6, state.getUserMessage().length()));
                    Faq best = all.stream()
                            .filter(f -> f.getQuestion().toLowerCase().contains(prefix))
                            .findFirst()
                            .orElse(all.get(0));
                    meta.infoGrid(toInfoGrid(best.getAnswerGrid()));
                    meta.chips(best.getFollowUpChips());
                    state.setToolResults(List.of(best));
                }
            });
        });
    }


    private void handleFacilities(AgentState state, List<AgentTraceStep> trace,
                                  Consumer<List<AgentTraceStep>> emit,
                                  AgentMessageMeta.AgentMessageMetaBuilder meta) {
        String category = state.getFaqCategory();   // IntentClassifier 复用该字段存设施子类
        String label = "PostgreSQL: facilities query" + (category != null ? " · " + category : "");

        runStep(trace, emit, label, () -> {
            List<Facility> facilities = category != null
                    ? facilityRepo.findByHotelAndCategory(state.getHotelId(), category)
                    : facilityRepo.findAllByHotel(state.getHotelId());

            List<AgentInfoGridItem> grid = facilities.stream()
                    .limit(4)
                    .map(f -> AgentInfoGridItem.builder()
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


    private void handleRestaurant(AgentState state, List<AgentTraceStep> trace,
                                  Consumer<List<AgentTraceStep>> emit,
                                  AgentMessageMeta.AgentMessageMetaBuilder meta) {
        double minRating = state.getMinRating() != null ? state.getMinRating() : DEFAULT_MIN_RATING;
        List<String> ambiance = state.getAmbiance();
        Integer maxPrice = state.getMaxPrice();

        List<String> qdrantNames = new ArrayList<>();
        runStep(trace, emit, "Qdrant: semantic search · hotel=" + state.getHotelId(), () -> {
            List<String> names = qdrantService.searchRestaurantNames(
                    state.getHotelId(), state.getUserMessage(), 8);
            qdrantNames.addAll(names);
            log.debug("[Restaurant] qdrantNames={}", qdrantNames);
        });

        runStep(trace, emit, "Filter: ambiance=" + (ambiance != null ? ambiance : "any")
                + ", price≤" + (maxPrice != null ? maxPrice : "any")
                + ", rating≥" + minRating, () -> {
        });

        List<Restaurant> pgResults = new ArrayList<>();
        runStep(trace, emit, "PostgreSQL: hours & availability lookup", () -> {
            String pgAmbiance = ambiance != null ? "{" + String.join(",", ambiance) + "}" : null;
            List<Restaurant> results = restaurantRepo.findFiltered(
                    state.getHotelId(), minRating, maxPrice, pgAmbiance, DEFAULT_RESULT_LIMIT + 3);
            pgResults.addAll(results);
            log.debug("[Restaurant] pgResults={}", pgResults.stream().map(Restaurant::getName).toList());
        });

        runStep(trace, emit, "ReRank by semantic score + rating", () -> {
            List<Restaurant> reranked = rerank(pgResults, qdrantNames, DEFAULT_RESULT_LIMIT);
            log.debug("[Restaurant] reranked={}", reranked.stream().map(Restaurant::getName).toList());
            meta.cards(reranked.stream().map(r -> toRestaurantCard(r, qdrantNames)).toList());
            meta.chips(List.of("有素食选项吗？", "可以预订餐位吗？", "有着装要求吗？"));
            state.setToolResults(new ArrayList<>(reranked));
        });
    }


    private void handleAttractions(AgentState state, List<AgentTraceStep> trace,
                                   Consumer<List<AgentTraceStep>> emit,
                                   AgentMessageMeta.AgentMessageMetaBuilder meta) {
        String category = state.getAttractionCategory();

        // Step 1: Qdrant RAG
        List<String> qdrantNames = new ArrayList<>();
        runStep(trace, emit, "Qdrant: RAG retrieval · hotel=" + state.getHotelId(), () -> {
            List<String> names = qdrantService.searchAttractionNames(
                    state.getHotelId(), state.getUserMessage(), 8);
            qdrantNames.addAll(names);
            log.debug("[Attractions] qdrantNames={}", qdrantNames);
        });

        runStep(trace, emit, "Filter: category=" + (category != null ? category : "all") + ", rating≥4.0", () -> {
        });

        List<Attraction> pgResults = new ArrayList<>();
        runStep(trace, emit, "PostgreSQL: metadata enrichment", () -> {
            List<Attraction> results = category != null
                    ? attractionRepo.findByCategory(state.getHotelId(), category, DEFAULT_MIN_RATING, DEFAULT_RESULT_LIMIT + 2)
                    : attractionRepo.findTopRated(state.getHotelId(), DEFAULT_MIN_RATING, DEFAULT_RESULT_LIMIT + 2);
            pgResults.addAll(results);
            log.debug("[Attractions] pgResults={}", pgResults.stream().map(Attraction::getName).toList());
        });

        runStep(trace, emit, "Rerank by relevance & rating", () -> {
            if (pgResults.isEmpty() && !qdrantNames.isEmpty()) {
                // Qdrant 有结果但 PG 没查到, 按名字补查一次
                log.warn("[Attractions] pgResults empty, fallback to qdrantNames lookup");
                List<Attraction> fallback = attractionRepo.findByNames(state.getHotelId(), qdrantNames);
                pgResults.addAll(fallback);
            }

            List<Attraction> reranked = rerank(pgResults, qdrantNames, DEFAULT_RESULT_LIMIT);
            log.debug("[Attractions] reranked={}, cards={}",
                    reranked.stream().map(Attraction::getName).toList(), reranked.size());

            meta.cards(reranked.stream().map(this::toAttractionCard).toList());
            meta.chips(List.of("如何前往？", "门票多少钱？", "几点开放？"));
            state.setToolResults(new ArrayList<>(reranked));
        });
    }


    private void handleRoomControl(AgentState state, List<AgentTraceStep> trace,
                                   Consumer<List<AgentTraceStep>> emit,
                                   AgentMessageMeta.AgentMessageMetaBuilder meta) {
        String action = state.getRoomControlAction();
        String value = state.getRoomControlValue();
        String label = "RoomControl: action=" + action + (value != null ? " · value=" + value : "");

        runStep(trace, emit, label, () -> {
            // TODO: 对接客控系统 API
            // roomControlClient.send(state.getRoomNumber(), action, value);
            log.info("[RoomControl] room={} action={} value={}", state.getRoomNumber(), action, value);

            meta.chips(buildRoomControlChips(action));
            state.setToolResults(List.of(Map.of(
                    "action", action != null ? action : "",
                    "value", value != null ? value : "",
                    "room", state.getRoomNumber() != null ? state.getRoomNumber() : ""
            )));
        });
    }


    private void handleDelivery(AgentState state, List<AgentTraceStep> trace,
                                Consumer<List<AgentTraceStep>> emit,
                                AgentMessageMeta.AgentMessageMetaBuilder meta) {
        String item = state.getDeliveryItem();
        int quantity = state.getDeliveryQuantity() != null ? state.getDeliveryQuantity() : 1;
        String label = "Delivery: item=" + item + " · qty=" + quantity;

        runStep(trace, emit, label, () -> {
            // TODO: 对接工单系统
            // workOrderClient.create(state.getHotelId(), state.getRoomNumber(), item, quantity);
            log.info("[Delivery] room={} item={} qty={}", state.getRoomNumber(), item, quantity);

            meta.chips(List.of("还需要其他物品？", "送餐服务", "叫醒服务"));
            state.setToolResults(List.of(Map.of(
                    "item", item != null ? item : "",
                    "quantity", quantity,
                    "room", state.getRoomNumber() != null ? state.getRoomNumber() : ""
            )));
        });
    }


    private void handleConcierge(AgentState state, List<AgentTraceStep> trace,
                                 Consumer<List<AgentTraceStep>> emit,
                                 AgentMessageMeta.AgentMessageMetaBuilder meta) {
        String action = state.getServiceAction();
        String detail = state.getServiceDetail();
        String label = "Concierge: action=" + action + (detail != null ? " · detail=" + detail : "");

        runStep(trace, emit, label, () -> {
            // TODO: 按 serviceAction 路由到不同第三方
            // wakeup       → PMS 叫醒模块等
            // transport    → 滴滴企业版 API / 前台调度
            // luggage      → 行李寄存工单
            // tour_booking → 景区票务 API / 人工转接
            log.info("[Concierge] room={} action={} detail={}", state.getRoomNumber(), action, detail);

            meta.chips(buildConciergeChips(action));
            state.setToolResults(List.of(Map.of(
                    "action", action != null ? action : "",
                    "detail", detail != null ? detail : "",
                    "room", state.getRoomNumber() != null ? state.getRoomNumber() : ""
            )));
        });
    }


    private void runStep(List<AgentTraceStep> trace, Consumer<List<AgentTraceStep>> emit,
                         String label, Runnable action) {
        int idx = trace.size();
        trace.add(AgentTraceStep.builder().label(label).status("running").build());
        emit.accept(List.copyOf(trace));
        try {
            action.run();
            trace.set(idx, AgentTraceStep.builder().label(label).status("done").build());
        } catch (Exception e) {
            trace.set(idx, AgentTraceStep.builder().label(label).status("error").build());
            log.warn("[Tools] Step '{}' failed: {}", label, e.getMessage());
        }
        emit.accept(List.copyOf(trace));
    }

    private void errorStep(List<AgentTraceStep> trace, String msg,
                           Consumer<List<AgentTraceStep>> emit) {
        trace.add(AgentTraceStep.builder().label(msg).status("error").build());
        emit.accept(List.copyOf(trace));
    }


    private List<AgentInfoGridItem> toInfoGrid(List<Map<String, String>> grid) {
        if (grid == null) return List.of();
        return grid.stream()
                .map(m -> AgentInfoGridItem.builder()
                        .label(m.get("label"))
                        .value(m.get("value"))
                        .build())
                .toList();
    }

    private AgentRestaurantCard toRestaurantCard(Restaurant r, List<String> qdrantNames) {
        return AgentRestaurantCard.builder()
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

    private AgentRestaurantCard toAttractionCard(Attraction a) {
        return AgentRestaurantCard.builder()
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

    private List<String> buildRoomControlChips(String action) {
        if (action == null) return List.of();
        return switch (action) {
            case "ac" -> List.of("调高温度", "调低温度", "关闭空调");
            case "light" -> List.of("调亮", "调暗", "关灯");
            case "curtain" -> List.of("打开窗帘", "关闭窗帘", "半开");
            case "dnd" -> List.of("取消勿扰", "请打扫房间");
            case "housekeeping" -> List.of("明天再打扫", "需要换床单");
            case "tv" -> List.of("换台", "调音量", "关闭电视");
            default -> List.of();
        };
    }

    private List<String> buildConciergeChips(String action) {
        if (action == null) return List.of();
        return switch (action) {
            case "wakeup" -> List.of("修改叫醒时间", "取消叫醒");
            case "transport" -> List.of("预约接机", "预约送机", "市内用车");
            case "luggage" -> List.of("寄存行李", "取回行李");
            case "tour_booking" -> List.of("推荐景点", "查询门票", "人工协助");
            default -> List.of();
        };
    }

    /**
     * 按 Qdrant 语义相关度对 PG 结果重排，无匹配项排到末尾。
     */
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