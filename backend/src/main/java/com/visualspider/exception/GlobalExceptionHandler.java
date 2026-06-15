package com.visualspider.exception;

import com.visualspider.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidUrl(InvalidUrlException e) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(StartUrlInvalidException.class)
    public ResponseEntity<ApiResponse<Void>> handleStartUrlInvalid(StartUrlInvalidException e) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BlockedAddressException.class)
    public ResponseEntity<ApiResponse<Void>> handleBlockedAddress(BlockedAddressException e) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(FetchTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleFetchTimeout(FetchTimeoutException e) {
        return buildErrorResponse(HttpStatus.GATEWAY_TIMEOUT, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(FetchFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleFetchFailed(FetchFailedException e) {
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("参数校验失败");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, 4001, message);
    }

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusinessException(BusinessException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BrowserSessionAlreadyActiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleBrowserSessionAlreadyActive(BrowserSessionAlreadyActiveException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(TaskAlreadyRunningException.class)
    public ResponseEntity<ApiResponse<Void>> handleTaskAlreadyRunning(TaskAlreadyRunningException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception e) {
        return ApiResponse.error(500, e.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(HttpStatus status, int code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }
}
