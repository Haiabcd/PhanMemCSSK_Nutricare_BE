package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.PlanLogManualRequest;
import com.hn.nutricarebe.dto.request.SaveLogRequest;
import com.hn.nutricarebe.dto.response.LogResponse;
import com.hn.nutricarebe.dto.response.NutritionResponse;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.enums.LogSource;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.NutritionMapper;
import com.hn.nutricarebe.mapper.PlanLogMapper;
import com.hn.nutricarebe.repository.PlanLogIngredientRepository;
import com.hn.nutricarebe.repository.PlanLogRepository;
import com.hn.nutricarebe.repository.MealPlanItemRepository;
import com.hn.nutricarebe.service.PlanLogService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.hn.nutricarebe.helper.PlanLogHelper.aggregateActual;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlanLogServiceImpl implements PlanLogService {
    MealPlanItemRepository mealPlanItemRepository;
    PlanLogRepository logRepository;
    PlanLogIngredientRepository planLogIngredientRepository;
    NutritionMapper nutritionMapper;
    PlanLogMapper logMapper;

    @Override
    @Transactional
    public void savePlanLog(SaveLogRequest req) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());

        MealPlanItem item = mealPlanItemRepository.findById(req.getMealPlanItemId())
                .orElseThrow(() -> new AppException(ErrorCode.MEAL_PLAN_NOT_FOUND));

        if (item.getDay() == null || item.getDay().getUser() == null
                || !userId.equals(item.getDay().getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        Nutrition snap = item.getNutrition();
        PlanLog log = PlanLog.builder()
                .user(User.builder().id(userId).build())
                .date(item.getDay().getDate())
                .mealSlot(item.getMealSlot())
                .food(item.getFood())
                .servingSizeGram(BigDecimal.ZERO)
                .source(LogSource.PLAN)
                .planItem(item)
                .portion(item.getPortion())
                .actualNutrition(snap)
                .build();

        item.setUsed(true);
        mealPlanItemRepository.save(item);
        logRepository.save(log);
    }

    @Override
    @Transactional
    public List<LogResponse> getLog(LocalDate date, MealSlot mealSlot) {
        if (date == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());

        List<PlanLog> logs = logRepository.findByUser_IdAndDateAndMealSlot(userId, date, mealSlot);
        return logs.stream()
                .map(logMapper::toLogResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        PlanLog p = logRepository.findById(id).orElse(null);
        if(p == null || p.getUser() == null || !userId.equals(p.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (p != null && p.getPlanItem() != null) {
            MealPlanItem item = p.getPlanItem();
            item.setUsed(false);
            mealPlanItemRepository.save(item);
        }
        logRepository.deleteById(id);
    }

    @Override
    public NutritionResponse getNutritionLogByDate(LocalDate date) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());
        List<PlanLog> logs = logRepository.findByUser_IdAndDate(userId, date);
        return aggregateActual(logs);
    }



    @Override
    public void savePlanLog_Manual( PlanLogManualRequest req){
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());

        PlanLog log = PlanLog.builder()
                .user(User.builder().id(userId).build())
                .date(req.getDate())
                .mealSlot(req.getMealSlot())
                .food(req.getFoodId() != null ?  Food.builder().id(req.getFoodId()).build() : null)
                .nameFood(req.getNameFood())
                .servingSizeGram(BigDecimal.ZERO)
                .planItem(null)
                .source(LogSource.MANUAL)
                .portion(req.getConsumedServings())
                .actualNutrition(nutritionMapper.toNutrition(req.getTotalNutrition()))
                .build();

        log =  logRepository.save(log);
        if(req.getIngredients() != null){
            for(PlanLogManualRequest.IngredientEntryDTO ingredientDTO : req.getIngredients()){
                PlanLogIngredient pli = PlanLogIngredient.builder()
                        .planLog(log)
                        .ingredient(Ingredient.builder().id(ingredientDTO.getId()).build())
                        .quantity(ingredientDTO.getQty())
                        .build();
                planLogIngredientRepository.save(pli);
            }
        }
    }
}
