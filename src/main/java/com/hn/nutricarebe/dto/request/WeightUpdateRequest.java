package com.hn.nutricarebe.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.Range;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WeightUpdateRequest {
    @NotNull(message = "Cân nặng là bắt buộc")
    @Range(min = 30, max = 200, message = "Cân nặng phải từ 30kg đến 200kg")
    Integer weightKg;
}
