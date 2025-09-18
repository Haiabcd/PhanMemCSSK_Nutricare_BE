package com.hn.nutricarebe.exception;

import com.hn.nutricarebe.dto.response.ApiResponse;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Business exception
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        ErrorCode ec = ex.getErrorCode();

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .code(ec.getCode())
                .message(ec.getMessage())
                .build();

        return ResponseEntity.status(ec.getHttpStatus()).body(body);
    }

    // Bean Validation (@Valid) trên @RequestBody
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        LinkedHashMap::new, // giữ thứ tự field
                        Collectors.mapping(DefaultMessageSourceResolvable::getDefaultMessage, Collectors.toList())
                ));

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .code(1001) // gán mã lỗi riêng cho validation
                .message("Dữ liệu không hợp lệ")
                .errors(errors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }
}
