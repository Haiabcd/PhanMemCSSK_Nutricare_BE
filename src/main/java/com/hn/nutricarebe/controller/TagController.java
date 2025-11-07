package com.hn.nutricarebe.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import com.hn.nutricarebe.dto.request.TagCreationRequest;
import com.hn.nutricarebe.dto.request.TagDto;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.service.TagService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequiredArgsConstructor
@RequestMapping("/tags")
public class TagController {
    TagService tagService;

    @GetMapping("/autocomplete")
    public List<TagDto> autocomplete(
            @RequestParam("q") String q, @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return tagService.autocomplete(q, limit);
    }

    @PostMapping(value = "/save")
    public ApiResponse<Void> createFood(@Valid @RequestBody TagCreationRequest request) {
        tagService.save(request);
        return ApiResponse.<Void>builder().message("Tạo tag thành công").build();
    }
}
