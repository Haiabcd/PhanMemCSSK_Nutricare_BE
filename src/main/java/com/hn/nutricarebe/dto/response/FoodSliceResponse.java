package com.hn.nutricarebe.dto.response;


import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FoodSliceResponse {
    List<FoodResponse> items;
    UUID nextCursorId;          // id cuối cùng của trang
    Instant nextCursorCreatedAt; // createdAt của item cuối
    boolean hasNext;
}
