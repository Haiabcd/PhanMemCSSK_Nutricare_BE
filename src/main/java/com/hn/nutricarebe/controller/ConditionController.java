package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.ConditionCreationRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.ConditionResponse;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.service.ConditionService;
import com.hn.nutricarebe.service.impl.ConditionServiceImpl;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/conditions")
public class ConditionController {
    ConditionService conditionService;

    @GetMapping()
    public ApiResponse<List<Condition>> getAllCondition(){
        return ApiResponse.<List<Condition>>builder()
                .message("Lấy danh sách bệnh nền thành công")
                .data(conditionService.findAll())
                .build();
    }

    @PostMapping("/save")
    public ApiResponse<ConditionResponse> saveCondition(@Valid @RequestBody ConditionCreationRequest request){
        return ApiResponse.<ConditionResponse>builder()
                .message("Tạo bệnh nền thành công")
                .data(conditionService.save(request))
                .build();
    }
}
