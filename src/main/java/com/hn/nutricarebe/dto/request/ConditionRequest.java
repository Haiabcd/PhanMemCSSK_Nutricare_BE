package com.hn.nutricarebe.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConditionRequest {
    @NotBlank(message = "Tên bệnh nền không được để trống")
    String name;
}
