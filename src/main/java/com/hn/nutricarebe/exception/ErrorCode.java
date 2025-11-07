package com.hn.nutricarebe.exception;

import org.springframework.http.HttpStatus;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode {
    ALLERGY_EXISTED(1001, "Dị ứng đã tồn tại", HttpStatus.CONFLICT),
    ALLERGY_NOT_FOUND(1002, "Dị ứng không tồn tại", HttpStatus.NOT_FOUND),
    DELETE_ALLERGY_CONFLICT(1003, "Dị ứng đã được gán cho người dùng, không thể xóa.", HttpStatus.CONFLICT),

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
    DELETE_CONDITION_CONFLICT(7003, "Bệnh nền đã được gán cho người dùng, không thể xóa.", HttpStatus.CONFLICT),

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

    NOT_FOUND_PLAN_LOG(9012, "Log không tồn tại", HttpStatus.NOT_FOUND),

    NUTRITION_RULE_NOT_FOUND(9012, "Quy tắc dinh dưỡng không tồn tại", HttpStatus.NOT_FOUND),
    INVALID_ARGUMENT(9013, "Tham số không hợp lệ", HttpStatus.BAD_REQUEST),

    MEAL_PLAN_NOT_FOUND(10001, "Kế hoạch không tồn tại", HttpStatus.NOT_FOUND),
    MEAL_PLAN_ITEM_USED(10002, "Món ăn trong kế hoạch đã được sử dụng", HttpStatus.BAD_REQUEST),

    THIRD_PARTY_ERROR(10005, "LogMeal: missing imageId", HttpStatus.BAD_REQUEST),
    PROVIDER_ALREADY_LINKED(11001, "Tài khoản google đã được liên kết với tài khoản khác", HttpStatus.CONFLICT),
    CONVERSION_REQUIRES_DENSITY(
            11002, "Không thể quy đổi giữa khối lượng và thể tích khi thiếu mật độ (density).", HttpStatus.BAD_REQUEST),
    AI_SERVICE_ERROR(12001, "AI đang quá tải. Vui lòng thử lại sau ít phút.", HttpStatus.INTERNAL_SERVER_ERROR),

    USER_NAME_NOT_FOUND(13001, "Tên đăng nhập không đúng", HttpStatus.NOT_FOUND),
    PASSWORD_INCORRECT(13002, "Mật khẩu không đúng", HttpStatus.UNAUTHORIZED),
    USERNAME_EXISTED(13003, "Tên đăng nhập đã tồn tại", HttpStatus.CONFLICT),
    PASSWORD_SAME_AS_OLD(13004, "Mật khẩu mới không được giống mật khẩu cũ", HttpStatus.BAD_REQUEST);
    int code;
    String message;
    HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
