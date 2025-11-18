package com.hn.nutricarebe.helper;

import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.enums.ActivityLevel;
import com.hn.nutricarebe.enums.Gender;
import com.hn.nutricarebe.enums.GoalType;

public final class ProfileHelper {
    private ProfileHelper() {}

    public static String buildGoalText(Profile p) {
        if (p.getGoal() == null) return null;
        if (p.getGoal() == GoalType.MAINTAIN) {
            return "Duy trì cân nặng hiện tại";
        }
        Integer delta = p.getTargetWeightDeltaKg();
        Integer weeks = p.getTargetDurationWeeks();

        String action = (p.getGoal() == GoalType.GAIN) ? "Tăng" : "Giảm";
        if (delta == null && weeks == null) {
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
        return String.format("%s cân trong %d tuần", action, weeks);
    }

    public static String buildGenderText(Gender g) {
        if (g == null) return "Chưa xác định";
        switch (g) {
            case MALE -> {
                return "Nam";
            }
            case FEMALE -> {
                return "Nữ";
            }
            default -> {
                return "Khác";
            }
        }
    }

    public static String buildActivityLevel(ActivityLevel a) {
        if (a == null) return "Chưa xác định";
        switch (a) {
            case SEDENTARY -> {
                return "Ít vận động";
            }
            case LIGHTLY_ACTIVE -> {
                return "Vận động nhẹ";
            }
            case MODERATELY_ACTIVE -> {
                return "Vận động vừa phải";
            }
            case VERY_ACTIVE -> {
                return "Vận động nhiều";
            }
            case EXTRA_ACTIVE -> {
                return "Vận động rất nhiều";
            }
            default -> {
                return "Chưa xác định";
            }
        }
    }


}
