package com.hn.nutricarebe.dto.ai;

import com.hn.nutricarebe.entity.Nutrition;
import jakarta.annotation.Nullable;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

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
