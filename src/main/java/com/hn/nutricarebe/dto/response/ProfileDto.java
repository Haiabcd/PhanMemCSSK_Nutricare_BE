package com.hn.nutricarebe.dto.response;

import java.util.List;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileDto {
    private String goal; // "giảm cân" | "tăng cơ" | ...
    private String activity; // "sedentary" | "light" | "moderate" | "active"
    private Integer age;
    private Double heightCm;
    private Double weightKg;
    private List<String> conditions; // bệnh nền
    private List<String> allergies; // dị ứng
    private String locale; // "vi" | "en"
}
