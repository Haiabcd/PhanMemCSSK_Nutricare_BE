package com.hn.nutricarebe.dto.ai;

import jakarta.annotation.Nullable;

import org.springframework.web.multipart.MultipartFile;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatRequest {
    String message;

    @Nullable
    MultipartFile file;
}
