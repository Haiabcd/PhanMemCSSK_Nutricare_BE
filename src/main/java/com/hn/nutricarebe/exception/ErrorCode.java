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
    CONDITION_EXISTED(1008, "Bệnh nền đã tồn tại", HttpStatus.CONFLICT),
    FILE_EMPTY(1009, "File rỗng hoặc không tồn tại", HttpStatus.BAD_REQUEST),
    FILE_UPLOAD_FAILED(1010, "Tải file lên thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR(2001, "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
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
