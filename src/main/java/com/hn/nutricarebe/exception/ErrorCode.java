package com.hn.nutricarebe.exception;

import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import org.springframework.http.HttpStatus;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode {
    ALLERGY_EXISTED(1001, "Dị ứng đã tồn tại", HttpStatus.CONFLICT),
    ALLERGY_NOT_FOUND(1018, "Dị ứng không tồn tại", HttpStatus.NOT_FOUND),
    DELETE_ALLERGY_CONFLICT(1014, "Không thể xóa dị ứng", HttpStatus.CONFLICT),

    VALIDATION_FAILED(1002, "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(1003, "Người dùng không tồn tại", HttpStatus.NOT_FOUND),

    PROFILE_NOT_FOUND(1020, "Hồ sơ không tồn tại", HttpStatus.NOT_FOUND),

    FOOD_NAME_EXISTED(1004, "Món ăn đã tồn tại", HttpStatus.CONFLICT),
    FOOD_NOT_FOUND(1012, "Món ăn không tồn tại", HttpStatus.NOT_FOUND),
    DELETE_CONFLICT(1014, "Không thể xóa món ăn", HttpStatus.CONFLICT),
    NAME_EMPTY(1016, "Tên không được ngắn hơn 2 ký tự", HttpStatus.BAD_REQUEST),

    INGREDIENT_NAME_EXISTED(1005, "Nguyên liệu đã tồn tại", HttpStatus.CONFLICT),
    INGREDIENT_NOT_FOUND(1013, "Nguyên liệu không tồn tại", HttpStatus.NOT_FOUND),
    DELETE_INGREDIENT_CONFLICT(1015, "Không thể xóa nguyên liệu", HttpStatus.CONFLICT),

    DEVICE_ID_EXISTED(1006, "Device ID đã tồn tại", HttpStatus.CONFLICT),
    USERID_EXISTED(1007, "User ID đã tồn tại", HttpStatus.CONFLICT),

    CONDITION_EXISTED(1008, "Bệnh nền đã tồn tại", HttpStatus.CONFLICT),
    CONDITION_NOT_FOUND(1017, "Bệnh nền không tồn tại", HttpStatus.NOT_FOUND),
    DELETE_CONDITION_CONFLICT(1019, "Không thể xóa bệnh nền", HttpStatus.CONFLICT),

    FILE_EMPTY(1009, "File rỗng hoặc không tồn tại", HttpStatus.BAD_REQUEST),
    FILE_UPLOAD_FAILED(1010, "Tải file lên thất bại", HttpStatus.INTERNAL_SERVER_ERROR),

    DELETE_OBJECT_FAILED(1011, "Xoá file S3 thất bại", HttpStatus.INTERNAL_SERVER_ERROR),

    INVALID_OR_EXPIRED_STATE(2000, "State không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    TOKEN_EXCHANGE_FAILED(2002, "Lấy token thất bại", HttpStatus.BAD_REQUEST),

    VALIDATION_ERROR(2001, "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(1999, "Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),

    UNAUTHENTICATED(1006, "Vui lòng đăng nhập", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007, "Bạn không có quyền thực hiện hành động này", HttpStatus.FORBIDDEN),
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
