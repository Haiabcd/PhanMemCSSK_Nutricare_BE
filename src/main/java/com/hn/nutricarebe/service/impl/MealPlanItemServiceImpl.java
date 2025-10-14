package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.MealPlanItemCreationRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.dto.response.MealPlanItemResponse;
import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.entity.MealPlanItem;
import com.hn.nutricarebe.entity.Nutrition;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.helper.MealPlanHelper;
import com.hn.nutricarebe.mapper.CdnHelper;
import com.hn.nutricarebe.mapper.FoodMapper;
import com.hn.nutricarebe.repository.FoodRepository;
import com.hn.nutricarebe.repository.MealPlanItemRepository;
import com.hn.nutricarebe.service.MealPlanItemService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;

import static com.hn.nutricarebe.helper.MealPlanHelper.*;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MealPlanItemServiceImpl implements MealPlanItemService {
    FoodMapper foodMapper;
    CdnHelper cdnHelper;
    MealPlanItemRepository mealPlanItemRepository;
    FoodRepository foodRepository;

    @Override
    public MealPlanItemResponse createMealPlanItems(MealPlanItemCreationRequest request) {
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<FoodResponse> getUpcomingFoods(int page, int size) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());
        LocalDate today = LocalDate.now();
        // Sort đã viết trong JPQL -> không cần sort ở đây
        Pageable pageable = PageRequest.of(page, size);
        Slice<Food> foods = mealPlanItemRepository.findFoodsFromDate(userId, today, pageable);
        return foods.map(f -> foodMapper.toFoodResponse(f, cdnHelper));
    }


    @Override
    @Transactional
    public void smartSwapMealItem(UUID itemId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());

        MealPlanItem item = mealPlanItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MEAL_PLAN_NOT_FOUND));

        // Quyền sở hữu item
        if (item.getDay() == null || item.getDay().getUser() == null
                || !userId.equals(item.getDay().getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Dữ liệu tham chiếu cũ
        MealSlot slot = item.getMealSlot();
        var oldFood = item.getFood();
        var oldTags = nonNullTags(oldFood.getTags());
        var oldSnap = item.getNutrition();
        var oldFoodId = oldFood.getId();

        // Lướt phân trang ứng viên theo slot
        Pageable pageable = PageRequest.of(0, 50);

        CandidateBest best = null;  //Ứng viên tốt nhất tìm được
        boolean hasNext;  //Theo dõi còn trang tiếp theo không
        do {
            var slice = foodRepository.findByMealSlot(slot, pageable);
            for (var cand : slice.getContent()) {
                if (cand.getId().equals(oldFoodId)) continue;
                if (!tagsEqual(nonNullTags(cand.getTags()), oldTags)) continue;

                var n = cand.getNutrition();

                // Tìm portion thuộc {1.5, 1.0, 0.5}
                PortionScore ps = bestPortionAgainstTarget(n, oldSnap);
                if (ps == null) continue;

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

    /* ==================== Helpers cho smartSwap ==================== */

    // Chuẩn hoá set tags != null
    private static java.util.Set<com.hn.nutricarebe.enums.FoodTag> nonNullTags(java.util.Set<com.hn.nutricarebe.enums.FoodTag> in) {
        return (in == null) ? java.util.Set.of() : in;
    }

    // So sánh "y chang" tập tags (không hơn không kém)
    private static boolean tagsEqual(
            java.util.Set<com.hn.nutricarebe.enums.FoodTag> a,
            java.util.Set<com.hn.nutricarebe.enums.FoodTag> b
    ) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        return a.containsAll(b) && b.containsAll(a);
    }

    // Chọn khẩu phần tốt nhất  + điểm chênh lệch so với mục tiêu
    private PortionScore bestPortionAgainstTarget(Nutrition cand, Nutrition target) {
        double[] steps = PORTION_STEPS;
        double bestDist = Double.POSITIVE_INFINITY;
        double bestStep = 1.0;

        for (double step : steps) {
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
        double dk = Math.abs(safeDouble(a.getKcal())     - safeDouble(b.getKcal()));
        double dp = Math.abs(safeDouble(a.getProteinG()) - safeDouble(b.getProteinG()));
        double dc = Math.abs(safeDouble(a.getCarbG())    - safeDouble(b.getCarbG()));
        double df = Math.abs(safeDouble(a.getFatG())     - safeDouble(b.getFatG()));
        return dk * 1.0 + dp * 0.4 + dc * 0.3 + df * 0.3;
    }

    private boolean isGoodEnough(double score, Nutrition target) {
        double kcal = Math.max(1.0, safeDouble(target.getKcal()));
        return score < (0.05 * kcal + 20.0);
    }

    private static class PortionScore {
        final double portion;
        final double distance;
        PortionScore(double portion, double distance) { this.portion = portion; this.distance = distance; }
    }

    private static class CandidateBest {
        final Food food;
        final double portion;
        final double score;

        CandidateBest(Food food, double portion, double score) {
            this.food = food; this.portion = portion; this.score = score;
        }
    }

}
