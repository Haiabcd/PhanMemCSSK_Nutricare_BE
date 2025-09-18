package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.ConditionCreationRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
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
    public ApiResponse<Condition> saveCondition(@Valid @RequestBody ConditionCreationRequest request, BindingResult result){
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        if (result.hasErrors()) {
            result.getFieldErrors().forEach(error -> {
                errors.put(error.getField(), error.getDefaultMessage());
            });
            return ApiResponse.<Condition>builder()
                    .message("Lỗi dữ liệu đầu vào")
                    .errors(errors)
                    .build();
        }
       return ApiResponse.<Condition>builder()
                .message("Thêm bệnh nền thành công")
                .data(conditionService.save(request))
                .build();
    }
}
