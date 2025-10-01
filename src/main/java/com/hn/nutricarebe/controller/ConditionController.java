package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.ConditionCreationRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.ConditionResponse;
import com.hn.nutricarebe.service.ConditionService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/conditions")
public class ConditionController {
    ConditionService conditionService;

    @PostMapping("/save")
    public ApiResponse<ConditionResponse> saveCondition(@Valid @RequestBody ConditionCreationRequest request){
        return ApiResponse.<ConditionResponse>builder()
                .message("Tạo bệnh nền thành công")
                .data(conditionService.save(request))
                .build();
    }
}
