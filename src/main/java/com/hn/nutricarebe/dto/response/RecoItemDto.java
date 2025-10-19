package com.hn.nutricarebe.dto.response;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecoItemDto {
    private String type;       // "article" | "video"
    private String title;
    private String url;        // link gốc
    private String source;     // ví dụ: "who.int", "cdc.gov"
    private String imageUrl;   // thumbnail nếu có (YouTube)
    private Instant published;
}
