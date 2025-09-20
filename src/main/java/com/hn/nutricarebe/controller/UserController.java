package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.UserCreationRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.UserCreationResponse;
import com.hn.nutricarebe.service.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController {
    UserService userService;

//    @PostMapping("/save")
//    public ApiResponse<UserCreationResponse> save(@Valid @RequestBody UserCreationRequest request){
//
//        return ApiResponse.<UserCreationResponse>builder()
//                .message("Tạo người dùng thành công")
//                .data(userService.save(request))
//                .build();
//    }
}
