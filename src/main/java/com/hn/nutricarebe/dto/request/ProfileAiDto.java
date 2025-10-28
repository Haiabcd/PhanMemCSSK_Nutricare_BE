package com.hn.nutricarebe.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileAiDto {
    List<String> conditions;  // tên bệnh nền
    List<String> allergies;   // chất/nhóm dị ứng
    Integer age;
    Double heightCm;
    Double weightKg;
    String goal; // ví dụ: "giảm cân 0.5 kg/tuần"
}
