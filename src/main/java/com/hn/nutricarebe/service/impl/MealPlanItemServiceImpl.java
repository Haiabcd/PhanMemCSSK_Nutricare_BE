package com.hn.nutricarebe.service.impl;

import static com.hn.nutricarebe.helper.MealPlanHelper.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import com.hn.nutricarebe.dto.TagDirectives;
import com.hn.nutricarebe.dto.ai.MealPlanItemLite;
import com.hn.nutricarebe.dto.ai.SwapContext;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.helper.SuggestionHelper;
import com.hn.nutricarebe.service.MealPlanDayService;
import com.hn.nutricarebe.service.NutritionRuleService;
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

    @Override
    @Transactional
    public void smartSwapMealItem(UUID itemId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());

        MealPlanItem item = mealPlanItemRepository
                .findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MEAL_PLAN_NOT_FOUND));

        // Quyền sở hữu item
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

        // Lướt phân trang ứng viên theo slot
        Pageable pageable = PageRequest.of(0, 50);

        CandidateBest best = null; // Ứng viên tốt nhất tìm được
        boolean hasNext; // Theo dõi còn trang tiếp theo không
        do {
            var slice = foodRepository.findByMealSlot(slot, pageable);
            for (var cand : slice.getContent()) {
                if (cand.getId().equals(oldFoodId)) continue;
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

            // Nếu đã có ứng viên rất sát (ví dụ sai khác kcal < 5%) thì dừng sớm
            if (best != null && isGoodEnough(best.score, oldSnap)) break;
        } while (hasNext);

        if (best == null) {
            throw new AppException(ErrorCode.FOOD_NOT_FOUND);
        }

        // Cập nhật item ngay tại chỗ
        item.setFood(best.food);
        item.setPortion(MealPlanHelper.bd(best.portion, 2));
        item.setNutrition(scaleNutrition(best.food.getNutrition(), best.portion));

        mealPlanItemRepository.save(item);
    }


    @Override
    public List<SwapSuggestion> suggest() {
        SwapContext ctx =getSwapContext();
        List<SwapSuggestion> out = new ArrayList<>();
        Set<UUID> recent = Optional.ofNullable(ctx.getRecentFoodIds()).orElse(Set.of());
        Set<String> avoid = Optional.ofNullable(ctx.getAvoidTags()).orElse(Set.of());
    for (var item : ctx.getItems()) {
        NutritionResponse target = item.getNutrition();
        int targetKcal = (int)Math.round(SuggestionHelper.d(target.getKcal()));
        // cửa sổ kcal 0.5x–2.0x
        int minK = Math.max(20, (int)Math.round(targetKcal * 0.5));
        int maxK = Math.max(minK + 10, (int)Math.round(targetKcal * 2.0));
        var foods = foodRepository.findCandidatesBySlotAndKcal(
                item.getSlot(), minK, maxK, targetKcal, PageRequest.of(0, 300)
        );
        // lọc cứng
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
                if (dist < bestDist) { bestDist = dist; bestPortion = p; }
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
            double kcalRel = Math.abs(
                    SuggestionHelper.d(SuggestionHelper.scale(c.f.getNutrition(), c.portion).getKcal()) - SuggestionHelper.d(target.getKcal())
            ) / Math.max(1.0, SuggestionHelper.d(target.getKcal()));
            double protRel = Math.abs(
                    SuggestionHelper.d(SuggestionHelper.scale(c.f.getNutrition(), c.portion).getProteinG()) - SuggestionHelper.d(target.getProteinG())
            ) / Math.max(1.0, SuggestionHelper.d(target.getProteinG()));

            return SwapCandidate.builder()
                    .foodId(c.f.getId())
                    .foodName(c.f.getName())
                    .portion(BigDecimal.valueOf(c.portion))
                    .reason(String.format("Tương đương kcal/protein (lệch ~%d%%/~%d%%).",
                            Math.round(kcalRel*100), Math.round(protRel*100)))
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
    return out;
}


    /* ==================== Helpers cho smartSwap ==================== */
    public SwapContext getSwapContext() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
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
        List<MealPlanItemLite> liteItems = items.stream().map(i -> {
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
}
