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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/conditions")
public class ConditionController {
    ConditionService conditionService;

    // Tạo bệnh nền
    @PostMapping("/save")
    public ApiResponse<ConditionResponse> saveCondition(@Valid @RequestBody ConditionCreationRequest request){
        return ApiResponse.<ConditionResponse>builder()
                .message("Tạo bệnh nền thành công")
                .data(conditionService.save(request))
                .build();
    }

    // Lấy danh sách bệnh nền
    @GetMapping("/all")
    public ApiResponse<Slice<ConditionResponse>> getAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.<Slice<ConditionResponse>>builder()
                .message("Lấy danh sách bệnh nền thành công")
                .data(conditionService.getAll(pageable))
                .build();
    }

    // Lấy bệnh nền theo ID
    @GetMapping("/{id}")
    public ApiResponse<ConditionResponse> getById(@PathVariable UUID id) {
        return ApiResponse.<ConditionResponse>builder()
                .message("Lấy bệnh nền thành công")
                .data(conditionService.getById(id))
                .build();
    }
}
