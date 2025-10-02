package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.enums.ActivityLevel;
import com.hn.nutricarebe.enums.FoodTag;
import com.hn.nutricarebe.enums.GoalType;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.mapper.MealPlanDayMapper;
import com.hn.nutricarebe.repository.FoodRepository;
import com.hn.nutricarebe.repository.MealPlanDayRepository;
import com.hn.nutricarebe.repository.MealPlanItemRepository;
import com.hn.nutricarebe.service.MealPlanDayService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MealPlanDayServiceImpl implements MealPlanDayService {

    MealPlanDayRepository mealPlanDayRepository;
    MealPlanDayMapper mealPlanDayMapper;
    FoodRepository foodRepository;
    MealPlanItemRepository mealPlanItemRepository;


    @Override
    public MealPlanResponse createPlan(MealPlanCreationRequest request) {
        final double MIN_KCAL_FEMALE = 1200.0;
        final double MIN_KCAL_MALE   = 1500.0;
        final double FAT_PCT = 0.30;                    // WHO: ≤30%
        final double FREE_SUGAR_PCT_MAX = 0.10;         // WHO: <10%
        final int    SODIUM_MG_LIMIT = 2000;            // WHO: <2000 mg natri
        final double WATER_ML_PER_KG = 35.0;            // 30–35 ml/kg
        final double MAX_DAILY_ADJ   = 1000.0;          // ±1000 kcal/ngày

        var profile = request.getProfile();
        int currentYear = java.time.Year.now().getValue();
        int age    = Math.max(0, currentYear - profile.getBirthYear());
        int weight = Math.max(1, profile.getWeightKg());
        int height = Math.max(50, profile.getHeightCm());

        // 1) BMR: Mifflin–St Jeor
        double bmr = switch (profile.getGender()) {
            case MALE   -> 10 * weight + 6.25 * height - 5 * age + 5;
            case FEMALE -> 10 * weight + 6.25 * height - 5 * age - 161;
            case OTHER  -> 10 * weight + 6.25 * height - 5 * age;
        };

        // 2) TDEE theo mức độ hoạt động (guard null)
        ActivityLevel al = profile.getActivityLevel() != null ? profile.getActivityLevel() : ActivityLevel.SEDENTARY;
        double activityFactor = switch (al) {
            case SEDENTARY         -> 1.2;
            case LIGHTLY_ACTIVE    -> 1.375;
            case MODERATELY_ACTIVE -> 1.55;
            case VERY_ACTIVE       -> 1.725;
            case EXTRA_ACTIVE      -> 1.9;
        };
        double tdee = bmr * activityFactor;

        // 3) Điều chỉnh kcal theo mục tiêu cân nặng (delta kg dương=tăng, âm=giảm)
        Integer deltaKg = profile.getTargetWeightDeltaKg();
        Integer weeks   = profile.getTargetDurationWeeks();
        double dailyAdj = 0.0;
        boolean hasDelta = (deltaKg != null && deltaKg != 0) && (weeks != null && weeks > 0);
        if (hasDelta && profile.getGoal() != GoalType.MAINTAIN) {
            dailyAdj = (deltaKg * 7700.0) / (weeks * 7.0); // kcal/ngày, mang dấu theo delta
            dailyAdj = Math.max(-MAX_DAILY_ADJ, Math.min(MAX_DAILY_ADJ, dailyAdj));
        }

        // Nếu MAINTAIN, bỏ qua delta
        double targetCalories = (profile.getGoal() == GoalType.MAINTAIN) ? tdee : (tdee + dailyAdj);

        // 3b) Mức kcal tối thiểu theo giới
        targetCalories = switch (profile.getGender()){
            case FEMALE -> Math.max(MIN_KCAL_FEMALE, targetCalories);
            case MALE   -> Math.max(MIN_KCAL_MALE,   targetCalories);
            case OTHER  -> Math.max(MIN_KCAL_FEMALE, targetCalories);
        };

        // 4) Nước
        double waterMl = weight * WATER_ML_PER_KG;

        // 5) Protein theo g/kg (WHO ≥0.8; giảm mỡ/tăng cơ 1.0–1.2)
        double proteinPerKg = switch (profile.getGoal()) {
            case MAINTAIN -> 0.8;
            case LOSE     -> 1.0;
            case GAIN     -> 1.2;
        };
        double proteinG = weight * proteinPerKg;
        double proteinKcal = proteinG * 4.0;

        // 6) Fat: 30% năng lượng (không vượt chuẩn WHO)
        double fatKcal = targetCalories * FAT_PCT;
        double fatG = fatKcal / 9.0;

        // 7) Carb = phần còn lại
        double carbKcal = Math.max(0.0, targetCalories - proteinKcal - fatKcal);
        double carbG = carbKcal / 4.0;

        // 8) Fiber: tối thiểu 25g; nâng theo 14g/1000kcal nếu kcal cao
        double fiberG = Math.max(25.0, 14.0 * (targetCalories / 1000.0));

        // 9) Free sugar trần <10% năng lượng (lưu mg)
        double sugarGMax = (targetCalories * FREE_SUGAR_PCT_MAX) / 4.0;
        double sugarMg = sugarGMax * 1000.0;

        Nutrition target = Nutrition.builder()
                .kcal(bd(targetCalories, 2))
                .proteinG(bd(proteinG, 2))
                .carbG(bd(carbG, 2))
                .fatG(bd(fatG, 2))
                .fiberG(bd(fiberG, 2))
                .sodiumMg(bd(SODIUM_MG_LIMIT, 2))
                .sugarMg(bd(sugarMg, 2))
                .build();


        LocalDate startDate = LocalDate.now();

        List<MealPlanDay> days = new ArrayList<>(7);
        User user = User.builder().id(request.getUserId()).build();
        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);

            MealPlanDay day = MealPlanDay.builder()
                    .user(user)
                    .targetNutrition(target)
                    .date(d)
                    .waterTargetMl((int) Math.round(waterMl))
                    .build();
            days.add(day);
        }
        List<MealPlanDay> savedDays  = mealPlanDayRepository.saveAll(days);
        //        return mealPlanDayMapper.toMealPlanResponse(mealPlanDayRepository.save(savedDays.getFirst()));


        // ===== Cấu hình bữa & số món/bữa ===== (tính lại)
        Map<MealSlot, Double> slotKcalPct = Map.of(
                MealSlot.BREAKFAST, 0.25,
                MealSlot.LUNCH,     0.30,
                MealSlot.DINNER,    0.30,
                MealSlot.SNACK,     0.15
        );
        Map<MealSlot, Integer> slotItemCounts = Map.of(
                MealSlot.BREAKFAST, 2,
                MealSlot.LUNCH,     3,
                MealSlot.DINNER,    3,
                MealSlot.SNACK,     1
        );

        // ===== Chuẩn bị pool ứng viên theo slot (1 lần) =====
        record SlotPool(List<Food> foods, Map<UUID, Double> baseScore) {}
        Map<MealSlot, SlotPool> pools = new EnumMap<>(MealSlot.class);

        final int CANDIDATE_LIMIT = 80;     // 60–100
        final int noRepeatWindow  = 3;      // không lặp trong 3 ngày
        final long seed = Objects.hash(request.getUserId(), LocalDate.now().get( WEEK_OF_WEEK_BASED_YEAR ));
        Random rng = new Random(seed);

        double dayTargetKcal = safeDouble(target.getKcal());

        for (MealSlot slot : MealSlot.values()) {
            double slotKcal = dayTargetKcal * slotKcalPct.get(slot);  // kcal mục tiêu bữa
            int itemCount   = slotItemCounts.get(slot);  // số món bữa
            int perItem     = (int)Math.round(slotKcal / Math.max(1, itemCount));  // số kcal/món

            int minKcal = (int)Math.max(50, Math.round(perItem * 0.5));//món kcal tối thiểu
            int maxKcal = (int)Math.round(perItem * 2.0); //món kcal tối đa

            List<Food> pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                    slot.name(), minKcal, maxKcal, perItem, CANDIDATE_LIMIT
            );

            if (pool.size() < itemCount) {
                pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                        slot.name(), Math.max(30,(int)(perItem*0.3)), (int)(perItem*2.5), perItem, CANDIDATE_LIMIT
                );
            }
            // chấm điểm 1 lần (heuristic + LLM nếu muốn), sau đó xáo để tăng đa dạng khi điểm gần nhau

            //Mục tiêu dinh dưỡng bữa
            Nutrition slotTarget = approxMacroTargetForSlot(target, slotKcalPct.get(slot));

            Map<UUID, Double> score = new HashMap<>();
            for (Food f : pool) {
                double s = scoreFoodHeuristic(f, slotTarget);
                s += scoreFoodByLLMIfAny(f, slot, slotTarget);
                score.put(f.getId(), s);
            }

            // Sắp xếp giảm dần theo điểm
            pool.sort(
                    Comparator.<Food>comparingDouble(
                            f -> score.getOrDefault(f.getId(), 0.0)).reversed());


            // Xáo nhẹ nhóm 5 món để tăng đa dạng (không xáo cả list vì sẽ làm giảm chất lượng)
            for (int i=0; i+4<pool.size(); i+=5)
                Collections.shuffle(pool.subList(i,i+5), rng);

            pools.put(slot, new SlotPool(pool, score));
        }

        // ===== Trạng thái "không lặp" theo slot trong noRepeatWindow ngày =====
        Map<MealSlot, Deque<UUID>> recentBySlot = new EnumMap<>(MealSlot.class);
        for (MealSlot s : MealSlot.values())
            recentBySlot.put(s, new ArrayDeque<>());

        //Lập lịch 7 ngày: khác món mỗi ngày, nhẹ, không re-score
        for (int di = 0; di < savedDays.size(); di++) {
            MealPlanDay day = savedDays.get(di);
            int rank = 1;

            for (MealSlot slot : MealSlot.values()) {
                double slotKcal = dayTargetKcal * slotKcalPct.get(slot);
                int itemCount   = slotItemCounts.get(slot);
                Nutrition slotTarget = approxMacroTargetForSlot(target, slotKcalPct.get(slot));

                // con trỏ bắt đầu khác nhau theo ngày để đẩy đa dạng
                SlotPool sp = pools.get(slot);
                List<Food> pool = sp.foods();
                if (pool.isEmpty()) continue;

                int startIdx = (int)Math.floor(
                        Math.abs(rng.nextGaussian()) * 3 + di*itemCount) % Math.max(1,pool.size());

                Set<FoodTag> usedTags = new HashSet<>();  // tag đã dùng trong bữa để ưu tiên món khác tag
                double kcalRemain = slotKcal;

                int picked = 0;  // số món đã chọn
                int scan = 0;    // số món đã duyệt
                while (picked < itemCount && scan < pool.size()*2) {

                    // Lấy món ở vị trí (startIdx + scan) % size
                    Food cand = pool.get( (startIdx + scan) % pool.size() );
                    scan++;

                    // Nếu món đã dùng trong 3 ngày gần đây → BỎ QUA
                    if (recentBySlot.get(slot).contains(cand.getId())) continue;

                    // Nếu món không có thông tin kcal → BỎ QUA
                    var nut = cand.getNutrition();
                    if (nut == null || nut.getKcal()==null || safeDouble(nut.getKcal())<=0) continue;

                    // Nếu món có tag trùng với món đã chọn trong bữa → ưu tiên bỏ qua
                    if (!Collections.disjoint(usedTags, cand.getTags())) {
                        if (rng.nextDouble() < 0.30) continue;
                    }

                    // Tính khẩu phần để gần đủ kcal còn lại (60%–160% 1 phần)
                    double portion = clamp(kcalRemain / safeDouble(nut.getKcal()), 0.6, 1.6);
                    Nutrition snap = scaleNutrition(nut, portion);

                    // sodium/sugar mềm dẻo theo slot-target
                    boolean exceedNa = snap.getSodiumMg()!=null && safeDouble(snap.getSodiumMg()) > Math.max(400, 2000*slotKcalPct.get(slot));
                    boolean exceedSu = snap.getSugarMg()!=null && snap.getSugarMg().compareTo(slotTarget.getSugarMg()!=null?slotTarget.getSugarMg():BigDecimal.ZERO) > 0;
                    if (exceedNa || exceedSu) {
                        // thử giảm 15%
                        double tryPortion = clamp(portion*0.85, 0.5, portion);
                        Nutrition trySnap = scaleNutrition(nut, tryPortion);
                        if ((trySnap.getSodiumMg()!=null && safeDouble(trySnap.getSodiumMg())> 2000*slotKcalPct.get(slot))) continue;
                        portion = tryPortion;
                        snap = trySnap;
                    }

                    mealPlanItemRepository.save(MealPlanItem.builder()
                            .day(day)
                            .mealSlot(slot)
                            .food(cand)
                            .portion(bd(portion,2))
                            .rank(rank++)
                            .nutrition(snap)
                            .build());

                    // cập nhật trạng thái no-repeat
                    Deque<UUID> dq = recentBySlot.get(slot);   // Thêm vào danh sách "đã dùng"
                    dq.addLast(cand.getId());
                    while (dq.size() > noRepeatWindow * slotItemCounts.get(slot))
                        dq.removeFirst();

                    usedTags.addAll(cand.getTags()); // Thêm tags đã dùng
                    kcalRemain = Math.max(0, kcalRemain - safeDouble(snap.getKcal()));
                    picked++; // Tăng số món đã chọn
                }

                // nếu vẫn thiếu món -> lấp món kcal thấp không trùng recent (fix lại)
                if (picked < itemCount) {
                    for (Food f : pool.stream().sorted(Comparator.comparingDouble(ff -> safeDouble(ff.getNutrition().getKcal()))).toList()) {
                        if (picked >= itemCount) break;
                        if (recentBySlot.get(slot).contains(f.getId())) continue;
                        var nut = f.getNutrition();
                        if (nut == null || nut.getKcal()==null || safeDouble(nut.getKcal())<=0) continue;
                        Nutrition snap = scaleNutrition(nut, 1.0);
                        mealPlanItemRepository.save(MealPlanItem.builder()
                                .day(day)
                                .mealSlot(slot)
                                .food(f)
                                .portion(bd(1.0,2))
                                .rank(rank++)
                                .nutrition(snap)
                                .build());
                        Deque<UUID> dq = recentBySlot.get(slot);
                        dq.addLast(f.getId());
                        while (dq.size() > noRepeatWindow * slotItemCounts.get(slot)) dq.removeFirst();
                        picked++;
                    }
                }
            }
        }


        // Trả về ngày đầu theo contract cũ
        return mealPlanDayMapper.toMealPlanResponse(savedDays.getFirst());

    }


    //Chuyển double -> BigDecimal với scale & rounding
    private static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    /* ===================== HÀM PHỤ TRỢ ===================== */

    // Heuristic: ưu tiên gần macro mục tiêu (cosine-like), giàu fiber, kcal density vừa phải,
    // phạt sodium/sugar cao. Trả về điểm càng lớn càng tốt.
    private double scoreFoodHeuristic(Food f, Nutrition slotTarget) {
        Nutrition n = f.getNutrition();
        if (n == null) return -1e9;

        // dinh dưỡng món
        double kcal = safeDouble(n.getKcal());

        double p = safeDouble(n.getProteinG());
        double c = safeDouble(n.getCarbG());
        double fat = safeDouble(n.getFatG());
        double sum = p + c + fat + 1e-6;

        double fiber = safeDouble(n.getFiberG());

        double sodium = safeDouble(n.getSodiumMg());
        double sugarMg = safeDouble(n.getSugarMg());

        // dinh dưỡng target
        double tp = safeDouble(slotTarget.getProteinG());
        double tc = safeDouble(slotTarget.getCarbG());
        double tf = safeDouble(slotTarget.getFatG());
        double sumT = tp + tc + tf + 1e-6;

        // actual ratios
        double rp = p / sum;
        double rc = c / sum;
        double rf = fat / sum;

        // target ratios
        double rtp = (tp / sumT);
        double rtc = (tc / sumT);
        double rtf = (tf / sumT);

        // khoảng cách:  Càng gần mục tiêu thì penalty càng thấp, điểm càng cao
        double ratioPenalty = Math.abs(rp - rtp) + Math.abs(rc - rtc) + Math.abs(rf - rtf);

        // kcal density: tránh quá cao (khó chia portion) hoặc quá thấp (không đủ no)

        double kcalDensityScore = -Math.abs(kcal - (safeDouble(slotTarget.getKcal()) / 2.5));

        // fiber thưởng nhẹ
        double fiberBonus = Math.min(fiber, 8.0); // cap

        // sodium/sugar phạt theo % phần còn lại trong ngày
        double sodiumPenalty = 0.0;
        if (safeDouble(slotTarget.getSodiumMg()) > 0) sodiumPenalty = sodium / (safeDouble(slotTarget.getSodiumMg()) + 1e-6) * 2.0;
        double sugarPenalty = 0.0;
        if (slotTarget.getSugarMg() != null && slotTarget.getSugarMg().doubleValue() > 0) {
            sugarPenalty = sugarMg / (slotTarget.getSugarMg().doubleValue() + 1e-6) * 1.5;
        }

        // tags bonus: ưu tiên món có tag lành mạnh, giảm đồ chiên/ngọt…
        double tagAdj = 0.0;
        if (f.getTags() != null) {
            if (f.getTags().contains(FoodTag.HIGH_FIBER)) tagAdj += 1.0;
            if (f.getTags().contains(FoodTag.LEAN_PROTEIN)) tagAdj += 1.0;
            if (f.getTags().contains(FoodTag.FRIED)) tagAdj -= 1.0;
            if (f.getTags().contains(FoodTag.SUGARY)) tagAdj -= 1.0;
            if (f.getTags().contains(FoodTag.PROCESSED)) tagAdj -= 0.6;
        }

        return -ratioPenalty + (kcalDensityScore / 300.0) + (fiberBonus * 0.2) + tagAdj - sodiumPenalty - sugarPenalty;
    }

    // Nếu có bean LLM scorer, dùng thêm điểm ưu tiên (vị giác/ẩm thực/độ hợp slot…)
    private double scoreFoodByLLMIfAny(Food food, MealSlot slot, Nutrition slotTarget) {
//        try {
//            if (mealLLMScorer != null) {
//                return -mealLLMScorer.scoreFood(food, slot, slotTarget, Set.of(), Locale.getDefault());
//            }
//        } catch (Exception ignore) {}
        return 0.0;
    }

    private Nutrition scaleNutrition(Nutrition base, double portion) {
        return Nutrition.builder()
                .kcal(bd(safeDouble(base.getKcal()) * portion, 2))
                .proteinG(bd(safeDouble(base.getProteinG()) * portion, 2))
                .carbG(bd(safeDouble(base.getCarbG()) * portion, 2))
                .fatG(bd(safeDouble(base.getFatG()) * portion, 2))
                .fiberG(bd(safeDouble(base.getFiberG()) * portion, 2))
                .sodiumMg(bd(safeDouble(base.getSodiumMg()) * portion, 2))
                .sugarMg(bd(safeDouble(base.getSugarMg()) * portion, 2))
                .build();
    }


    // Ước lượng macro mục tiêu cho từng slot
    private Nutrition approxMacroTargetForSlot(Nutrition dayTarget, double pctKcal) {
        // Giữ ratios theo ngày, chỉ scale theo % kcal của slot
        double kcal = safeDouble(dayTarget.getKcal()) * pctKcal;
        double ratio = kcal / Math.max(1, safeDouble(dayTarget.getKcal()));  //Tỉ lệ giữa kcal slot và ngày
        return Nutrition.builder()
                .kcal(bd(kcal, 2))
                .proteinG(bd(safeDouble(dayTarget.getProteinG()) * ratio, 2))
                .carbG(bd(safeDouble(dayTarget.getCarbG()) * ratio, 2))
                .fatG(bd(safeDouble(dayTarget.getFatG()) * ratio, 2))
                .fiberG(bd(Math.max(6.0, safeDouble(dayTarget.getFiberG()) * ratio), 2)) // bữa nào cũng có tối thiểu fiber
                .sodiumMg(bd(Math.min(700, 2000 * pctKcal), 2))    // soft cap theo % kcal
                .sugarMg(bd(Math.max(0, safeDouble(dayTarget.getSugarMg()) * ratio), 2))
                .build();
    }

    //lo ==== hi ===== v
    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private double safeDouble(BigDecimal x){ return x == null ? 0.0 : x.doubleValue(); }

}