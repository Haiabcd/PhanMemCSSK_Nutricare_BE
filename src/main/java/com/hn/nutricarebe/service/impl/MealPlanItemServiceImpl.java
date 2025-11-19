package com.hn.nutricarebe.service.impl;

import static com.hn.nutricarebe.helper.MealPlanHelper.*;
import static com.hn.nutricarebe.helper.SuggestionHelper.buildImageUrl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.hn.nutricarebe.dto.TagDirectives;
import com.hn.nutricarebe.dto.ai.MealPlanItemLite;
import com.hn.nutricarebe.dto.ai.SwapContext;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.helper.SuggestionHelper;
import com.hn.nutricarebe.service.MealPlanDayService;
import com.hn.nutricarebe.service.NutritionRuleService;
import lombok.experimental.NonFinal;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.helper.MealPlanHelper;
import com.hn.nutricarebe.repository.*;
import com.hn.nutricarebe.service.MealPlanItemService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MealPlanItemServiceImpl implements MealPlanItemService {
    MealPlanItemRepository mealPlanItemRepository;
    FoodRepository foodRepository;
    MealPlanDayRepository mealPlanDayRepository;
    PlanLogRepository planLogRepository;
    MealPlanDayService mealPlanDayService;
    NutritionRuleService nutritionRuleService;

    @NonFinal
    @Value("${cdn.base-url}")
    String cdnBaseUrl;

    @NonFinal
    Map<String, SuggestCacheEntry> suggestCache = new ConcurrentHashMap<>();

    @NonFinal
    Map<UUID, Deque<UUID>> swapHistoryPerItem = new ConcurrentHashMap<>();

    static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    @Transactional
    public void smartSwapMealItem(UUID itemId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());
        MealPlanItem item = mealPlanItemRepository
                .findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MEAL_PLAN_NOT_FOUND));
        if (item.getDay() == null
                || item.getDay().getUser() == null
                || !userId.equals(item.getDay().getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (item.isUsed()) {
            throw new AppException(ErrorCode.MEAL_PLAN_ITEM_USED);
        }
        // Dữ liệu tham chiếu cũ
        MealSlot slot = item.getMealSlot();
        var oldFood = item.getFood();
        var oldSnap = item.getNutrition();
        var oldFoodId = oldFood.getId();
        // ========= LỊCH SỬ ĐỔI MÓN THEO ITEM (PHẦN MỚI) =========
        Deque<UUID> history = swapHistoryPerItem
                .computeIfAbsent(itemId, k -> new ArrayDeque<>());
        Set<UUID> recentFoods = new HashSet<>();
        LocalDate baseDate = Optional.ofNullable(item.getDay())
                .map(MealPlanDay::getDate)
                .orElse(LocalDate.now(VN_ZONE));
        final int NO_REPEAT_DAYS = 3;
        LocalDate startRecent = baseDate.minusDays(NO_REPEAT_DAYS);
        LocalDate endRecent   = baseDate.plusDays(NO_REPEAT_DAYS);
        // 1) Món đã ăn +/- 3 ngày
        List<PlanLog> logsRecent =
                planLogRepository.findByUser_IdAndDateBetween(userId, startRecent, endRecent);
        logsRecent.stream()
                .map(PlanLog::getFood)
                .filter(Objects::nonNull)
                .map(Food::getId)
                .forEach(recentFoods::add);
        // 2) Món đã/đang nằm trong plan +/- 3 ngày
        Set<UUID> planned =
                mealPlanItemRepository.findDistinctFoodIdsPlannedBetween(userId, startRecent, endRecent);
        if (planned != null) {
            recentFoods.addAll(planned);
        }
        // 3) Không bao giờ chọn lại món hiện tại
        recentFoods.add(oldFoodId);
        // 4) TRÁNH CÁC MÓN ĐÃ TỪNG GẮN VỚI ITEM NÀY TRONG QUÁ KHỨ GẦN (A ↔ B ↔ A)
        recentFoods.addAll(history);

        // Lướt phân trang ứng viên theo slot
        Pageable pageable = PageRequest.of(0, 50);

        CandidateBest best = null; // Ứng viên tốt nhất tìm được
        boolean hasNext; // Theo dõi còn trang tiếp theo không
        do {
            var slice = foodRepository.findByMealSlot(slot, pageable);
            for (var cand : slice.getContent()) {
                if (recentFoods.contains(cand.getId())) continue;
                if (!tagsEqual(cand.getTags(), oldFood.getTags())) continue;
                var n = cand.getNutrition();
                // Tìm portion thuộc {1.5, 1.0, 0.5}
                PortionScore ps = bestPortionAgainstTarget(n, oldSnap);
                // Tính điểm chênh lệch (L1 weighted)
                double score = ps.distance;
                if (best == null || score < best.score) {
                    best = new CandidateBest(cand, ps.portion, score);
                }
            }
            hasNext = slice.hasNext();
            if (hasNext) pageable = slice.nextPageable();
            if (best != null && isGoodEnough(best.score, oldSnap)) break;
        } while (hasNext);

        if (best == null) {
            throw new AppException(ErrorCode.FOOD_NOT_FOUND);
        }
        // ===== Cập nhật lịch sử swap cho item này =====
        history.addLast(oldFoodId);
        while (history.size() > 5) {
            history.removeFirst();
        }
        item.setFood(best.food);
        item.setPortion(MealPlanHelper.bd(best.portion, 2));
        item.setNutrition(scaleNutrition(best.food.getNutrition(), best.portion));
        mealPlanItemRepository.save(item);
    }

    @Override
    @Transactional
    public List<SwapSuggestion> suggest() {
        UUID userId = getCurrentUserId();
        SwapContext ctx = getSwapContext(userId);
        LocalDate today = LocalDate.now(VN_ZONE);
        String cacheKey = buildContextKey(userId, ctx);
        String signature = buildItemsSignature(ctx);
        SuggestCacheEntry cached = suggestCache.get(cacheKey);
        if (cached != null) {
            if (cached.isExpired(today)) {
                suggestCache.remove(cacheKey);
            } else if (Objects.equals(cached.signature, signature)) {
                return cached.suggestions;
            }
        }
        List<SwapSuggestion> out = new ArrayList<>();
        Set<UUID> recent = Optional.ofNullable(ctx.getRecentFoodIds()).orElse(Set.of());
        Set<String> avoid = Optional.ofNullable(ctx.getAvoidTags()).orElse(Set.of());

        for (var item : ctx.getItems()) {
            NutritionResponse target = item.getNutrition();
            int targetKcal = (int) Math.round(SuggestionHelper.d(target.getKcal()));
            int minK = Math.max(20, (int) Math.round(targetKcal * 0.5));
            int maxK = Math.max(minK + 10, (int) Math.round(targetKcal * 2.0));
            String slotStr = item.getSlot();
            MealSlot slot = MealSlot.valueOf(slotStr);

            var foods = foodRepository.findCandidatesBySlotAndKcal(
                    slot, minK, maxK, targetKcal, PageRequest.of(0, 300)
            );

            var filtered = foods.stream()
                    .filter(f -> !f.getId().equals(item.getCurrentFoodId()))
                    .filter(f -> !recent.contains(f.getId()))
                    .filter(f -> f.getTags() == null || f.getTags().stream()
                            .map(Tag::getNameCode).noneMatch(avoid::contains))
                    .toList();

            record Cand(Food f, double portion, double distance) {}
            List<Cand> scored = new ArrayList<>();

            for (var f : filtered) {
                Nutrition base = f.getNutrition();
                double bestDist = Double.POSITIVE_INFINITY;
                double bestPortion = 1.0;

                for (double p : SuggestionHelper.PORTIONS) {
                    var scaled = SuggestionHelper.scale(base, p);
                    double dist = SuggestionHelper.dist(scaled, target);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestPortion = p;
                    }
                }

                if (SuggestionHelper.isGoodEnough(bestDist, target)) {
                    scored.add(new Cand(f, bestPortion, bestDist));
                }
            }

            var top = scored.stream()
                    .sorted(Comparator.comparingDouble(c -> c.distance))
                    .limit(SuggestionHelper.TOP_K)
                    .toList();

            List<SwapCandidate> candidates = top.stream().map(c -> {
                var scaled = SuggestionHelper.scale(c.f.getNutrition(), c.portion);

                double kcalRel = Math.abs(
                        SuggestionHelper.d(scaled.getKcal()) - SuggestionHelper.d(target.getKcal())
                ) / Math.max(1.0, SuggestionHelper.d(target.getKcal()));

                double protRel = Math.abs(
                        SuggestionHelper.d(scaled.getProteinG()) - SuggestionHelper.d(target.getProteinG())
                ) / Math.max(1.0, SuggestionHelper.d(target.getProteinG()));

                int kcalPct = (int) Math.round(kcalRel * 100);
                int protPct = (int) Math.round(protRel * 100);

                String reason = String.format(
                        "Món thay thế giữ gần đúng lượng calo và protein trong kế hoạch, chỉ chênh khoảng %d%% kcal và %d%% protein so với món gốc.",
                        kcalPct, protPct
                );

                return SwapCandidate.builder()
                        .foodId(c.f.getId())
                        .foodName(c.f.getName())
                        .portion(BigDecimal.valueOf(c.portion))
                        .imageUrl(buildImageUrl(c.f.getImageKey(), cdnBaseUrl))
                        .reason(reason)
                        .build();
            }).toList();

            out.add(SwapSuggestion.builder()
                    .itemId(item.getItemId())
                    .slot(item.getSlot())
                    .originalFoodId(item.getCurrentFoodId())
                    .originalFoodName(item.getCurrentFoodName())
                    .originalPortion(item.getPortion())
                    .candidates(candidates)
                    .build());
        }
        SuggestCacheEntry entry = new SuggestCacheEntry(signature, out, today);
        suggestCache.put(cacheKey, entry);

        return out;
    }

    @Override
    public void updateCache(UUID userId, MealPlanItem itemOld) {
        MealPlanDay day = itemOld.getDay();
        if (day != null) {
            String cacheKey = userId + "|" + day.getId() + "|" + day.getDate();
            SuggestCacheEntry cacheEntry = suggestCache.get(cacheKey);
            if (cacheEntry != null && !cacheEntry.isExpired(LocalDate.now(VN_ZONE))) {
                UUID itemId = itemOld.getId();
                // 1. Xoá tất cả SwapSuggestion của itemId này khỏi danh sách
                List<SwapSuggestion> newSuggestions = cacheEntry.suggestions.stream()
                        .filter(s -> !Objects.equals(s.getItemId(), itemId))
                        .toList();
                // 2. Cập nhật lại signature: bỏ segment có prefix "itemId:"
                String oldSig = cacheEntry.signature;
                String itemPrefix = itemId.toString() + ":";

                String newSig = Arrays.stream(oldSig.split("\\|"))
                        .filter(seg -> !seg.startsWith(itemPrefix))
                        .collect(Collectors.joining("|"));
                // 3. Nếu không còn suggestion nào nữa -> xoá luôn cache entry
                if (newSuggestions.isEmpty() || newSig.isEmpty()) {
                    suggestCache.remove(cacheKey);
                } else {
                    suggestCache.put(cacheKey,
                            new SuggestCacheEntry(newSig, newSuggestions, cacheEntry.createdDate));
                }
            }
        }
    }


    /* ==================== Helpers cho smartSwap ==================== */
    private SwapContext getSwapContext(UUID userId) {
        LocalDate today = LocalDate.now(VN_ZONE);
        MealPlanDay day = mealPlanDayRepository
                .findByUser_IdAndDate(userId, today)
                .orElseThrow(() -> new AppException(ErrorCode.MEAL_PLAN_NOT_FOUND));
        Set<MealPlanItemResponse> items = mealPlanDayService.getMealPlanByDate(today).getItems();
        final int NO_REPEAT_DAYS = 2;
        Set<UUID> recent = new HashSet<>();
        LocalDate startRecent = today.minusDays(NO_REPEAT_DAYS);
        List<PlanLog> logsRecent = planLogRepository.findByUser_IdAndDateBetween(userId, startRecent, today);
        logsRecent.stream()
                .map(PlanLog::getFood)
                .filter(Objects::nonNull)
                .map(Food::getId)
                .forEach(recent::add);
        Set<UUID> plannedPast =
                mealPlanItemRepository.findDistinctFoodIdsPlannedBetween(userId, startRecent, today.minusDays(1));
        if (plannedPast != null) recent.addAll(plannedPast);
        items.stream().map(i -> i.getFood().getId()).forEach(recent::add);
        LocalDate endFuture = today.plusDays(NO_REPEAT_DAYS);
        Set<UUID> plannedFuture =
                mealPlanItemRepository.findDistinctFoodIdsPlannedBetween(userId, today.plusDays(1), endFuture);
        if (plannedFuture != null) recent.addAll(plannedFuture);

        List<NutritionRule> rules = nutritionRuleService.getRuleByUserId(userId);
        TagDirectives tagDir = MealPlanHelper.buildTagDirectives(
                rules, com.hn.nutricarebe.dto.request.MealPlanCreationRequest.builder().userId(userId).build());
        Set<String> avoidTags = tagDir.getAvoid();
        List<MealPlanItemLite> liteItems = items.stream()
                .filter(i -> !i.isUsed())
                .map(i -> {
            FoodResponse f = i.getFood();
            return MealPlanItemLite.builder()
                    .itemId(i.getId())
                    .slot(i.getMealSlot())
                    .currentFoodId(f.getId())
                    .currentFoodName(f.getName())
                    .portion(i.getPortion())
                    .nutrition(i.getNutrition())
                    .build();
        }).collect(Collectors.toList());

        return SwapContext.builder()
                .dayId(day.getId())
                .date(day.getDate())
                .items(liteItems)
                .avoidTags(avoidTags == null ? Set.of() : avoidTags)
                .recentFoodIds(recent)
                .build();
    }

    // So sánh "y chang" tập tags (không hơn không kém)
    private static boolean tagsEqual(Set<Tag> a, Set<Tag> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;

        Set<UUID> aIds = a.stream().map(Tag::getId).collect(Collectors.toSet());
        Set<UUID> bIds = b.stream().map(Tag::getId).collect(Collectors.toSet());
        return aIds.equals(bIds);
    }

    // Chọn khẩu phần tốt nhất  + điểm chênh lệch so với mục tiêu
    private PortionScore bestPortionAgainstTarget(Nutrition cand, Nutrition target) {
        double bestDist = Double.POSITIVE_INFINITY;
        double bestStep = 1.0;
        for (double step : PORTION_STEPS) {
            Nutrition s = scaleNutrition(cand, step);
            double dist = nutritionDistanceL1Weighted(s, target);
            if (dist < bestDist) {
                bestDist = dist;
                bestStep = step;
            }
        }
        return new PortionScore(bestStep, bestDist);
    }

    // Khoảng cách L1 có trọng số giữa hai snapshot (kcal ưu tiên, rồi protein/carb/fat)
    private double nutritionDistanceL1Weighted(Nutrition a, Nutrition b) {
        double dk = Math.abs(safeDouble(a.getKcal()) - safeDouble(b.getKcal()));
        double dp = Math.abs(safeDouble(a.getProteinG()) - safeDouble(b.getProteinG()));
        double dc = Math.abs(safeDouble(a.getCarbG()) - safeDouble(b.getCarbG()));
        double df = Math.abs(safeDouble(a.getFatG()) - safeDouble(b.getFatG()));
        return dk + dp * 0.4 + dc * 0.3 + df * 0.3;
    }

    private boolean isGoodEnough(double score, Nutrition target) {
        double kcal = Math.max(1.0, safeDouble(target.getKcal()));
        return score < (0.05 * kcal + 20.0);
    }

    private static class PortionScore {
        final double portion;
        final double distance;

        PortionScore(double portion, double distance) {
            this.portion = portion;
            this.distance = distance;
        }
    }

    private static class CandidateBest {
        final Food food;
        final double portion;
        final double score;

        CandidateBest(Food food, double portion, double score) {
            this.food = food;
            this.portion = portion;
            this.score = score;
        }
    }



    private static class SuggestCacheEntry {
        final String signature;
        final List<SwapSuggestion> suggestions;
        final LocalDate createdDate; // chỉ sống trong ngày

        SuggestCacheEntry(String signature, List<SwapSuggestion> suggestions, LocalDate createdDate) {
            this.signature = signature;
            this.suggestions = suggestions;
            this.createdDate = createdDate;
        }
        boolean isExpired(LocalDate today) {
            return createdDate.isBefore(today);
        }
    }

    private UUID getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(auth.getName());
    }

    // Key theo user + day (một user có thể có nhiều day trong tương lai)
    private String buildContextKey(UUID userId, SwapContext ctx) {
        return userId + "|" + ctx.getDayId() + "|" + ctx.getDate();
    }

    // Signature mô tả danh sách item hiện tại
    private String buildItemsSignature(SwapContext ctx) {
        return ctx.getItems().stream()
                .sorted(Comparator.comparing(MealPlanItemLite::getItemId))
                .map(i -> i.getItemId()
                        + ":" + i.getCurrentFoodId()
                        + ":" + i.getPortion())
                .collect(Collectors.joining("|"));
    }
}
