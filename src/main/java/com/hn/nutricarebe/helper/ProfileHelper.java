package com.hn.nutricarebe.helper;

import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.enums.GoalType;

public final class ProfileHelper {
    private ProfileHelper() {}

    public static String buildGoalText(Profile p) {
        if (p.getGoal() == null) return null;

        // MAINTAIN: không cần delta/tuần
        if (p.getGoal() == GoalType.MAINTAIN) {
            return "Duy trì cân nặng hiện tại";
        }

        // GAIN/LOSE: cố gắng sử dụng delta và số tuần nếu có
        Integer delta = p.getTargetWeightDeltaKg();
        Integer weeks = p.getTargetDurationWeeks();

        String action = (p.getGoal() == GoalType.GAIN) ? "Tăng" : "Giảm";
        if (delta == null && weeks == null) {
            // chỉ biết mục tiêu chung
            return action + " cân (chưa có mục tiêu chi tiết)";
        }
        if (delta != null && weeks != null && weeks > 0) {
            double kg = Math.abs(delta.doubleValue());
            double rate = kg / weeks;
            return String.format("%s %.0f kg trong %d tuần (≈ %.2f kg/tuần)", action, kg, weeks, rate);
        }
        if (delta != null) {
            double kg = Math.abs(delta.doubleValue());
            return String.format("%s %.0f kg", action, kg);
        }
        // weeks != null nhưng chưa có delta
        return String.format("%s cân trong %d tuần", action, weeks);
    }
}
