package com.hn.nutricarebe.controller;

import org.springframework.web.bind.annotation.*;

import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.HeaderResponse;
import com.hn.nutricarebe.dto.response.InfoResponse;
import com.hn.nutricarebe.service.UserService;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController {
    UserService userService;

    @GetMapping("/my-info")
    public ApiResponse<InfoResponse> getMyInfo() {
        return ApiResponse.<InfoResponse>builder()
                .message("Lấy thông tin người dùng thành công")
                .data(userService.getUserByToken())
                .build();
    }

    @GetMapping("/header")
    public ApiResponse<HeaderResponse> getHeader() {
        return ApiResponse.<HeaderResponse>builder()
                .message("Lấy thông tin header thành công")
                .data(userService.getHeader())
                .build();
    }
}
