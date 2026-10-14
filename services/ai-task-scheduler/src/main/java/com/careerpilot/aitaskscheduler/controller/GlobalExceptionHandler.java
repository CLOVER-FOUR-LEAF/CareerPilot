package com.careerpilot.aitaskscheduler.controller;

import com.careerpilot.aitaskscheduler.dto.ApiResponse;
import com.careerpilot.aitaskscheduler.service.AiTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把异常收敛成统一包络，而不是让 Spring 默认的 Whitelabel 页面漏到前端。
 */
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

    @ExceptionHandler(AiTaskService.TaskNotFoundException.class)
    public ApiResponse<Void> handleNotFound(AiTaskService.TaskNotFoundException e) {
        return ApiResponse.error(1404, e.getMessage());
    }

    @ExceptionHandler(AiTaskService.UnsupportedTaskTypeException.class)
    public ApiResponse<Void> handleUnsupported(AiTaskService.UnsupportedTaskTypeException e) {
        return ApiResponse.error(1400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return ApiResponse.error(1500, "服务内部错误");
    }
}
