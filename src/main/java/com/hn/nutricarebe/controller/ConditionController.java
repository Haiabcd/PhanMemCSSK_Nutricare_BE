package com.hn.nutricarebe.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import com.hn.nutricarebe.dto.request.ConditionRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.ConditionResponse;
import com.hn.nutricarebe.service.ConditionService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/conditions")
public class ConditionController {
    ConditionService conditionService;

    // Tạo bệnh nền
    @PostMapping("/save")
    public ApiResponse<Void> saveCondition(@Valid @RequestBody ConditionRequest request) {
        conditionService.save(request);
        return ApiResponse.<Void>builder().message("Tạo bệnh nền thành công").build();
    }

    // Lấy danh sách bệnh nền
    @GetMapping("/all")
    public ApiResponse<Slice<ConditionResponse>> getAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
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

    // Tìm kiếm bệnh nền theo tên
    @GetMapping("/search")
    public ApiResponse<Slice<ConditionResponse>> searchByName(
            @RequestParam("name") String name,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.<Slice<ConditionResponse>>builder()
                .message("Tìm bệnh nền theo tên thành công")
                .data(conditionService.searchByName(name, pageable))
                .build();
    }

    // Xoá bệnh nền theo ID
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCondition(@PathVariable UUID id) {
        conditionService.deleteById(id);
        return ApiResponse.<Void>builder().message("Xoá bệnh nền thành công").build();
    }

    // Cập nhật bệnh nền
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable UUID id, @Valid @RequestBody ConditionRequest request) {
        conditionService.update(id, request);
        return ApiResponse.<Void>builder()
                .message("Cập nhật bệnh nền thành công")
                .build();
    }
}
