package com.hn.nutricarebe.service.impl;

import static com.hn.nutricarebe.helper.MealPlanHelper.*;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hn.nutricarebe.dto.TagDirectives;
import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.helper.MealPlanHelper;
import com.hn.nutricarebe.mapper.CdnHelper;
import com.hn.nutricarebe.mapper.FoodMapper;
import com.hn.nutricarebe.mapper.ProfileMapper;
import com.hn.nutricarebe.repository.*;
import com.hn.nutricarebe.service.MealPlanItemService;
import com.hn.nutricarebe.service.NutritionRuleService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MealPlanItemServiceImpl implements MealPlanItemService {
    MealPlanItemRepository mealPlanItemRepository;
    NutritionRuleService nutritionRuleService;
    ProfileRepository profileRepository;
    FoodRepository foodRepository;
    ProfileMapper profileMapper;
    FoodMapper foodMapper;
    CdnHelper cdnHelper;

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
    @Transactional
    public List<FoodResponse> suggestAllowedFoodsInternal(MealSlot slot, int limit) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());

        Profile profile = profileRepository
                .findByUser_Id(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
        ProfileCreationRequest pReq = profileMapper.toProfileCreationRequest(profile);
        MealPlanCreationRequest req =
                MealPlanCreationRequest.builder().userId(userId).profile(pReq).build();

        List<NutritionRule> rules = nutritionRuleService.getRuleByUserId(userId);

        TagDirectives tagDir = buildTagDirectives(rules, req);

        // 3) Pool món rộng (theo slot nếu có)
        final int CANDIDATE_LIMIT = Math.max(limit * 6, 200);
        final int MIN_KCAL = 20, MAX_KCAL = 2000, PIVOT = 500;

        List<Food> pool;
        if (slot != null) {
            pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                    slot.name(), MIN_KCAL, MAX_KCAL, PIVOT, CANDIDATE_LIMIT);
        } else {
            pool = new ArrayList<>();
            for (MealSlot s : MealSlot.values()) {
                pool.addAll(foodRepository.selectCandidatesBySlotAndKcalWindow(
                        s.name(), MIN_KCAL, MAX_KCAL, PIVOT, CANDIDATE_LIMIT / 4));
            }
        }
        if (pool == null) pool = Collections.emptyList();

        pool = pool.stream()
                .filter(f -> Collections.disjoint(tagsOf(f), tagDir.getAvoid()))
                .collect(Collectors.toCollection(ArrayList::new));

        List<Food> allowed = new ArrayList<>(pool.size());
        for (Food f : pool) {
            Nutrition base = f.getNutrition();
            if (base == null) continue;

            boolean pass = false;
            double portion = 1.0;
            Nutrition snap = scaleNutrition(base, portion);

            if (passesItemRules(rules, snap, req)) {
                pass = true;
            } else {
                var step = stepDown(portion);
                while (step.isPresent()) {
                    double p2 = step.getAsDouble();
                    Nutrition s2 = scaleNutrition(base, p2);
                    if (passesItemRules(rules, s2, req)) {
                        pass = true;
                        break;
                    }
                    step = stepDown(p2);
                }
            }
            if (pass) allowed.add(f);
        }
        LinkedHashMap<UUID, Food> dedup = new LinkedHashMap<>();
        for (Food f : allowed) dedup.putIfAbsent(f.getId(), f);
        List<Food> dsFood = dedup.values().stream().limit(limit).toList();
        return dsFood.stream().map(f -> foodMapper.toFoodResponse(f, cdnHelper)).collect(Collectors.toList());
    }

    /* ==================== Helpers cho smartSwap ==================== */

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
