package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.AllergyRequest;
import com.hn.nutricarebe.dto.response.AllergyResponse;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.service.AllergyService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/allergies")
public class AllergyController {
    AllergyService allergyService;

    // Tạo dị ứng
    @PostMapping("/save")
    public ApiResponse<Void> saveAllergy(@Valid @RequestBody AllergyRequest request) {
        allergyService.save(request);
        return ApiResponse.<Void>builder()
                .message("Tạo dị ứng thành công")
                .build();
    }

    // Lấy danh sách dị ứng
    @GetMapping("/all")
    public ApiResponse<Slice<AllergyResponse>> getAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.<Slice<AllergyResponse>>builder()
                .message("Lấy danh sách dị ứng thành công")
                .data(allergyService.getAll(pageable))
                .build();
    }

    // Tìm kiếm dị ứng theo tên
    @GetMapping("/search")
    public ApiResponse<Slice<AllergyResponse>> searchByName(
            @RequestParam("name") String name,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.<Slice<AllergyResponse>>builder()
                .message("Tìm dị ứng theo tên thành công")
                .data(allergyService.searchByName(name, pageable))
                .build();
    }

    // Lấy dị ứng theo ID
    @GetMapping("/{id}")
    public ApiResponse<AllergyResponse> getById(@PathVariable UUID id) {
        return ApiResponse.<AllergyResponse>builder()
                .message("Lấy dị ứng thành công")
                .data(allergyService.getById(id))
                .build();
    }

    // Xoá dị ứng theo ID
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAllergy(@PathVariable UUID id) {
        allergyService.deleteById(id);
        return ApiResponse.<Void>builder()
                .message("Xoá dị ứng thành công")
                .build();
    }

    //Cập nhật dị ứng
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable UUID id,
            @Valid @RequestBody AllergyRequest request
    ) {
        allergyService.update(id, request);
        return ApiResponse.<Void>builder()
                .message("Cập nhật dị ứng thành công")
                .build();
    }
}
