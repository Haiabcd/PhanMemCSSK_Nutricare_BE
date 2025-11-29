package com.hn.nutricarebe.service.impl;

import static com.hn.nutricarebe.helper.MealPlanHelper.*;
import static com.hn.nutricarebe.helper.SuggestionHelper.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import com.hn.nutricarebe.dto.TagDirectives;
import com.hn.nutricarebe.dto.ai.MealPlanItemLite;
import com.hn.nutricarebe.dto.ai.SwapContext;
import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.helper.SuggestionHelper;
import com.hn.nutricarebe.service.*;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
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
    ProfileService profileService;
    NutritionRuleService nutritionRuleService;

    @NonFinal
    @Value("${cdn.base-url}")
    String cdnBaseUrl;

    Map<String, SuggestCacheEntry> suggestCache = new ConcurrentHashMap<>();
    Map<UUID, Deque<UUID>> swapHistoryPerItem = new ConcurrentHashMap<>();

    static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String FRUIT_TAG = "fruit";

    @Override
    @Transactional
    public void swapFood(UUID itemId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
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

        // ===== Lấy context giống suggest(): recentFoodIds + avoidTags =====
        SwapContext ctx = getSwapContext(userId);
        Set<UUID> recentFoods = new HashSet<>(
                Optional.ofNullable(ctx.getRecentFoodIds()).orElse(Set.of())
        );
        Set<String> avoidTags = Optional.ofNullable(ctx.getAvoidTags()).orElse(Set.of());

        // ===== Tham chiếu món cũ =====
        MealSlot slot = item.getMealSlot();
        Food oldFood = item.getFood();
        Nutrition oldSnap = item.getNutrition();
        UUID oldFoodId = oldFood.getId();

        // ===== Lịch sử đổi món cho item này (tránh A ↔ B ↔ A) =====
        Deque<UUID> history = swapHistoryPerItem
                .computeIfAbsent(itemId, k -> new ConcurrentLinkedDeque<>());

        // Không bao giờ chọn lại món hiện tại + các món đã từng swap
        recentFoods.add(oldFoodId);
        recentFoods.addAll(history);

        // ===== Bước 1: Lọc ứng viên theo kcal (Energy filter) giống logic cũ =====
        double targetKcalD = safeDouble(oldSnap.getKcal());
        int targetKcal = (int) Math.round(targetKcalD);

        int minK;
        int maxK;
        if (targetKcal <= 0) {
            minK = 50;
            maxK = 800;
        } else {
            double ratio = KCAL_FILTER_RATIO; // 0.2 = ±20%
            minK = (int) Math.round(targetKcal * (1.0 - ratio));
            maxK = (int) Math.round(targetKcal * (1.0 + ratio));
            minK = Math.max(40, minK);
            maxK = Math.max(minK + 40, maxK);
        }

        List<Food> candidates = foodRepository.findCandidatesBySlotAndKcal(
                slot,
                minK,
                maxK,
                targetKcal,
                PageRequest.of(0, 300)
        );

        CandidateBest bestStrict = null;
        CandidateBest bestRelaxed = null;

        for (Food cand : candidates) {
            // 1) Không dùng lại các món đã / sẽ dùng trong cửa sổ 2 ngày như suggest()
            if (recentFoods.contains(cand.getId())) continue;

            // 2) Chỉ tránh avoidTags (giống suggest), KHÔNG yêu cầu tagsEqual 1–1 nữa
            if (!avoidTags.isEmpty() && cand.getTags() != null) {
                boolean hasAvoid = cand.getTags().stream()
                        .map(Tag::getNameCode)
                        .anyMatch(avoidTags::contains);
                if (hasAvoid) {
                    continue;
                }
            }

            Nutrition candNut = cand.getNutrition();

            // Tìm portion tốt nhất (0.5, 1.0, 1.5) sao cho dinh dưỡng gần nhất
            PortionScore ps = bestPortionAgainstTarget(candNut, oldSnap);
            Nutrition scaled = scaleNutrition(candNut, ps.portion);
            double score = ps.distance;

            // bestRelaxed: luôn giữ ứng viên có score nhỏ nhất để fallback
            if (bestRelaxed == null || score < bestRelaxed.score) {
                bestRelaxed = new CandidateBest(cand, ps.portion, score);
            }

            // Kiểm tra macro/kcal không lệch quá ngưỡng
            if (!withinMacroTolerance(scaled, oldSnap)) {
                continue;
            }

            // Ngưỡng "đủ tốt" strict (dựa trên score)
            if (!isGoodEnoughStrict(score, oldSnap)) {
                continue;
            }

            // bestStrict: ứng viên vừa đủ macro + đủ tốt về score
            if (bestStrict == null || score < bestStrict.score) {
                bestStrict = new CandidateBest(cand, ps.portion, score);
            }
        }

        // Ưu tiên strict, nếu không có thì dùng món gần nhất (bestRelaxed)
        CandidateBest best = (bestStrict != null) ? bestStrict : bestRelaxed;

        if (best == null) {
            // Không tìm được món nào thỏa: không trùng recent, không dính avoid tags
            throw new AppException(ErrorCode.FOOD_NOT_FOUND);
        }

        // ===== Cập nhật lịch sử swap =====
        history.addLast(oldFoodId);
        while (history.size() > 5) {
            history.removeFirst();
        }

        // ===== Áp dụng món mới =====
        item.setFood(best.food);
        item.setPortion(MealPlanHelper.bd(best.portion, 2));
        item.setNutrition(scaleNutrition(best.food.getNutrition(), best.portion));
        mealPlanItemRepository.save(item);
    }


    @Override
    @Transactional
    public List<SwapSuggestion> suggest() {
        boolean hasAnyCandidate = false;
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
                if(!cached.suggestions.isEmpty()) {
                    return cached.suggestions;

                }
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

            if (!candidates.isEmpty()) {
                hasAnyCandidate = true;
            }

            out.add(SwapSuggestion.builder()
                    .itemId(item.getItemId())
                    .slot(item.getSlot())
                    .originalFoodId(item.getCurrentFoodId())
                    .originalFoodName(item.getCurrentFoodName())
                    .originalPortion(item.getPortion())
                    .candidates(candidates)
                    .build());
        }

        if(!hasAnyCandidate) {

            List<SwapSuggestion> noSuggestions = new ArrayList<>();
            List<Food> snackFoods = loadSnackFoodsForFallback(recent, avoid);
            List<SwapCandidate> candidates = new ArrayList<>();
            for(Food f : snackFoods) {
                candidates.add(SwapCandidate.builder()
                        .foodId(f.getId())
                        .foodName(f.getName())
                        .portion(BigDecimal.valueOf(1.0))
                        .imageUrl(buildImageUrl(f.getImageKey(), cdnBaseUrl))
                        .reason("Món ăn nhẹ này có thể là lựa chọn thay thế tốt để bổ sung dinh dưỡng trong ngày.")
                        .build());
            }
            noSuggestions.add(SwapSuggestion.builder()
                    .itemId(UUID.randomUUID())
                    .slot(MealSlot.SNACK.name())
                    .originalFoodId(null)
                    .originalFoodName(null)
                    .originalPortion(BigDecimal.ONE)
                    .candidates(candidates)
                    .build());
            SuggestCacheEntry entry = new SuggestCacheEntry(signature, noSuggestions, today);
            suggestCache.put(cacheKey, entry);
            return noSuggestions;
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

    //=========================HELPER METHODS=========================//
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
        MealPlanCreationRequest mReq = MealPlanCreationRequest.builder()
                .userId(userId)
                .profile(profileService.findByUserId_request(userId))
                .build();
        TagDirectives tagDir = MealPlanHelper.buildTagDirectives(
                rules, mReq);
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

    // Chọn khẩu phần tốt nhất + điểm chênh lệch so với mục tiêu
    private PortionScore bestPortionAgainstTarget(Nutrition cand, Nutrition target) {
        double bestDist = Double.POSITIVE_INFINITY;
        double bestStep = 1.0;
        for (double step : PORTIONS) {
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

    // Ngưỡng filter kcal ban đầu (khoảng ±20% quanh món cũ)
    static final double KCAL_FILTER_RATIO = 0.20;

    // Ngưỡng chênh lệch % cho kcal & macro (0.0–1.0)
    static final double MAX_KCAL_DIFF_RATIO   = 0.20; // ±20% kcal
    static final double MAX_MACRO_DIFF_RATIO  = 0.20; // ±20% protein/carb/fat

    // |a-b| / max(1, b) để tránh chia 0, lấy giá trị dương
    private double relativeDiff(double newVal, double oldVal) {
        double base = Math.max(1.0, Math.abs(oldVal));
        return Math.abs(newVal - oldVal) / base;
    }

    // Kiểm tra xem hồ sơ macro sau khi scale có còn trong ngưỡng cho phép không
    private boolean withinMacroTolerance(Nutrition scaled, Nutrition target) {
        double kcalNew = safeDouble(scaled.getKcal());
        double kcalOld = safeDouble(target.getKcal());

        double protNew = safeDouble(scaled.getProteinG());
        double protOld = safeDouble(target.getProteinG());

        double carbNew = safeDouble(scaled.getCarbG());
        double carbOld = safeDouble(target.getCarbG());

        double fatNew  = safeDouble(scaled.getFatG());
        double fatOld  = safeDouble(target.getFatG());

        double kcalDiff = relativeDiff(kcalNew, kcalOld);
        double protDiff = relativeDiff(protNew, protOld);
        double carbDiff = relativeDiff(carbNew, carbOld);
        double fatDiff  = relativeDiff(fatNew, fatOld);

        // Yêu cầu: cả kcal và 3 macro đều không lệch quá ngưỡng
        return kcalDiff <= MAX_KCAL_DIFF_RATIO
                && protDiff <= MAX_MACRO_DIFF_RATIO
                && carbDiff <= MAX_MACRO_DIFF_RATIO
                && fatDiff  <= MAX_MACRO_DIFF_RATIO;
    }

    private List<Food> loadSnackFoodsForFallback(Set<UUID> recent, Set<String> avoid) {
        var sliceFruit = foodRepository.findByMealSlotAndTag(
                MealSlot.SNACK,
                FRUIT_TAG,
                PageRequest.of(0, 50)
        );

        List<Food> fFruit = sliceFruit.getContent().stream()
                // không lấy món mới dùng gần đây
                .filter(f -> !recent.contains(f.getId()))
                // tránh các tag trong danh sách avoid
                .filter(f -> f.getTags() == null || f.getTags().stream()
                        .map(Tag::getNameCode)
                        .noneMatch(avoid::contains))
                .limit(10)
                .toList();

        if (fFruit.size() >= 10) {
            return fFruit;
        }

        int needed = 10 - fFruit.size();

        // tránh trùng món giữa đợt 1 và đợt 2
        Set<UUID> usedIds = fFruit.stream()
                .map(Food::getId)
                .collect(Collectors.toSet());

        var sliceSnack = foodRepository.findByMealSlot(
                MealSlot.SNACK,
                PageRequest.of(0, 50)
        );

        List<Food> fSnack = sliceSnack.getContent().stream()
                .filter(f -> !recent.contains(f.getId()))
                .filter(f -> !usedIds.contains(f.getId()))
                .filter(f -> f.getTags() == null || f.getTags().stream()
                        .map(Tag::getNameCode)
                        .noneMatch(avoid::contains))
                .limit(needed)
                .toList();

        List<Food> combined = new ArrayList<>(fFruit);
        combined.addAll(fSnack);
        return combined;
    }


}