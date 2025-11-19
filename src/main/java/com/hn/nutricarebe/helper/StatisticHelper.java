package com.hn.nutricarebe.helper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.service.impl.StatisticsServiceImpl;

import static com.hn.nutricarebe.helper.MealPlanHelper.*;

public final class StatisticHelper {
    private StatisticHelper() {}

    public static StatisticsServiceImpl.WeekRange getCurrentWeekRange() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return new StatisticsServiceImpl.WeekRange(start, end);
    }

    public static StatisticsServiceImpl.MonthRange getCurrentMonthRange() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = today.withDayOfMonth(today.lengthOfMonth());
        return new StatisticsServiceImpl.MonthRange(start, end);
    }

    //Cảnh báo
    public static String compareDay(DayTarget target, DayConsumedTotal consumed) {
        var t = target.getTarget();
        var c = consumed.getTotal();
        double dKcal = safe(c.getKcal()) - safe(t.getKcal());
        StringBuilder sb = new StringBuilder();
        if (!withinKcal(t.getKcal(), dKcal)) {
            sb.append(dKcal < 0 ? "Thiếu kcal " + fmt(-dKcal) : "Dư kcal " + fmt(dKcal));
        }
        if (sb.isEmpty()) return null;
        return "Ngày " + target.getDate() + ": " + sb.toString().trim();
    }

    // Trả về NỘI DUNG cảnh báo kcal cho 1 ngày (không kèm "Ngày ...")
    public static String compareDayBody(DayTarget target, DayConsumedTotal consumed) {
        var t = target.getTarget();
        var c = consumed.getTotal();
        double dKcal = safe(c.getKcal()) - safe(t.getKcal());

        if (!withinKcal(t.getKcal(), dKcal)) {
            return dKcal < 0
                    ? "Thiếu kcal " + fmt(-dKcal)
                    : "Dư kcal " + fmt(dKcal);
        }
        return null;
    }



    public static boolean withinKcal(Number tgt, double d) {
        double target = safe(tgt);
        double actual = target + d;
        if (target <= 0) {
            return true;
        }
        return isWithinRatio(actual, target, KCAL_MIN_RATIO, KCAL_MAX_RATIO);
    }

    public static double safe(Number n) {
        return n == null ? 0.0 : n.doubleValue();
    }

    public static String fmt(double v) {
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }

    public static List<MonthlyWeeklyNutritionDto> aggregateMonthByWeeks(
            List<DailyNutritionDto> daily, LocalDate monthStart, LocalDate monthEnd) {

        List<StatisticsServiceImpl.WeekRange> weeks = splitMonthIntoWeeksMonSun(monthStart, monthEnd);

        Map<LocalDate, DailyNutritionDto> byDate = daily.stream()
                .collect(java.util.stream.Collectors.toMap(DailyNutritionDto::getDate, d -> d, (a, b) -> b));

        List<MonthlyWeeklyNutritionDto> out = new java.util.ArrayList<>();
        int idx = 1;
        for (StatisticsServiceImpl.WeekRange w : weeks) {
            java.math.BigDecimal prot = java.math.BigDecimal.ZERO;
            java.math.BigDecimal carb = java.math.BigDecimal.ZERO;
            java.math.BigDecimal fat = java.math.BigDecimal.ZERO;
            java.math.BigDecimal fiber = java.math.BigDecimal.ZERO;
            int daysWithLogs = 0;

            LocalDate d = w.start();
            while (!d.isAfter(w.end())) {
                DailyNutritionDto dn = byDate.get(d);
                if (dn != null) {
                    boolean hasAny = nz(dn.getProteinG()).signum() != 0
                            || nz(dn.getCarbG()).signum() != 0
                            || nz(dn.getFatG()).signum() != 0
                            || nz(dn.getFiberG()).signum() != 0;
                    if (hasAny) daysWithLogs++;

                    prot = prot.add(nz(dn.getProteinG()));
                    carb = carb.add(nz(dn.getCarbG()));
                    fat = fat.add(nz(dn.getFatG()));
                    fiber = fiber.add(nz(dn.getFiberG()));
                }
                d = d.plusDays(1);
            }

            out.add(MonthlyWeeklyNutritionDto.builder()
                    .weekIndex(idx++)
                    .weekStart(w.start())
                    .weekEnd(w.end())
                    .proteinG(prot)
                    .carbG(carb)
                    .fatG(fat)
                    .fiberG(fiber)
                    .daysWithLogs(daysWithLogs)
                    .build());
        }
        return out;
    }

    public static List<StatisticsServiceImpl.WeekRange> splitMonthIntoWeeksMonSun(
            LocalDate monthStart, LocalDate monthEnd) {
        LocalDate cursor = monthStart.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endCap = monthEnd.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<StatisticsServiceImpl.WeekRange> list = new java.util.ArrayList<>();
        while (!cursor.isAfter(endCap)) {
            LocalDate ws = cursor;
            LocalDate we = cursor.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            LocalDate clippedStart = ws.isBefore(monthStart) ? monthStart : ws;
            LocalDate clippedEnd = we.isAfter(monthEnd) ? monthEnd : we;

            if (!clippedStart.isAfter(clippedEnd)) {
                list.add(new StatisticsServiceImpl.WeekRange(clippedStart, clippedEnd));
            }
            cursor = we.plusDays(1);
        }
        return list;
    }

    public static java.math.BigDecimal nz(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }

    public static List<MonthlyWeeklyWaterTotalDto> aggregateWaterMonthByWeeks(
            List<DailyWaterTotalDto> daily, LocalDate monthStart, LocalDate monthEnd) {

        List<StatisticsServiceImpl.WeekRange> weeks = splitMonthIntoWeeksMonSun(monthStart, monthEnd);

        Map<LocalDate, Long> byDate = new java.util.HashMap<>();
        for (DailyWaterTotalDto d : daily) {
            byDate.put(d.getDate(), d.getTotalMl() == null ? 0L : d.getTotalMl());
        }

        List<MonthlyWeeklyWaterTotalDto> out = new java.util.ArrayList<>();
        int idx = 1;
        for (StatisticsServiceImpl.WeekRange w : weeks) {
            long totalMl = 0L;
            int daysWithLogs = 0;

            LocalDate cur = w.start();
            while (!cur.isAfter(w.end())) {
                Long ml = byDate.get(cur);
                if (ml != null && ml > 0L) {
                    totalMl += ml;
                    daysWithLogs++;
                }
                cur = cur.plusDays(1);
            }

            out.add(MonthlyWeeklyWaterTotalDto.builder()
                    .weekIndex(idx++)
                    .weekStart(w.start())
                    .weekEnd(w.end())
                    .totalMl(totalMl)
                    .daysWithLogs(daysWithLogs)
                    .build());
        }
        return out;
    }

    public static List<String> warningsByWeekCompact(
            List<DayTarget> dayTargets,
            Map<LocalDate, DayConsumedTotal> consumedMap,
            Map<LocalDate, Long> waterActualMap,
            Map<LocalDate, Integer> waterTargetMap,
            LocalDate monthStart,
            LocalDate monthEnd,
            int topPerWeek) {

        List<StatisticsServiceImpl.WeekRange> weeks = splitMonthIntoWeeksMonSun(monthStart, monthEnd);

        Map<LocalDate, DayTarget> targetByDate = new java.util.HashMap<>();
        for (DayTarget t : dayTargets) targetByDate.put(t.getDate(), t);

        List<String> lines = new java.util.ArrayList<>();
        int idx = 1;
        for (StatisticsServiceImpl.WeekRange w : weeks) {
            record ScoredWarn(String text, double score) {}
            List<ScoredWarn> scored = new java.util.ArrayList<>();
            int noFoodLogDays = 0;
            int noWaterLogDays = 0;

            LocalDate d = w.start();
            while (!d.isAfter(w.end())) {
                DayTarget t = targetByDate.get(d);

                // --------- ĂN UỐNG (kcal) ----------
                if (t != null) {
                    DayConsumedTotal c = consumedMap.get(d);
                    if (c == null) {
                        noFoodLogDays++;
                        scored.add(new ScoredWarn("Không có log ăn uống", 1.0));
                    } else {
                        String kcalBody = compareDayBody(t, c); // chỉ nội dung kcal
                        if (kcalBody != null) {
                            var tv = t.getTarget();
                            var cv = c.getTotal();
                            double dKcal = safe(cv.getKcal()) - safe(tv.getKcal());
                            double score = Math.abs(dKcal) / 100.0; // chỉ tính điểm theo kcal
                            scored.add(new ScoredWarn(kcalBody, score));
                        }
                    }
                }


                // --------- NƯỚC ----------
                Long actualWater = (waterActualMap != null) ? waterActualMap.get(d) : null;
                Integer targetWater = (waterTargetMap != null) ? waterTargetMap.get(d) : null;

                long actual = (actualWater == null ? 0L : actualWater);
                if (actualWater == null) {
                    noWaterLogDays++;
                }

                if (targetWater != null && targetWater > 0) {
                    long deficit = targetWater - actual;
                    if (deficit > 0) {
                        String msg = "Ngày " + d + ": Thiếu nước " + deficit
                                + " ml (uống " + actual + "/" + targetWater + " ml)";
                        double score = deficit / 250.0; // thiếu càng nhiều, score càng cao
                        scored.add(new ScoredWarn(msg, score));
                    }
                }
                d = d.plusDays(1);
            }

            List<String> topMsgs = scored.stream()
                    .sorted((a, b) -> Double.compare(b.score, a.score))
                    .limit(Math.max(0, topPerWeek))
                    .map(sw -> shortenWarn(sw.text()))
                    .toList();

            String period = w.start() + "–" + w.end();
            StringBuilder sb = new StringBuilder("Tuần " + (idx++) + " (" + period + "): ");
            if (noFoodLogDays > 0) sb.append(noFoodLogDays).append(" ngày không có log ăn uống; ");
            if (noWaterLogDays > 0) sb.append(noWaterLogDays).append(" ngày không có log nước; ");
            if (!topMsgs.isEmpty()) sb.append(String.join(" | ", topMsgs));
            String line = sb.toString().trim();
            if (line.endsWith(";")) line = line.substring(0, line.length() - 1);
            lines.add(line);
        }
        return lines;
    }


    public static String shortenWarn(String full) {
        int idx = full.indexOf(':');
        return (idx > 0 && idx + 1 < full.length()) ? full.substring(idx + 1).trim() : full;
    }
}
