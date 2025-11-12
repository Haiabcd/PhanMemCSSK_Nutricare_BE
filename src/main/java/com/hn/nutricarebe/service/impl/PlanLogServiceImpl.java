package com.hn.nutricarebe.service.impl;

import static com.hn.nutricarebe.helper.MealPlanHelper.addNut;
import static com.hn.nutricarebe.helper.PlanLogHelper.aggregateActual;
import static com.hn.nutricarebe.helper.PlanLogHelper.resolveActualOrFallback;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.transaction.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.hn.nutricarebe.dto.overview.FoodLogStatDto;
import com.hn.nutricarebe.dto.overview.TopUserDto;
import com.hn.nutricarebe.dto.request.PlanLogManualRequest;
import com.hn.nutricarebe.dto.request.PlanLogScanRequest;
import com.hn.nutricarebe.dto.request.PlanLogUpdateRequest;
import com.hn.nutricarebe.dto.request.SaveLogRequest;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.enums.LogSource;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.CdnHelper;
import com.hn.nutricarebe.mapper.NutritionMapper;
import com.hn.nutricarebe.mapper.PlanLogMapper;
import com.hn.nutricarebe.repository.MealPlanItemRepository;
import com.hn.nutricarebe.repository.PlanLogIngredientRepository;
import com.hn.nutricarebe.repository.PlanLogRepository;
import com.hn.nutricarebe.service.MealPlanDayService;
import com.hn.nutricarebe.service.PlanLogService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

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
    MealPlanDayService mealPlanDayService;
    CdnHelper cdnHelper;

    @Override
    @Transactional
    public void savePlanLog(SaveLogRequest req) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());

        MealPlanItem item = mealPlanItemRepository
                .findById(req.getMealPlanItemId())
                .orElseThrow(() -> new AppException(ErrorCode.MEAL_PLAN_NOT_FOUND));

        if (item.getDay() == null
                || item.getDay().getUser() == null
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
                .nameFood(item.getFood().getName() != null ? item.getFood().getName() : null)
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

        return logs.stream().map(log -> logMapper.toLogResponse(log, cdnHelper)).toList();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        PlanLog p = logRepository.findById(id).orElse(null);
        if (p == null || p.getUser() == null || !userId.equals(p.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (p.getPlanItem() != null) {
            MealPlanItem item = p.getPlanItem();
            item.setUsed(false);
            mealPlanItemRepository.save(item);
            logRepository.deleteById(id);
        } else {
            logRepository.deleteById(id);
            mealPlanDayService.updatePlanForOneDay(p.getDate(), userId);
        }
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
    public KcalWarningResponse savePlanLog_Manual(PlanLogManualRequest req) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());
        PlanLog log = PlanLog.builder()
                .user(User.builder().id(userId).build())
                .date(req.getDate())
                .mealSlot(req.getMealSlot())
                .food(
                        req.getFoodId() != null
                                ? Food.builder().id(req.getFoodId()).build()
                                : null)
                .nameFood(req.getNameFood())
                .servingSizeGram(BigDecimal.ZERO)
                .planItem(null)
                .source(LogSource.MANUAL)
                .portion(req.getConsumedServings())
                .actualNutrition(nutritionMapper.toNutrition(req.getTotalNutrition()))
                .build();
        log = logRepository.save(log);
        if (req.getIngredients() != null) {
            for (PlanLogManualRequest.IngredientEntryDTO ingredientDTO : req.getIngredients()) {
                PlanLogIngredient pli = PlanLogIngredient.builder()
                        .planLog(log)
                        .ingredient(
                                Ingredient.builder().id(ingredientDTO.getId()).build())
                        .quantity(ingredientDTO.getQty())
                        .build();
                planLogIngredientRepository.save(pli);
            }
        }
        boolean nowDate = req.getDate().isEqual(LocalDate.now());

        // Lấy kcal mục tiêu
        double targetKcal = mealPlanDayService.getMealTargetKcal(userId, req.getMealSlot());

        List<PlanLog> logAllDay = logRepository.findByUser_IdAndDate(userId, req.getDate());
        List<PlanLog> logsThisSlot = logAllDay.stream()
                .filter(l -> l.getMealSlot() == req.getMealSlot())
                .toList();
        NutritionResponse nr = aggregateActual(logsThisSlot);
        double actualKcal = nr.getKcal() != null ? nr.getKcal().doubleValue() : 0.0;
        // 5. So sánh và trả về cảnh báo
        double diff = actualKcal - targetKcal;
        KcalWarningResponse.Status status;
        if (diff > 50) {
            if (nowDate) {
                mealPlanDayService.updatePlanForOneDay(req.getDate(), userId);
            }
            status = KcalWarningResponse.Status.OVER;
        } else if (diff < -50) {
            if (nowDate) {
                mealPlanDayService.updatePlanForOneDay(req.getDate(), userId);
            }
            status = KcalWarningResponse.Status.UNDER;
        } else {
            status = KcalWarningResponse.Status.OK;
        }

        return KcalWarningResponse.builder()
                .mealSlot(req.getMealSlot().name())
                .targetKcal(targetKcal)
                .actualKcal(actualKcal)
                .diff(diff)
                .status(status)
                .build();
    }

    @Override
    public KcalWarningResponse savePlanLog_Scan(PlanLogScanRequest req) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());
        PlanLog log = PlanLog.builder()
                .user(User.builder().id(userId).build())
                .date(req.getDate())
                .mealSlot(req.getMealSlot())
                .food(null)
                .nameFood(req.getNameFood())
                .servingSizeGram(BigDecimal.ZERO)
                .planItem(null)
                .source(LogSource.SCAN)
                .portion(req.getConsumedServings())
                .actualNutrition(nutritionMapper.toNutrition(req.getTotalNutrition()))
                .build();
        log = logRepository.save(log);
        if (req.getIngredients() != null) {
            for (PlanLogScanRequest.IngredientEntryDTO ingredientDTO : req.getIngredients()) {
                PlanLogIngredient pli = PlanLogIngredient.builder()
                        .planLog(log)
                        .ingredient(
                                Ingredient.builder().id(ingredientDTO.getId()).build())
                        .quantity(ingredientDTO.getQty())
                        .build();
                planLogIngredientRepository.save(pli);
            }
        }
        // Lấy kcal mục tiêu
        double targetKcal = mealPlanDayService.getMealTargetKcal(userId, req.getMealSlot());

        List<PlanLog> logAllDay = logRepository.findByUser_IdAndDate(userId, req.getDate());
        List<PlanLog> logsThisSlot = logAllDay.stream()
                .filter(l -> l.getMealSlot() == req.getMealSlot())
                .toList();
        NutritionResponse nr = aggregateActual(logsThisSlot);
        double actualKcal = nr.getKcal() != null ? nr.getKcal().doubleValue() : 0.0;
        // 5. So sánh và trả về cảnh báo
        double diff = actualKcal - targetKcal;
        KcalWarningResponse.Status status;
        if (diff > 50) {
            mealPlanDayService.updatePlanForOneDay(req.getDate(), userId);
            status = KcalWarningResponse.Status.OVER;
        } else if (diff < -50) {
            mealPlanDayService.updatePlanForOneDay(req.getDate(), userId);
            status = KcalWarningResponse.Status.UNDER;
        } else {
            status = KcalWarningResponse.Status.OK;
        }
        return KcalWarningResponse.builder()
                .mealSlot(req.getMealSlot().name())
                .targetKcal(targetKcal)
                .actualKcal(actualKcal)
                .diff(diff)
                .status(status)
                .build();
    }

    @Transactional
    @Override
    public KcalWarningResponse updatePlanLog(PlanLogUpdateRequest req, UUID id) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());

        PlanLog logOld = logRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND_PLAN_LOG));

        if (logOld.getUser() == null || !userId.equals(logOld.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // ==== Cập nhật field đơn ====
        logOld.setMealSlot(req.getMealSlot());
        logOld.setFood(
                req.getFoodId() != null ? Food.builder().id(req.getFoodId()).build() : null);
        logOld.setNameFood(req.getNameFood());
        logOld.setPortion(req.getConsumedServings());
        logOld.setActualNutrition(nutritionMapper.toNutrition(req.getTotalNutrition()));

        logOld.getIngredients().size();

        for (PlanLogIngredient child : new java.util.HashSet<>(logOld.getIngredients())) {
            child.setPlanLog(null);
        }
        logOld.getIngredients().clear();
        logRepository.flush();

        // ==== Thêm ingredient mới ====
        if (req.getIngredients() != null) {
            for (PlanLogManualRequest.IngredientEntryDTO dto : req.getIngredients()) {
                PlanLogIngredient pli = PlanLogIngredient.builder()
                        .ingredient(Ingredient.builder().id(dto.getId()).build())
                        .quantity(dto.getQty()) // BigDecimal
                        .build();
                pli.setPlanLog(logOld);
                logOld.getIngredients().add(pli);
            }
        }
        logRepository.saveAndFlush(logOld);

        // Lấy kcal mục tiêu theo bữa
        double targetKcal = mealPlanDayService.getMealTargetKcal(userId, req.getMealSlot());
        boolean nowDate = logOld.getDate().isEqual(LocalDate.now());
        List<PlanLog> logAllDay = logRepository.findByUser_IdAndDate(userId, logOld.getDate());
        List<PlanLog> logsThisSlot = logAllDay.stream()
                .filter(l -> l.getMealSlot() == req.getMealSlot())
                .toList();
        NutritionResponse nr = aggregateActual(logsThisSlot);
        double actualKcal = nr.getKcal() != null ? nr.getKcal().doubleValue() : 0.0;
        double diff = actualKcal - targetKcal;
        KcalWarningResponse.Status status;
        if (diff > 50) {
            if (nowDate) {
                mealPlanDayService.updatePlanForOneDay(logOld.getDate(), userId);
            }
            status = KcalWarningResponse.Status.OVER;
        } else if (diff < -50) {
            if (nowDate) {
                mealPlanDayService.updatePlanForOneDay(logOld.getDate(), userId);
            }
            status = KcalWarningResponse.Status.UNDER;
        } else {
            status = KcalWarningResponse.Status.OK;
        }

        return KcalWarningResponse.builder()
                .mealSlot(req.getMealSlot().name())
                .targetKcal(targetKcal)
                .actualKcal(actualKcal)
                .diff(diff)
                .status(status)
                .build();
    }

    @Override
    public List<TopFoodDto> getTopFoods(UUID userId, LocalDate start, LocalDate end, int limit) {
        return logRepository.findTopFoodsOfUserBetween(userId, start, end, PageRequest.of(0, limit));
    }

    @Override
    public List<DailyNutritionDto> getDailyNutrition(
            UUID userId, LocalDate start, LocalDate end, boolean fillMissingDays) {
        List<DailyNutritionDto> rows = logRepository.sumDailyNutritionByDateBetween(userId, start, end);

        if (!fillMissingDays) return rows;
        // Map theo ngày để fill ngày trống = 0
        Map<LocalDate, DailyNutritionDto> byDate =
                rows.stream().collect(Collectors.toMap(DailyNutritionDto::getDate, x -> x));
        List<DailyNutritionDto> filled = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DailyNutritionDto dto = byDate.get(d);
            if (dto == null) {
                dto = new DailyNutritionDto(d, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            filled.add(dto);
        }
        return filled;
    }

    @Override
    @Transactional
    public Map<MealSlot, Map<String, Long>> getMealSlotSummary(UUID userId, LocalDate start, LocalDate end) {

        long totalDays = ChronoUnit.DAYS.between(start, end) + 1;

        // Lấy các cặp (date, slot) đã có log
        List<DateSlotProjection> logs = logRepository.findDistinctDateAndSlotByUserAndDateBetween(userId, start, end);

        // Gom theo slot
        Map<MealSlot, Set<LocalDate>> slotToDates = new EnumMap<>(MealSlot.class);
        for (MealSlot slot : MealSlot.values()) {
            slotToDates.put(slot, new HashSet<>());
        }
        for (DateSlotProjection row : logs) {
            slotToDates.get(row.getMealSlot()).add(row.getDate());
        }

        // Kết quả: Map<MealSlot, {loggedDays, missedDays}>
        return Arrays.stream(MealSlot.values())
                .collect(Collectors.toMap(
                        slot -> slot,
                        slot -> {
                            long logged = slotToDates.get(slot).size();
                            long missed = Math.max(0, totalDays - logged);
                            Map<String, Long> stat = new LinkedHashMap<>();
                            stat.put("loggedDays", logged);
                            stat.put("missedDays", missed);
                            stat.put("totalDays", totalDays);
                            return stat;
                        },
                        (a, b) -> a,
                        () -> new EnumMap<>(MealSlot.class)));
    }

    @Override
    @Transactional
    public List<DayConsumedTotal> getConsumedTotalsBetween(LocalDate from, LocalDate to, UUID userId) {
        List<PlanLog> logs = logRepository.findByUser_IdAndDateBetween(userId, from, to);
        if (logs.isEmpty()) return List.of();

        Map<LocalDate, Nutrition> totalByDate = new TreeMap<>();
        for (PlanLog l : logs) {
            LocalDate d = l.getDate();
            Nutrition add = resolveActualOrFallback(l);
            if (add == null) continue;
            totalByDate.compute(d, (key, cur) -> (cur == null) ? add : addNut(cur, add));
        }
        return totalByDate.entrySet().stream()
                .map(e -> new DayConsumedTotal(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public Map<String, Long> getCountBySource() {
        long manual = logRepository.countBySource(LogSource.MANUAL);
        long scan = logRepository.countBySource(LogSource.SCAN);

        return Map.of(
                "manual", manual,
                "scan", scan);
    }

    @Override
    public Map<String, Long> getPlanLogCountByMealSlot() {
        List<Object[]> rows = logRepository.countByMealSlotAndSource(LogSource.PLAN);

        Map<String, Long> result = new LinkedHashMap<>();
        // Khởi tạo mặc định 0 cho các bữa
        for (MealSlot slot : MealSlot.values()) {
            result.put(slot.name(), 0L);
        }

        // Ghi đè giá trị có trong DB
        for (Object[] row : rows) {
            MealSlot slot = (MealSlot) row[0];
            Long count = (Long) row[1];
            result.put(slot.name(), count);
        }

        return result;
    }

    // Đếm tổng số log có nguồn từ PLAN
    @Override
    public long countLogsFromPlanSource(LogSource source) {
        return logRepository.countBySource(source);
    }

    // Lấy top 10 món ăn được ghi log nhiều nhất từ nguồn PLAN
    @Override
    public List<FoodLogStatDto> getTop10FoodsFromPlan() {
        return logRepository.findTopFoodsBySource(LogSource.PLAN).stream()
                .map(r -> new FoodLogStatDto((String) r[0], ((Number) r[1]).longValue()))
                .limit(10)
                .collect(Collectors.toList());
    }

    // Lấy top 15 người dùng có số log nhiều nhất
    @Override
    public List<TopUserDto> getTopUsersByLogCount() {
        return logRepository.findTopUsersByLogCount().stream()
                .limit(10)
                .map(row -> new TopUserDto((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }
}
