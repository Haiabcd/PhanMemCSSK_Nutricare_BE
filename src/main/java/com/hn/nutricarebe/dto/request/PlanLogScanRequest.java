package com.hn.nutricarebe.dto.request;

import com.hn.nutricarebe.enums.MealSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanLogScanRequest {
    @NotNull(message = "Ngày không được để trống")
    LocalDate date;                // map từ dateISO (YYYY-MM-DD)

    @NotNull(message = "Bữa ăn không được để trống")
    MealSlot mealSlot;             // BREAKFAST/LUNCH/DINNER/SNACK

    @NotBlank(message = "Tên món ăn không được để trống")
    String nameFood;

    @NotNull(message = "Số khẩu phần không được để trống")
    @DecimalMin(value = "0.01")
    BigDecimal consumedServings;

    @Valid
    NutritionRequest totalNutrition;

    List<PlanLogScanRequest.IngredientEntryDTO> ingredients;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class IngredientEntryDTO {
        private UUID id;
        private BigDecimal qty;

    }
}
