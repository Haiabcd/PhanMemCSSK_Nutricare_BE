package com.hn.nutricarebe.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WaterLogCreationRequest {
    @NotNull(message = "Thời điểm uống không được để trống")
    Instant drankAt;
    @Positive(message = "Lượng nước phải là số dương")
    @NotNull(message = "Lượng nước không được để trống")
    Integer amountMl;
}
