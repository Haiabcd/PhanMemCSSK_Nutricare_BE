package com.hn.nutricarebe.helper;

import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.service.impl.StatisticsServiceImpl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

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

    // === Chính sách cảnh báo mới:
    // - KHÔNG cảnh báo sugar (đường)
    // - Sodium chỉ cảnh báo khi DƯ; thiếu sodium thì bỏ qua
    public static String compareDay(DayTarget target, DayConsumedTotal consumed) {
        var t = target.getTarget();
        var c = consumed.getTotal();

        double dKcal  = safe(c.getKcal())     - safe(t.getKcal());
        double dProt  = safe(c.getProteinG()) - safe(t.getProteinG());
        double dCarb  = safe(c.getCarbG())    - safe(t.getCarbG());
        double dFat   = safe(c.getFatG())     - safe(t.getFatG());
        double dFiber = safe(c.getFiberG())   - safe(t.getFiberG());
        double dNa    = safe(c.getSodiumMg()) - safe(t.getSodiumMg());

        StringBuilder sb = new StringBuilder();

        if (!withinKcal(t.getKcal(), dKcal))  sb.append(dKcal < 0 ? "Thiếu kcal " + fmt(-dKcal) : "Dư kcal " + fmt(dKcal)).append("; ");
        if (!withinG(t.getProteinG(), dProt)) sb.append(dProt < 0 ? "Thiếu protein " + fmt(-dProt) : "Dư protein " + fmt(dProt)).append("; ");
        if (!withinG(t.getCarbG(), dCarb))    sb.append(dCarb < 0 ? "Thiếu carb " + fmt(-dCarb) : "Dư carb " + fmt(dCarb)).append("; ");
        if (!withinG(t.getFatG(), dFat))      sb.append(dFat < 0 ? "Thiếu fat " + fmt(-dFat) : "Dư fat " + fmt(dFat)).append("; ");
        if (!withinG(t.getFiberG(), dFiber))  sb.append(dFiber < 0 ? "Thiếu fiber " + fmt(-dFiber) : "Dư fiber " + fmt(dFiber)).append("; ");

        // Sodium: chỉ cảnh báo khi DƯ và vượt ngưỡng
        if (dNa > 0 && !withinSodium(t.getSodiumMg(), dNa)) {
            sb.append("Dư sodium ").append(fmt(dNa)).append("; ");
        }

        // Sugar: KHÔNG cảnh báo → bỏ hẳn

        if (sb.isEmpty()) return null;
        return "Ngày " + target.getDate() + ": " + sb.toString().trim();
    }

    public static boolean withinKcal(Number tgt, double d)   { return Math.abs(d) <= Math.max(60,  safe(tgt) * 0.07); }
    public static boolean withinG(Number tgt, double d)      { return Math.abs(d) <= Math.max(5,   safe(tgt) * 0.07); }
    public static boolean withinSodium(Number tgt, double d) { return Math.abs(d) <= Math.max(200, safe(tgt) * 0.07); }

    public static double safe(Number n) { return n == null ? 0.0 : n.doubleValue(); }
    public static String fmt(double v) { return String.valueOf(Math.round(v * 10.0) / 10.0); }

    public static List<MonthlyWeeklyNutritionDto> aggregateMonthByWeeks(
            List<DailyNutritionDto> daily, LocalDate monthStart, LocalDate monthEnd) {

        List<StatisticsServiceImpl.WeekRange> weeks = splitMonthIntoWeeksMonSun(monthStart, monthEnd);

        Map<LocalDate, DailyNutritionDto> byDate = daily.stream()
                .collect(java.util.stream.Collectors.toMap(DailyNutritionDto::getDate, d -> d, (a,b)->b));

        List<MonthlyWeeklyNutritionDto> out = new java.util.ArrayList<>();
        int idx = 1;
        for (StatisticsServiceImpl.WeekRange w : weeks) {
            java.math.BigDecimal prot = java.math.BigDecimal.ZERO;
            java.math.BigDecimal carb = java.math.BigDecimal.ZERO;
            java.math.BigDecimal fat  = java.math.BigDecimal.ZERO;
            java.math.BigDecimal fiber= java.math.BigDecimal.ZERO;
            int daysWithLogs = 0;

            LocalDate d = w.start();
            while (!d.isAfter(w.end())) {
                DailyNutritionDto dn = byDate.get(d);
                if (dn != null) {
                    boolean hasAny =
                            nz(dn.getProteinG()).signum()!=0 ||
                                    nz(dn.getCarbG()).signum()!=0    ||
                                    nz(dn.getFatG()).signum()!=0     ||
                                    nz(dn.getFiberG()).signum()!=0;
                    if (hasAny) daysWithLogs++;

                    prot  = prot.add(nz(dn.getProteinG()));
                    carb  = carb.add(nz(dn.getCarbG()));
                    fat   = fat.add(nz(dn.getFatG()));
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

    public static List<StatisticsServiceImpl.WeekRange> splitMonthIntoWeeksMonSun(LocalDate monthStart, LocalDate monthEnd) {
        LocalDate cursor = monthStart.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endCap = monthEnd.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<StatisticsServiceImpl.WeekRange> list = new java.util.ArrayList<>();
        while (!cursor.isAfter(endCap)) {
            LocalDate ws = cursor;
            LocalDate we = cursor.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            LocalDate clippedStart = ws.isBefore(monthStart) ? monthStart : ws;
            LocalDate clippedEnd   = we.isAfter(monthEnd)    ? monthEnd   : we;

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
            int noLogDays = 0;

            LocalDate d = w.start();
            while (!d.isAfter(w.end())) {
                DayTarget t = targetByDate.get(d);
                if (t != null) {
                    DayConsumedTotal c = consumedMap.get(d);
                    if (c == null) {
                        noLogDays++;
                        scored.add(new ScoredWarn("Không có log", 0.5));
                    } else {
                        String msg = compareDay(t, c);
                        if (msg != null) {
                            var tv = t.getTarget();
                            var cv = c.getTotal();
                            double dKcal  = safe(cv.getKcal())     - safe(tv.getKcal());
                            double dProt  = safe(cv.getProteinG()) - safe(tv.getProteinG());
                            double dCarb  = safe(cv.getCarbG())    - safe(tv.getCarbG());
                            double dFat   = safe(cv.getFatG())     - safe(tv.getFatG());
                            double dFiber = safe(cv.getFiberG())   - safe(tv.getFiberG());
                            double dNa    = safe(cv.getSodiumMg()) - safe(tv.getSodiumMg());
                            // double dSug = safe(cv.getSugarMg()) - safe(tv.getSugarMg()); // bỏ sugar

                            // Điểm: bỏ sugar; sodium chỉ tính khi DƯ
                            double score = Math.abs(dKcal)/100.0
                                    + Math.abs(dProt)/5.0
                                    + Math.abs(dCarb)/10.0
                                    + Math.abs(dFat)/5.0
                                    + Math.abs(dFiber)/5.0
                                    + (dNa > 0 ? Math.abs(dNa)/300.0 : 0.0);

                            scored.add(new ScoredWarn(msg, score));
                        }
                    }
                }
                d = d.plusDays(1);
            }

            List<String> topMsgs = scored.stream()
                    .sorted((a,b) -> Double.compare(b.score, a.score))
                    .limit(Math.max(0, topPerWeek))
                    .map(sw -> shortenWarn(sw.text()))
                    .toList();

            String period = w.start() + "–" + w.end();
            StringBuilder sb = new StringBuilder("Tuần " + (idx++) + " (" + period + "): ");
            if (noLogDays > 0) sb.append(noLogDays).append(" ngày không có log; ");
            if (!topMsgs.isEmpty()) sb.append(String.join(" | ", topMsgs));
            String line = sb.toString().trim();
            if (line.endsWith(";")) line = line.substring(0, line.length()-1);
            lines.add(line);
        }
        return lines;
    }

    public static String shortenWarn(String full) {
        int idx = full.indexOf(':');
        return (idx > 0 && idx+1 < full.length()) ? full.substring(idx+1).trim() : full;
    }
}
