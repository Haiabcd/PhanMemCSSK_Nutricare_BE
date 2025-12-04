package com.hn.nutricarebe.service.impl;

import static com.hn.nutricarebe.helper.MealPlanHelper.*;
import static com.hn.nutricarebe.helper.PlanLogHelper.resolveActualOrFallback;
import static java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR;
import java.time.LocalDate;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.hn.nutricarebe.dto.TagDirectives;
import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.DayTarget;
import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.enums.*;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.CdnHelper;
import com.hn.nutricarebe.mapper.MealPlanDayMapper;
import com.hn.nutricarebe.orchestrator.ProfileOrchestrator;
import com.hn.nutricarebe.repository.*;
import com.hn.nutricarebe.service.MealPlanDayService;
import com.hn.nutricarebe.service.NutritionRuleService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MealPlanDayServiceImpl implements MealPlanDayService {
    private static final double WATER_ML_PER_KG = 35.0;
    MealPlanDayRepository mealPlanDayRepository;
    MealPlanDayMapper mealPlanDayMapper;
    ProfileOrchestrator profileOrchestrator;
    NutritionRuleService nutritionRuleService;
    FoodRepository foodRepository;
    MealPlanItemRepository mealPlanItemRepository;
    PlanLogRepository planLogRepository;
    CdnHelper cdnHelper;
    @PersistenceContext
    EntityManager entityManager;

    /**
     * Tạo mới kế hoạch ăn cho {@code number} ngày liên tiếp tính từ hôm nay cho user trong request.
     * Bao gồm sinh ngày, chọn món cho từng bữa và tinh chỉnh cuối ngày.
     */
    @Override
    @Transactional
    public void createPlan(MealPlanCreationRequest request, int number) {
        // 1) Xây dựng context chung cho cả ngày (target dinh dưỡng + nước + rule)
        DayPlanContext ctx = buildDayPlanContext(request);
        UUID userId = request.getUserId();
        Nutrition target = ctx.dayTarget();
        double waterMl = ctx.waterTargetMl();
        int weight = ctx.weight();
        List<NutritionRule> rules = ctx.rules();

        // 2) Khởi tạo entity MealPlanDay cho số ngày yêu cầu, bắt đầu từ hôm nay
        LocalDate startDate = LocalDate.now();
        List<MealPlanDay> days = new ArrayList<>(number);
        User user = User.builder().id(userId).build();

        for (int i = 0; i < number; i++) {
            LocalDate d = startDate.plusDays(i);
            days.add(MealPlanDay.builder()
                    .user(user)
                    .targetNutrition(target)
                    .date(d)
                    .waterTargetMl((int) Math.round(waterMl))
                    .build());
        }
        List<MealPlanDay> planDay = mealPlanDayRepository.saveAll(days);

        // 3) Thông số chung cho việc ghép món trong ngày
        int totalItemsPerDay =
                SLOT_ITEM_COUNTS.values().stream().mapToInt(Integer::intValue).sum();
        final int CANDIDATE_LIMIT = 80;
        final int noRepeatWindow = 3; // Tránh lặp món trong 3 ngày gần nhất

        // Pool món theo từng slot (bữa ăn) + baseScore của từng món
        record SlotPool(List<Food> foods, Map<UUID, Double> baseScore) {}
        // Danh sách món theo từng bữa (BREAKFAST / LUNCH / DINNER / SNACK)
        Map<MealSlot, SlotPool> pools = new EnumMap<>(MealSlot.class);

        // Seed cố định theo user + tuần trong năm để vừa ngẫu nhiên vừa ổn định
        final long seed = Objects.hash(userId, LocalDate.now().get(WEEK_OF_WEEK_BASED_YEAR));
        Random rng = new Random(seed);

        double dayTargetKcal = safeDouble(target.getKcal());
        // 5) Tổng hợp tag avoid / prefer / limit từ rule & request
        TagDirectives globalTagDir = buildTagDirectives(rules, request);

        for (MealSlot slot : MealSlot.values()) {
            double slotKcal = dayTargetKcal * SLOT_KCAL_PCT.get(slot);
            int itemCount = SLOT_ITEM_COUNTS.get(slot);
            int perItem = (int) Math.round(slotKcal / itemCount);

            // 6) Tìm pool món theo từng bữa với khoảng kcal tương đối hợp lý
            List<Food> pool = new ArrayList<>();
            // ban đầu chọn món trong khoảng 50% - 200% kcal/món; thiếu thì mở rộng thêm
            double lowMul = 0.5, highMul = 2.0;
            for (int attempt = 0; attempt < 5; attempt++) {
                int minKcal = Math.max(20, (int) Math.round(perItem * lowMul));
                int maxKcal = Math.max(minKcal + 10, (int) Math.round(perItem * highMul));
                pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                        slot.name(), minKcal, maxKcal, perItem, CANDIDATE_LIMIT);
                if (pool != null && pool.size() >= itemCount) break;
                // Nếu chưa đủ món: mở rộng cửa sổ kcal
                lowMul *= 0.7;
                highMul *= 1.3;
            }
            if (pool == null) pool = Collections.emptyList();

            // 7) Lọc bỏ các món có tag thuộc nhóm cần tránh (avoid)
            pool = pool.stream()
                    .filter(f -> Collections.disjoint(tagsOf(f), globalTagDir.getAvoid()))
                    .collect(Collectors.toCollection(ArrayList::new));

            // 8) Tính target dinh dưỡng cho bữa hiện tại
            Nutrition slotTarget = approxMacroTargetForMeal(target, SLOT_KCAL_PCT.get(slot), rules, weight, request);

            // 9) Tính điểm heuristic cho từng món trong pool (đồng thời cộng thêm điểm tag prefer, trừ điểm tag limit)
            Map<UUID, Double> score = new HashMap<>();
            for (Food f : pool) {
                double s = scoreFoodHeuristic(f, slotTarget);
                // Cộng điểm theo số tag thuộc nhóm prefer
                if (!globalTagDir.getPreferBonus().isEmpty()) {
                    long cnt = tagsOf(f).stream()
                            .filter(globalTagDir.getPreferBonus()::containsKey)
                            .count();
                    s += cnt * 0.8;
                }
                // Trừ điểm theo số tag thuộc nhóm limit
                if (!globalTagDir.getLimitPenalty().isEmpty()) {
                    long cnt = tagsOf(f).stream()
                            .filter(globalTagDir.getLimitPenalty()::containsKey)
                            .count();
                    s -= cnt * 0.7;
                }
                score.put(f.getId(), s);
            }

            // 10) Sắp xếp pool theo điểm (cao → thấp) rồi xáo trộn nhẹ để đa dạng món
            pool.sort(Comparator.<Food>comparingDouble(f -> score.getOrDefault(f.getId(), 0.0))
                    .reversed());
            // Cứ mỗi block 5 món thì random thứ tự trong block
            for (int i = 0; i + 4 < pool.size(); i += 5) {
                Collections.shuffle(pool.subList(i, i + 5), rng);
            }
            pools.put(slot, new SlotPool(pool, score));
        }

        // 11) Queue lưu các món đã dùng gần đây (nhiều ngày) để tránh lặp lại
        Deque<UUID> recentAll = new ArrayDeque<>();
        List<MealPlanItem> allItems = new ArrayList<>();

        for (MealPlanDay day : planDay) {
            int rank = 1;

            for (MealSlot slot : MealSlot.values()) {
                double pct = SLOT_KCAL_PCT.get(slot);
                int itemCount = SLOT_ITEM_COUNTS.get(slot);

                SlotPool sp = pools.get(slot);
                List<Food> pool = sp.foods();
                if (pool.isEmpty()) continue;

                Nutrition slotTarget = approxMacroTargetForMeal(target, pct, rules, weight, request);

                // Dinh dưỡng còn thiếu cho bữa (sau mỗi lần chọn món sẽ trừ dần)
                Nutrition remaining = Nutrition.builder()
                        .kcal(bd(safeDouble(slotTarget.getKcal()), 2))
                        .proteinG(bd(safeDouble(slotTarget.getProteinG()), 2))
                        .carbG(bd(safeDouble(slotTarget.getCarbG()), 2))
                        .fatG(bd(safeDouble(slotTarget.getFatG()), 2))
                        .fiberG(bd(safeDouble(slotTarget.getFiberG()), 2))
                        .sodiumMg(bd(safeDouble(slotTarget.getSodiumMg()), 2))
                        .sugarMg(bd(safeDouble(slotTarget.getSugarMg()), 2))
                        .build();

                int picked = 0;                 // Số món đã chọn cho slot hiện tại
                Set<UUID> usedThisSlot = new HashSet<>(); // Món đã dùng trong slot này (tránh trùng trong cùng bữa)
                int scanGuard = 0;              // Ngăn vòng lặp vô hạn khi scan pool

                // 12) Vòng chọn món greedy: mỗi lần chọn món giúp giảm khoảng cách với target nhiều nhất
                while (picked < itemCount
                        && !isSatisfiedSlot(remaining, slotTarget)
                        && scanGuard < pool.size() * 3
                ) {
                    scanGuard++;

                    SelectionResult best = findBestCandidate(
                            pool,
                            usedThisSlot,
                            recentAll,    // né món đã dùng 3 ngày gần nhất (tính trên toàn bộ các slot)
                            remaining,
                            slotTarget,
                            slotTarget,   // heuristicTarget = slotTarget (cùng hướng tối ưu)
                            rules,
                            request
                    );

                    if (best == null) break;

                    MealPlanItem item = MealPlanItem.builder()
                            .day(day)
                            .mealSlot(slot)
                            .food(best.food())
                            .portion(bd(best.portion(), 2))
                            .used(false)
                            .rank(rank++)
                            .nutrition(best.snap())
                            .build();

                    allItems.add(item);
                    recentAll.addLast(best.food().getId());

                    // Giữ kích thước danh sách tránh lặp trong khung noRepeatWindow * tổng số món/ngày
                    while (recentAll.size() > noRepeatWindow * totalItemsPerDay) {
                        recentAll.removeFirst();
                    }

                    usedThisSlot.add(best.food().getId());
                    remaining = subNutSigned(remaining, best.snap());
                    picked++;
                }

                // 13) Fallback: nếu chưa “gần” target và chưa đủ itemCount thì thử bù thêm 1 món
                if (!isSatisfiedSlot(remaining, slotTarget) && picked < itemCount) {
                    SelectionResult best = findBestCandidate(
                            pool,
                            usedThisSlot,
                            recentAll,
                            remaining,
                            slotTarget,
                            slotTarget,
                            rules,
                            request
                    );

                    if (best != null) {
                        MealPlanItem item = MealPlanItem.builder()
                                .day(day)
                                .mealSlot(slot)
                                .food(best.food())
                                .portion(bd(best.portion(), 2))
                                .used(false)
                                .rank(rank++)
                                .nutrition(best.snap())
                                .build();

                        allItems.add(item);

                        recentAll.addLast(best.food().getId());
                        while (recentAll.size() > noRepeatWindow * totalItemsPerDay) {
                            recentAll.removeFirst();
                        }
                    }
                }
            }
        }
        if (!allItems.isEmpty()) {
            mealPlanItemRepository.saveAll(allItems);
        }
        // 14) Tinh chỉnh cuối ngày: bù thêm fat/xơ nếu còn thiếu
        for (MealPlanDay day : planDay) {
            postTuneDayForFatAndFiber(day, target, rules, request);
        }
    }

    /**
     * Lấy kế hoạch ăn trong ngày {@code date} của user hiện tại.
     * Nếu ngày đó chưa có plan thì tự động tạo mới rồi trả về.
     */
    @Override
    @Transactional
    public MealPlanResponse getMealPlanByDate(LocalDate date) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());
        MealPlanDay m = mealPlanDayRepository.findByUser_IdAndDate(userId, date).orElse(null);
        if (m == null) {
            return createOrUpdatePlanForOneDay(date, userId);
        }
        return mealPlanDayMapper.toMealPlanResponse(m, cdnHelper);
    }

    /**
     * Xóa kế hoạch (MealPlanDay + MealPlanItem) từ ngày {@code today} trở đi của một user.
     */
    @Override
    @Transactional
    public void removeFromDate(LocalDate today, UUID userId) {
        mealPlanItemRepository.deleteItemsFromDate(userId, today);
        mealPlanDayRepository.deleteFromDate(userId, today);
    }

    /**
     * Tạo lại / cập nhật kế hoạch cho một ngày cụ thể (xóa item chưa dùng và sinh lại).
     */
    @Override
    @Transactional
    public void updatePlanForOneDay(LocalDate date, UUID userId) {
        createOrUpdatePlanForOneDay(date, userId);
    }

    /**
     * Tính và trả về kcal mục tiêu cho một bữa (slot) của user,
     * dựa trên profile hiện tại + rule + phân bổ % kcal theo bữa.
     */
    @Override
    @Transactional
    public double getMealTargetKcal(UUID userId, MealSlot slot) {
        // ===== 1) Lấy profile (bản request) =====
        ProfileCreationRequest profile = profileOrchestrator.getByUserId_request(userId);
        MealPlanCreationRequest req = MealPlanCreationRequest.builder()
                .userId(userId)
                .profile(profile)
                .build();

        // Dùng context chung của ngày
        DayPlanContext ctx = buildDayPlanContext(req);
        Nutrition dayTarget = ctx.dayTarget();
        List<NutritionRule> rules = ctx.rules();
        int weight = ctx.weight();

        // ===== 3) Xác định % kcal theo slot (breakfast/lunch/dinner/snack) =====
        Map<MealSlot, Double> slotKcalPct = Map.of(
                MealSlot.BREAKFAST, 0.25,
                MealSlot.LUNCH, 0.30,
                MealSlot.DINNER, 0.30,
                MealSlot.SNACK, 0.15);
        double pct = slotKcalPct.getOrDefault(slot, 0.0);
        if (pct <= 0) return 0.0;
        // ===== 4) Tính target dinh dưỡng cho bữa đó =====
        Nutrition mealTarget = approxMacroTargetForMeal(dayTarget, pct, rules, weight, req);
        // ===== 5) Trả về kcal mục tiêu của bữa =====
        return safeDouble(mealTarget.getKcal());
    }

    /**
     * Lấy danh sách target dinh dưỡng ngày (DayTarget) của user trong khoảng [from, to].
     */
    @Override
    @Transactional
    public List<DayTarget> getDayTargetsBetween(LocalDate from, LocalDate to, UUID userId) {
        List<MealPlanDay> days = mealPlanDayRepository.findByUser_IdAndDateBetweenOrderByDateAsc(userId, from, to);
        return days.stream()
                .map(d -> new DayTarget(d.getDate(), d.getTargetNutrition()))
                .toList();
    }

    /**
     * Tạo mới hoặc cập nhật kế hoạch cho một ngày duy nhất, rồi trả về response đầy đủ.
     * Dựa trên: profile, rule, log đã ăn, lịch sử planner gần đây.
     */
    public MealPlanResponse createOrUpdatePlanForOneDay(LocalDate date, UUID userId) {
        // ===== 1) Lấy profile + rules + day target =====
        ProfileCreationRequest pReq = profileOrchestrator.getByUserId_request(userId);
        MealPlanCreationRequest mReq =
                MealPlanCreationRequest.builder().userId(userId).profile(pReq).build();

        DayPlanContext ctx = buildDayPlanContext(mReq);
        Nutrition dayTarget = ctx.dayTarget();
        double waterMl = ctx.waterTargetMl();
        int weight = ctx.weight();
        List<NutritionRule> rules = ctx.rules();

        // ===== 2) Lấy hoặc tạo mới MealPlanDay cho date =====
        MealPlanDay day = mealPlanDayRepository
                .findByUser_IdAndDate(userId, date)
                .orElseGet(() -> MealPlanDay.builder()
                        .user(User.builder().id(userId).build())
                        .date(date)
                        .build());
        day.setTargetNutrition(dayTarget);
        day.setWaterTargetMl((int) Math.round(waterMl));
        day = mealPlanDayRepository.save(day);

        // ===== 3) Đọc log hôm nay + gần đây (để né món và tính nutrition đã ăn) =====
        final int NO_REPEAT_DAYS = 3;
        final int NO_REPEAT_FUTURE_DAYS = 2;
        LocalDate startRecent = date.minusDays(NO_REPEAT_DAYS);
        LocalDate endRecent   = date.plusDays(NO_REPEAT_FUTURE_DAYS);

        List<PlanLog> todayLogs = planLogRepository.findByUser_IdAndDate(userId, date);
        List<PlanLog> recentLogs =
                planLogRepository.findByUser_IdAndDateBetween(userId, startRecent, date.minusDays(1));

        // 3.1) Tính dinh dưỡng đã tiêu thụ theo từng slot (vector dinh dưỡng)
        Map<MealSlot, Nutrition> consumedBySlot = new EnumMap<>(MealSlot.class);
        for (MealSlot s : MealSlot.values()) consumedBySlot.put(s, new Nutrition());

        Set<UUID> eatenFoodToday = new HashSet<>();
        for (PlanLog l : todayLogs) {
            MealSlot s = l.getMealSlot();
            Nutrition add = resolveActualOrFallback(l);
            consumedBySlot.put(s, addNut(consumedBySlot.get(s), add));
            if (l.getFood() != null) eatenFoodToday.add(l.getFood().getId());
        }

        // 3.2) Tập food đã ăn/đã log gần đây (kể cả hôm nay) để né trùng
        Set<UUID> recentFoods = recentLogs.stream()
                .filter(l -> l.getFood() != null)
                .map(l -> l.getFood().getId())
                .collect(Collectors.toSet());
        recentFoods.addAll(eatenFoodToday);

        // Cộng thêm các food đã được plan trong khoảng gần đây (quá khứ + tương lai gần)
        Set<UUID> plannedRecently =
                mealPlanItemRepository.findDistinctFoodIdsPlannedBetween(userId, startRecent, endRecent);
        recentFoods.addAll(plannedRecently);

        // ===== 4) Xóa các item cũ CHƯA được dùng của ngày đó (tránh rác & lỗi FK) =====
        mealPlanItemRepository.deleteUnusedItemsByDay(day.getId());

        // ===== 5) Tính directives để lọc avoid/limit/prefer theo rule + request =====
        TagDirectives tagDir = buildTagDirectives(rules, mReq);
        int rankBase = 1
                + mealPlanItemRepository
                .findByDay_User_IdAndDay_Date(userId, date)
                .size();

        // ===== 6) Với từng slot: tính meal target → remaining → chọn món greedy =====
        for (MealSlot slot : MealSlot.values()) {
            int targetItems = SLOT_ITEM_COUNTS.get(slot);
            double pct = SLOT_KCAL_PCT.get(slot);

            // 6.1) Meal target & remaining (sau khi trừ đi phần đã ăn)
            Nutrition mealTarget = approxMacroTargetForMeal(dayTarget, pct, rules, weight, mReq);
            Nutrition consumed = consumedBySlot.getOrDefault(slot, new Nutrition());
            Nutrition remaining = subNutSigned(mealTarget, consumed);
            if (isSatisfiedSlot(remaining, mealTarget)) continue;

            // 6.2) Pool ứng viên theo slot (kcal khá rộng để linh hoạt)
            final int CANDIDATE_LIMIT = 120;
            final int MIN_KCAL = 20, MAX_KCAL = 2000, PIVOT = 500;
            List<Food> pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                    slot.name(), MIN_KCAL, MAX_KCAL, PIVOT, CANDIDATE_LIMIT);
            if (pool == null) pool = Collections.emptyList();
            if (pool.isEmpty()) continue;

            // 6.3) Lọc bỏ món có tag avoid và món đã xuất hiện gần đây
            List<Food> candidates = pool.stream()
                    .filter(f -> Collections.disjoint(tagsOf(f), tagDir.getAvoid()))
                    .filter(f -> !recentFoods.contains(f.getId()))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (candidates.isEmpty()) continue;

            // 6.4) Sắp xếp ứng viên theo heuristic hiện có (từ phù hợp nhất trở xuống)
            Nutrition slotTargetRemaining = remaining;
            candidates.sort(Comparator.comparingDouble(
                            (Food f) -> scoreFoodHeuristic(f, slotTargetRemaining))
                    .reversed());

            // 6.5) Ước lượng số món cần bù dựa trên kcal còn thiếu và khung targetItems
            double slotQuotaKcal = safeDouble(dayTarget.getKcal()) * pct;
            double rKcal = safeDouble(remaining.getKcal());
            int need = estimateItemNeed(slot, rKcal, slotQuotaKcal, targetItems);
            if (need <= 0) continue;

            // 6.6) Vòng chọn greedy: mỗi lần chọn món có gain tốt nhất (giảm khoảng cách vector)
            int picked = 0;
            Set<UUID> usedThisSlot = new HashSet<>();
            int scanGuard = 0;

            while (picked < need
                    && !isSatisfiedSlot(remaining, mealTarget)
                    && scanGuard < candidates.size() * 3) {

                scanGuard++;

                SelectionResult best = findBestCandidate(
                        candidates,
                        usedThisSlot,
                        recentFoods,        // né món đã ăn/đã log/đã plan gần đây
                        remaining,
                        mealTarget,
                        slotTargetRemaining,
                        rules,
                        mReq
                );

                if (best == null) break;

                mealPlanItemRepository.save(MealPlanItem.builder()
                        .day(day)
                        .mealSlot(slot)
                        .food(best.food())
                        .portion(bd(best.portion(), 2))
                        .used(false)
                        .rank(rankBase++)
                        .nutrition(best.snap())
                        .build());

                usedThisSlot.add(best.food().getId());
                recentFoods.add(best.food().getId());
                remaining = subNutSigned(remaining, best.snap());

                picked++;
            }

            // 6.7) Fallback: nếu chưa đủ gần target và vẫn còn nhu cầu thì bù thêm 1 item
            if (!isSatisfiedSlot(remaining, mealTarget) && picked < need) {
                SelectionResult best = findBestCandidate(
                        candidates,
                        usedThisSlot,
                        recentFoods,
                        remaining,
                        mealTarget,
                        slotTargetRemaining,
                        rules,
                        mReq
                );

                if (best != null) {
                    mealPlanItemRepository.save(MealPlanItem.builder()
                            .day(day)
                            .mealSlot(slot)
                            .food(best.food())
                            .portion(bd(best.portion(), 2))
                            .used(false)
                            .rank(rankBase++)
                            .nutrition(best.snap())
                            .build());
                    recentFoods.add(best.food().getId());
                }
            }
        }

        // ===== 7) Tinh chỉnh cuối ngày và trả response =====
        postTuneDayForFatAndFiber(day, dayTarget, rules, mReq);
        mealPlanItemRepository.flush();
        entityManager.flush();
        entityManager.clear();

        MealPlanDay hydrated =
                mealPlanDayRepository.findByUser_IdAndDate(userId, date).orElse(day);
        return mealPlanDayMapper.toMealPlanResponse(hydrated, cdnHelper);
    }


    /* ===================== HÀM PHỤ TRỢ ===================== */

    /**
     * Ước lượng cần thêm bao nhiêu món cho một slot dựa trên kcal còn thiếu (rKcal),
     * quota kcal của slot và số món target mặc định.
     */
    private int estimateItemNeed(MealSlot slot, double rKcal, double slotQuotaKcal, int targetItems) {
        if (rKcal <= EPS_KCAL) return 0;
        int maxBySlot = (slot == MealSlot.SNACK ? 1 : targetItems);
        if (rKcal < 0.33 * slotQuotaKcal) return Math.min(1, maxBySlot);
        if (rKcal < 0.66 * slotQuotaKcal) return Math.min(2, maxBySlot);
        return maxBySlot;
    }

    /**
     * Cộng tổng dinh dưỡng của tất cả item trong list (addNut lần lượt).
     */
    private Nutrition sumNutrition(List<MealPlanItem> items) {
        Nutrition total = new Nutrition();
        for (MealPlanItem i : items) {
            total = addNut(total, i.getNutrition());
        }
        return total;
    }

    /**
     * Tuning cuối ngày: nếu thiếu fat/xơ nhiều, thử bù thêm 1 món SNACK phù hợp (fat/xơ tốt, protein không quá cao).
     */
    private void postTuneDayForFatAndFiber(
            MealPlanDay day,
            Nutrition dayTarget,
            List<NutritionRule> rules,
            MealPlanCreationRequest request
    ) {
        // 1) Lấy tất cả item của ngày → tính dinh dưỡng thực tế
        List<MealPlanItem> items = mealPlanItemRepository.findByDay_Id(day.getId());
        if (items.isEmpty()) return;

        Nutrition actual = sumNutrition(items);

        double tK  = safeDouble(dayTarget.getKcal());
        double tP  = safeDouble(dayTarget.getProteinG());
        double tF  = safeDouble(dayTarget.getFatG());
        double tFi = safeDouble(dayTarget.getFiberG());

        double aK  = safeDouble(actual.getKcal());
        double aP  = safeDouble(actual.getProteinG());
        double aF  = safeDouble(actual.getFatG());
        double aFi = safeDouble(actual.getFiberG());

        if (tK <= 0 || tF <= 0 || tFi <= 0) return;

        double kcalRatio  = aK  / tK;
        double fatRatio   = aF  / tF;
        double fiberRatio = aFi / tFi;

        // Nếu kcal đã vượt ngưỡng cho phép thì dừng, không bù thêm
        if (kcalRatio > KCAL_MAX_RATIO) return;

        // Chỉ bù khi fat hoặc fiber còn thiếu đáng kể
        boolean needFat   = fatRatio   < FAT_MIN_RATIO;
        boolean needFiber = fiberRatio < FIBER_MIN_RATIO;

        if (!needFat && !needFiber) return;

        // 2) Ước lượng ngân sách kcal để bù (tối đa ~10% target, nhưng không dưới 50 kcal)
        int extraKcalBudget = (int) Math.round(Math.min(200, tK * 0.10));
        if (extraKcalBudget < 50) extraKcalBudget = 50;

        // 3) Lấy pool ứng viên SNACK theo cửa sổ kcal (tương đối nhỏ, phù hợp món phụ)
        final int CANDIDATE_LIMIT = 60;
        int minKcal = 50;
        int maxKcal = Math.max(minKcal + 10, extraKcalBudget);

        List<Food> pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                MealSlot.SNACK.name(), minKcal, maxKcal, extraKcalBudget, CANDIDATE_LIMIT);
        if (pool == null || pool.isEmpty()) return;

        // 4) Lọc theo tag avoid / limit từ rule và tránh món đã dùng trong ngày
        TagDirectives tagDir = buildTagDirectives(rules, request);

        List<Food> candidates = pool.stream()
                .filter(f -> Collections.disjoint(tagsOf(f), tagDir.getAvoid()))
                .collect(Collectors.toCollection(ArrayList::new));

        // Né trùng món đã có trong ngày
        Set<UUID> usedFoodIds = items.stream()
                .filter(i -> i.getFood() != null)
                .map(i -> i.getFood().getId())
                .collect(Collectors.toSet());

        candidates = candidates.stream()
                .filter(f -> !usedFoodIds.contains(f.getId()))
                .collect(Collectors.toCollection(ArrayList::new));

        if (candidates.isEmpty()) return;

        // 5) Chọn 1 món có fiber/fat tốt, protein không quá cao, không vượt ratio kcal/protein
        Food best = null;
        double bestScore = -1e9;
        Nutrition bestNut = null;
        double bestPortion = 1.0;

        for (Food cand : candidates) {
            Nutrition nutBase = cand.getNutrition();
            if (nutBase == null || nutBase.getKcal() == null) continue;

            for (double portion : PORTION_STEPS) {
                Nutrition snap = scaleNutrition(nutBase, portion);
                if (!passesItemRules(rules, snap, request)) continue;

                double k  = safeDouble(snap.getKcal());
                double p  = safeDouble(snap.getProteinG());
                double f  = safeDouble(snap.getFatG());
                double fi = safeDouble(snap.getFiberG());

                if (k <= 0 || k > extraKcalBudget * 1.5) continue;

                // Không cho vượt ngưỡng kcal tối đa sau khi bù
                double newKcalRatio = (aK + k) / tK;
                if (newKcalRatio > KCAL_MAX_RATIO) continue;

                // Không cho protein vượt quá ngưỡng cho phép
                if (tP > 0) {
                    double newProtRatio = (aP + p) / tP;
                    if (newProtRatio > PROT_MAX_RATIO) continue;
                }

                // Ưu tiên: nhiều fiber/fat, ít protein
                double score = 0.0;
                if (needFiber) score += fi * 2.0;   // ưu tiên bù xơ
                if (needFat)   score += f  * 1.5;   // ưu tiên bù béo

                // phạt nếu protein cao
                score -= p * 1.5;

                if (score > bestScore) {
                    bestScore = score;
                    best = cand;
                    bestNut = snap;
                    bestPortion = portion;
                }
            }
        }

        // Không tìm được ứng viên phù hợp thì bỏ qua
        if (best == null) return;

        // 6) Thêm món bù vào SNACK với rank = maxRank + 1 trong ngày
        int maxRank = items.stream()
                .mapToInt(MealPlanItem::getRank)
                .max()
                .orElse(0);

        mealPlanItemRepository.save(MealPlanItem.builder()
                .day(day)
                .mealSlot(MealSlot.SNACK)
                .food(best)
                .portion(bd(bestPortion, 2))
                .used(false)
                .rank(maxRank + 1)
                .nutrition(bestNut)
                .build());
    }

    //============================================RECORDS======================================================//

    /**
     * Kết quả chọn món: food + snapshot dinh dưỡng + portion + gain (điểm cải thiện).
     */
    private record SelectionResult(Food food, Nutrition snap, double portion, double gain) {}

    /**
     * Context một ngày: profile, weight, danh sách rules, constraints tổng,
     * target dinh dưỡng và nước uống ngày.
     */
    private record DayPlanContext(
            ProfileCreationRequest profile,
            int weight,
            List<NutritionRule> rules,
            AggregateConstraints constraints,
            Nutrition dayTarget,
            double waterTargetMl
    ) {}

    //============================================DAY======================================================//

    /**
     * Xây dựng DayPlanContext từ MealPlanCreationRequest:
     * lấy rule, tính constraints, target dinh dưỡng ngày và nhu cầu nước.
     */
    private DayPlanContext buildDayPlanContext(MealPlanCreationRequest request) {
        ProfileCreationRequest profile = request.getProfile();
        int weight = Math.max(1, profile.getWeightKg());
        List<NutritionRule> rules = nutritionRuleService.getRuleByUserId(request.getUserId());
        // Tính min/max dinh dưỡng (ngày) theo rule
        AggregateConstraints agg = deriveAggregateConstraintsFromRules(rules, weight);
        // Tính target dinh dưỡng ngày dựa trên profile + constraints
        Nutrition dayTarget = caculateNutrition(profile, agg);
        // Nhu cầu nước cơ bản theo cân nặng
        double waterMl = weight * WATER_ML_PER_KG;
        // Nếu rule có giới hạn tối thiểu nước thì ưu tiên theo rule
        if (agg.dayWaterMin != null) {
            waterMl = Math.max(waterMl, agg.dayWaterMin.doubleValue());
        }
        return new DayPlanContext(profile, weight, rules, agg, dayTarget, waterMl);
    }

    //============================================ITEM======================================================//

    /**
     * Tìm món ứng viên tốt nhất trong pool cho slot hiện tại (dựa trên remaining và mealTarget).
     * Trả về món + portion + snapshot dinh dưỡng nếu gain > 0, ngược lại trả null.
     */
    private SelectionResult findBestCandidate(
            List<Food> candidates,
            Set<UUID> usedThisSlot,
            Collection<UUID> globalRecent,
            Nutrition remaining,
            Nutrition mealTarget,
            Nutrition heuristicTarget,
            List<NutritionRule> rules,
            MealPlanCreationRequest request
    ) {
        double bestGain    = Double.NEGATIVE_INFINITY;
        Food bestFood      = null;
        Nutrition bestSnap = null;
        double bestPortion = 1.0;

        for (Food cand : candidates) {
            UUID id = cand.getId();
            // né food đã dùng trong slot này
            if (usedThisSlot != null && usedThisSlot.contains(id)) continue;
            // né food đã xuất hiện gần đây (log + plan)
            if (globalRecent != null && globalRecent.contains(id)) continue;

            Nutrition nut = cand.getNutrition();
            if (nut == null || nut.getKcal() == null || safeDouble(nut.getKcal()) <= 0) continue;

            for (double portion : PORTION_STEPS) {
                Nutrition snap = scaleNutrition(nut, portion);

                // Nếu vi phạm rule, thử giảm portion (stepDown) cho tới khi hợp lệ
                if (!passesItemRules(rules, snap, request)) {
                    var step = stepDown(portion);
                    boolean fixed = false;
                    while (step.isPresent()) {
                        double p2 = step.getAsDouble();
                        Nutrition s2 = scaleNutrition(nut, p2);
                        if (passesItemRules(rules, s2, request)) {
                            portion = p2;
                            snap = s2;
                            fixed = true;
                            break;
                        }
                        step = stepDown(p2);
                    }
                    if (!fixed) continue;
                }

                // Nếu thêm món này làm vượt ngưỡng protein cho bữa thì bỏ
                if (wouldExceedProteinForMeal(mealTarget, remaining, snap, PROT_MAX_RATIO)) {
                    continue;
                }

                // Gain = mức độ giảm khoảng cách dinh dưỡng + chút bonus theo score heuristic
                double before   = nutritionDistance(remaining);
                Nutrition after = subNutSigned(remaining, snap);
                double afterVal = nutritionDistance(after);

                double gain = (before - afterVal)
                        + 0.10 * scoreFoodHeuristic(cand, heuristicTarget);

                if (gain > bestGain) {
                    bestGain    = gain;
                    bestFood    = cand;
                    bestSnap    = snap;
                    bestPortion = portion;
                }
            }
        }

        // Nếu không có món nào giúp cải thiện (gain <= 0) thì coi như không chọn
        if (bestFood == null || bestGain <= 0) {
            return null;
        }
        return new SelectionResult(bestFood, bestSnap, bestPortion, bestGain);
    }
}
