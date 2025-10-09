package com.hn.nutricarebe.exception;

import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import org.springframework.http.HttpStatus;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode {
    ALLERGY_EXISTED(1001, "Dị ứng đã tồn tại", HttpStatus.CONFLICT),
    ALLERGY_NOT_FOUND(1002, "Dị ứng không tồn tại", HttpStatus.NOT_FOUND),
    DELETE_ALLERGY_CONFLICT(1003, "Không thể xóa dị ứng", HttpStatus.CONFLICT),

    VALIDATION_FAILED(2001, "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),

    USER_NOT_FOUND(3001, "Người dùng không tồn tại", HttpStatus.NOT_FOUND),
    DEVICE_ID_EXISTED(3002, "Device ID đã tồn tại", HttpStatus.CONFLICT),
    USERID_EXISTED(3003, "User ID đã tồn tại", HttpStatus.CONFLICT),

    PROFILE_NOT_FOUND(4001, "Hồ sơ không tồn tại", HttpStatus.NOT_FOUND),

    FOOD_NAME_EXISTED(5001, "Món ăn đã tồn tại", HttpStatus.CONFLICT),
    FOOD_NOT_FOUND(5002, "Món ăn không tồn tại", HttpStatus.NOT_FOUND),
    DELETE_CONFLICT(5003, "Không thể xóa món ăn", HttpStatus.CONFLICT),
    NAME_EMPTY(5004, "Tên không được ngắn hơn 2 ký tự", HttpStatus.BAD_REQUEST),

    INGREDIENT_NAME_EXISTED(6001, "Nguyên liệu đã tồn tại", HttpStatus.CONFLICT),
    INGREDIENT_NOT_FOUND(6002, "Nguyên liệu không tồn tại", HttpStatus.NOT_FOUND),
    DELETE_INGREDIENT_CONFLICT(6003, "Không thể xóa nguyên liệu", HttpStatus.CONFLICT),

    CONDITION_EXISTED(7001, "Bệnh nền đã tồn tại", HttpStatus.CONFLICT),
    CONDITION_NOT_FOUND(7002, "Bệnh nền không tồn tại", HttpStatus.NOT_FOUND),
    DELETE_CONDITION_CONFLICT(7003, "Không thể xóa bệnh nền", HttpStatus.CONFLICT),

    FILE_EMPTY(8001, "File rỗng hoặc không tồn tại", HttpStatus.BAD_REQUEST),
    FILE_UPLOAD_FAILED(8002, "Tải file lên thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
    DELETE_OBJECT_FAILED(8004, "Xoá file S3 thất bại", HttpStatus.INTERNAL_SERVER_ERROR),

    INVALID_OR_EXPIRED_STATE(9001, "State không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),


    INTERNAL_ERROR(9002, "Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),

    UNAUTHENTICATED(9003, "Vui lòng đăng nhập", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(9004, "Bạn không có quyền thực hiện hành động này", HttpStatus.FORBIDDEN),

    TOKEN_EXCHANGE_FAILED(9005, "Lấy token thất bại", HttpStatus.BAD_REQUEST),
    INVALID_TOKEN(9006, "Token không hợp lệ", HttpStatus.UNAUTHORIZED),
    INVALID_SIGNATURE(9007, "Chữ ký token không hợp lệ", HttpStatus.UNAUTHORIZED),
    EXPIRED_REFRESH_TOKEN(9008, "Refresh token đã hết hạn", HttpStatus.UNAUTHORIZED),
    EXPIRED_ACCESS_TOKEN(9009, "Access token đã hết hạn", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_NOT_FOUND(9010, "Refresh token không tồn tại", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED(9011, "Refresh token đã được sử dụng", HttpStatus.UNAUTHORIZED),
    ;
    int code;
    String message;
    HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
