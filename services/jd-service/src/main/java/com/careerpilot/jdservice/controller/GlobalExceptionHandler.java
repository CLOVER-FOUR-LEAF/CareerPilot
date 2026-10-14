package com.careerpilot.jdservice.controller;

import com.careerpilot.jdservice.dto.ApiResponse;
import com.careerpilot.jdservice.service.JdService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("参数校验失败");
        return ApiResponse.error(1400, msg);
    }

    @ExceptionHandler(JdService.JdNotFoundException.class)
    public ApiResponse<Void> handleNotFound(JdService.JdNotFoundException e) {
        return ApiResponse.error(1404, "JD 不存在或无权访问");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return ApiResponse.error(1500, "服务内部错误");
    }
}
