package com.hn.nutricarebe.dto.ai;

import jakarta.annotation.Nullable;

import org.springframework.web.multipart.MultipartFile;

import com.hn.nutricarebe.entity.Nutrition;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SuggestionAI {
    @Nullable
    MultipartFile image;

    String dishName;
    Nutrition nutrition;
}
