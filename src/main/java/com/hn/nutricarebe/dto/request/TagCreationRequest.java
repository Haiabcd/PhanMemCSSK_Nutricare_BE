package com.hn.nutricarebe.dto.request;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TagCreationRequest {
    @NotNull(message = "Mã tag không được để trống")
    String nameCode;
    String description;
}
