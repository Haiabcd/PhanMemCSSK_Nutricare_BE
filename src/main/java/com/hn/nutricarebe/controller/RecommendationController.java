package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.RecoItemDto;
import com.hn.nutricarebe.service.RecommendationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequiredArgsConstructor
@RequestMapping("/recommendations")
public class RecommendationController {

    RecommendationService recommendationService;

    @PostMapping("/findNewsfeed")
    public ApiResponse<List<RecoItemDto>> find(
            @RequestParam(name = "limit", defaultValue = "0") int limit
    ) {
        int effective = (limit <= 0) ? Integer.MAX_VALUE : limit; // coi 0 là vô hạn
        return ApiResponse.<List<RecoItemDto>>builder()
                .data(recommendationService.find(effective))
                .build();
    }


}
