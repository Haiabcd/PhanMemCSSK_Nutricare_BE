package com.hn.nutricarebe.exception;

import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import org.springframework.http.HttpStatus;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode {
    ALLERGY_EXISTED(1001, "Allergy existed", HttpStatus.CONFLICT),
    VALIDATION_FAILED(1002, "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(1003, "Người dùng không tồn tại", HttpStatus.NOT_FOUND),
    FOOD_NAME_EXISTED(1004, "Món ăn đã tồn tại", HttpStatus.CONFLICT),
    INGREDIENT_NAME_EXISTED(1005, "Nguyên liệu đã tồn tại", HttpStatus.CONFLICT),
    DEVICE_ID_EXISTED(1006, "Device ID đã tồn tại", HttpStatus.CONFLICT),
    USERID_EXISTED(1007, "User ID đã tồn tại", HttpStatus.CONFLICT),
    INTERNAL_ERROR(1999, "Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR);

    int code;
    String message;
    HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
