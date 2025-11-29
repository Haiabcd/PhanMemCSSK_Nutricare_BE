package com.hn.nutricarebe.service.impl;

import static com.hn.nutricarebe.helper.MealPlanHelper.*;
import static com.hn.nutricarebe.helper.PlanLogHelper.aggregateActual;
import static com.hn.nutricarebe.helper.PlanLogHelper.resolveActualOrFallback;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.hn.nutricarebe.dto.request.*;
import com.hn.nutricarebe.repository.FoodRepository;
import jakarta.transaction.Transactional;

import org.hibernate.Hibernate;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.hn.nutricarebe.dto.overview.FoodLogStatDto;
import com.hn.nutricarebe.dto.overview.TopUserDto;
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
    FoodRepository foodRepository;
    PlanLogRepository logRepository;
    PlanLogIngredientRepository planLogIngredientRepository;
    NutritionMapper nutritionMapper;
    PlanLogMapper logMapper;
    MealPlanDayService mealPlanDayService;
    MealPlanItemServiceImpl mealPlanItemService;
    CdnHelper cdnHelper;

    static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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

    @Transactional
    @Override
    public void saveSuggestion(SaveSuggestion req){
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());

        MealPlanItem itemOld = mealPlanItemRepository
                .findById(req.getItemId())
                .orElseThrow(() -> new AppException(ErrorCode.MEAL_PLAN_NOT_FOUND));

        if (itemOld.getDay() == null
                || itemOld.getDay().getUser() == null
                || !userId.equals(itemOld.getDay().getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Food newFood = foodRepository.findById(req.getNewFoodId())
                .orElseThrow(() -> new AppException(ErrorCode.FOOD_NOT_FOUND));
        // Cập nhật item theo món được chọn
        itemOld.setFood(newFood);
        itemOld.setPortion(req.getPortion());
        Nutrition snap = scaleNutrition(newFood.getNutrition(), safeDouble(req.getPortion()));
        itemOld.setNutrition(snap);
        itemOld.setUsed(true);
        // Ghi log
        PlanLog log = PlanLog.builder()
                .user(User.builder().id(userId).build())
                .date(itemOld.getDay().getDate())
                .mealSlot(itemOld.getMealSlot())
                .food(newFood)
                .servingSizeGram(BigDecimal.ZERO)
                .source(LogSource.PLAN)
                .nameFood(newFood.getName() != null ? newFood.getName() : null)
                .planItem(itemOld)
                .portion(req.getPortion())
                .actualNutrition(snap)
                .build();

        mealPlanItemRepository.save(itemOld);
        logRepository.save(log);
        mealPlanItemService.updateCache(userId, itemOld);

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

        // 1. Lưu PlanLog MANUAL
        PlanLog log = PlanLog.builder()
                .user(User.builder().id(userId).build())
                .date(req.getDate())
                .mealSlot(req.getMealSlot())
                .food(req.getFoodId() != null ? Food.builder().id(req.getFoodId()).build() : null)
                .nameFood(req.getNameFood())
                .servingSizeGram(BigDecimal.ZERO)
                .planItem(null)
                .source(LogSource.MANUAL)
                .portion(req.getConsumedServings())
                .actualNutrition(nutritionMapper.toNutrition(req.getTotalNutrition()))
                .build();
        log = logRepository.save(log);

        // 2. Lưu ingredients nếu có
        if (req.getIngredients() != null) {
            for (PlanLogManualRequest.IngredientEntryDTO ingredientDTO : req.getIngredients()) {
                PlanLogIngredient pli = PlanLogIngredient.builder()
                        .planLog(log)
                        .ingredient(Ingredient.builder().id(ingredientDTO.getId()).build())
                        .quantity(ingredientDTO.getQty())
                        .build();
                planLogIngredientRepository.save(pli);
            }
        }
        return buildKcalWarning(userId, req.getDate(), req.getMealSlot());
    }

    @Override
    public KcalWarningResponse savePlanLog_Scan(PlanLogScanRequest req) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());

        // 1. Lưu log SCAN
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
                        .ingredient(Ingredient.builder().id(ingredientDTO.getId()).build())
                        .quantity(ingredientDTO.getQty())
                        .build();
                planLogIngredientRepository.save(pli);
            }
        }
        return buildKcalWarning(userId, req.getDate(), req.getMealSlot());
    }

    @Transactional
    @Override
    public KcalWarningResponse updatePlanLog(PlanLogUpdateRequest req, UUID id) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());

        PlanLog logOld = logRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND_PLAN_LOG));

        if (logOld.getUser() == null || !userId.equals(logOld.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // ==== Cập nhật field đơn ====
        logOld.setMealSlot(req.getMealSlot());
        logOld.setFood(req.getFoodId() != null ? Food.builder().id(req.getFoodId()).build() : null);
        logOld.setNameFood(req.getNameFood());
        logOld.setPortion(req.getConsumedServings());
        logOld.setActualNutrition(nutritionMapper.toNutrition(req.getTotalNutrition()));
        // Lazy-init collection
        Hibernate.initialize(logOld.getIngredients());
        // Xóa các ingredient cũ
        for (PlanLogIngredient child : new HashSet<>(logOld.getIngredients())) {
            child.setPlanLog(null);
        }
        logOld.getIngredients().clear();
        logRepository.flush();
        // Thêm ingredient mới
        if (req.getIngredients() != null) {
            for (PlanLogManualRequest.IngredientEntryDTO dto : req.getIngredients()) {
                PlanLogIngredient pli = PlanLogIngredient.builder()
                        .ingredient(Ingredient.builder().id(dto.getId()).build())
                        .quantity(dto.getQty())
                        .build();
                pli.setPlanLog(logOld);
                logOld.getIngredients().add(pli);
            }
        }
        logRepository.saveAndFlush(logOld);
        return buildKcalWarning(userId, logOld.getDate(), req.getMealSlot());
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
        for (MealSlot slot : MealSlot.values()) {
            result.put(slot.name(), 0L);
        }
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


    private double computeTargetKcalForSlot(UUID userId, LocalDate date, MealSlot slot) {
        // Ưu tiên dùng tổng kcal của các món trong plan của bữa đó
        List<MealPlanItem> itemsOfDay =
                mealPlanItemRepository.findByDay_User_IdAndDay_Date(userId, date);

        double plannedKcalSlot = itemsOfDay.stream()
                .filter(i -> i.getMealSlot() == slot)
                .map(MealPlanItem::getNutrition)
                .filter(Objects::nonNull)
                .map(Nutrition::getKcal)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();

        // Nếu có plan → lấy plannedKcalSlot, nếu không → fallback sang target lý thuyết
        return plannedKcalSlot > 0
                ? plannedKcalSlot
                : mealPlanDayService.getMealTargetKcal(userId, slot);
    }

    private double computeActualKcalForSlot(UUID userId, LocalDate date, MealSlot slot) {
        List<PlanLog> logAllDay = logRepository.findByUser_IdAndDate(userId, date);
        List<PlanLog> logsThisSlot = logAllDay.stream()
                .filter(l -> l.getMealSlot() == slot)
                .toList();

        NutritionResponse nr = aggregateActual(logsThisSlot);
        return nr.getKcal() != null ? nr.getKcal().doubleValue() : 0.0;
    }


    private KcalWarningResponse buildKcalWarning(UUID userId, LocalDate date, MealSlot slot) {
        LocalDate today = LocalDate.now(VN_ZONE);
        boolean nowDate = date.isEqual(today);

        double targetKcal = computeTargetKcalForSlot(userId, date, slot);
        double actualKcal = computeActualKcalForSlot(userId, date, slot);

        double diff = actualKcal - targetKcal;
        KcalWarningResponse.Status status;

        // Dùng đúng tolerance KCAL_MIN_RATIO / KCAL_MAX_RATIO từ MealPlanHelper
        if (isWithinRatio(actualKcal, targetKcal, KCAL_MIN_RATIO, KCAL_MAX_RATIO)) {
            status = KcalWarningResponse.Status.OK;
        } else if (actualKcal > targetKcal) {
            if (nowDate) {
                mealPlanDayService.updatePlanForOneDay(date, userId);
            }
            status = KcalWarningResponse.Status.OVER;
        } else {
            if (nowDate) {
                mealPlanDayService.updatePlanForOneDay(date, userId);
            }
            status = KcalWarningResponse.Status.UNDER;
        }
        return KcalWarningResponse.builder()
                .mealSlot(slot.name())
                .targetKcal(targetKcal)
                .actualKcal(actualKcal)
                .diff(diff)
                .status(status)
                .build();
    }

}
