package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.MealPlanItemCreationRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.dto.response.MealPlanItemResponse;
import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.entity.MealPlanItem;
import com.hn.nutricarebe.entity.Nutrition;
import com.hn.nutricarebe.enums.FoodTag;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.CdnHelper;
import com.hn.nutricarebe.mapper.FoodMapper;
import com.hn.nutricarebe.repository.MealPlanItemRepository;
import com.hn.nutricarebe.service.MealPlanItemService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MealPlanItemServiceImpl implements MealPlanItemService {
    FoodMapper foodMapper;
    CdnHelper cdnHelper;
    MealPlanItemRepository mealPlanItemRepository;

    @Override
    public MealPlanItemResponse createMealPlanItems(MealPlanItemCreationRequest request) {
        return null;
    }

    @Override
    public Page<FoodResponse> getUpcomingFoods(int page, int size) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());
        LocalDate today = LocalDate.now();
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<Food> foods = mealPlanItemRepository.findFoodsFromDate(userId, today, pageable);
        return foods.map(f -> foodMapper.toFoodResponse(f, cdnHelper));
    }

//    @Override
//    public void smartSwapMealItem(UUID itemId) {
//        var auth = SecurityContextHolder.getContext().getAuthentication();
//        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
//        UUID userId = UUID.fromString(auth.getName());
//
//        MealPlanItem item = mealPlanItemRepository.findById(itemId)
//                .orElseThrow(() -> new AppException(ErrorCode.MEAL_PLAN_NOT_FOUND));
//
//        // Quyền sở hữu
//        if (item.getDay() == null || item.getDay().getUser() == null
//                || !userId.equals(item.getDay().getUser().getId())) {
//            throw new AppException(ErrorCode.UNAUTHORIZED);
//        }
//
//        MealSlot slot  = item.getMealSlot();
//        Food     cur   = item.getFood();
//        Set<FoodTag> targetTags = cur.getTags() == null ? Set.of() : cur.getTags();
//
//        // Snapshot đích (nutrition × portion cũ)
//        Nutrition targetSnap = item.getNutrition();
//        BigDecimal curPortion = item.getPortion() == null ? BigDecimal.ONE : item.getPortion();
//
//        // 1) Lấy pool theo slot (phân trang)
//        var page = PageRequest.of(0, 300);
//        List<Food> pool = foodRepository.findByMealSlotsContains(slot, page).getContent();
//
//        // 2) Lọc chính món hiện tại + chỉ giữ món có tags "giống hệt"
//        UUID curId = cur.getId();
//        pool.removeIf(f -> Objects.equals(f.getId(), curId));
//        pool.removeIf(f -> !tagsEqual(f.getTags(), targetTags));
//
//        if (pool.isEmpty()) {
//            throw new AppException(ErrorCode.FOOD_NOT_FOUND);
//        }
//
//        // 3) Duyệt pool: chọn món + portion sao cho nutrition×portion gần targetSnap nhất
//        Food bestFood = null;
//        double bestPortion = 1.0;
//        double bestLoss = Double.POSITIVE_INFINITY;
//
//        for (Food cand : pool) {
//            Nutrition base = cand.getNutrition();
//            if (base == null || base.getKcal() == null || base.getKcal().doubleValue() <= 0) continue;
//
//            // Ước lượng portion từ kcal (liên tục), rồi clamp + snap nhẹ (tùy chọn)
//            double baseKcal   = safeDouble(base.getKcal());
//            double targetKcal = safeDouble(targetSnap.getKcal());   // snapshot cũ (đã nhân portion)
//            double portionEst = targetKcal / Math.max(1e-6, baseKcal);
//
//            // Giới hạn portion trong [0.5, 1.5] (tuỳ bạn)
//            double portion = clamp(portionEst, 0.5, 1.5);
//
//            // (Tuỳ chọn) snap về các bậc dễ hiểu
//            portion = snapPortion(portion); // dùng {0.5, 0.75, 1.0, 1.25, 1.5}
//
//            // Tính nutrition sau khi scale
//            Nutrition scaled = scaleNutrition(base, portion);
//
//            // Đo “độ gần” với snapshot cũ (khoảng cách weighted L2)
//            double loss = nutritionDistance(scaled, targetSnap);
//
//            if (loss < bestLoss) {
//                bestLoss = loss;
//                bestFood = cand;
//                bestPortion = portion;
//            }
//        }
//
//        if (bestFood == null) {
//            throw new AppException(ErrorCode.DATA_NOT_FOUND, "Không tìm thấy ứng viên phù hợp dinh dưỡng.");
//        }
//
//        // 4) Cập nhật lại item
//        Nutrition newSnap = scaleNutrition(bestFood.getNutrition(), bestPortion);
//        item.setFood(bestFood);
//        item.setPortion(bd(bestPortion, 2));
//        item.setNutrition(newSnap);
//
//        mealPlanItemRepository.save(item);
//    }
}
