package com.hn.nutricarebe.dto.request;


import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SaveLogRequest {
    @NotNull(message = "Mã kế hoạch bữa ăn không được để trống")
    UUID mealPlanItemId;
}
