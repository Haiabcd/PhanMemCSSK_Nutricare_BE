package com.hn.nutricarebe.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnalyzeByUrlRequest {
    @NotNull(message = "Ảnh món ăn là bắt buộc")
    MultipartFile image;
//    String mealSlot;
}
