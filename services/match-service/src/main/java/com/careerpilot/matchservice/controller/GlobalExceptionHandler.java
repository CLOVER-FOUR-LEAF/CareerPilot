package com.careerpilot.matchservice.controller;

import com.careerpilot.matchservice.client.MatchSourceClient;
import com.careerpilot.matchservice.dto.ApiResponse;
import com.careerpilot.matchservice.service.HrInsightService;
import com.careerpilot.matchservice.service.MatchService;
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

    @ExceptionHandler(MatchService.MatchRecordNotFoundException.class)
    public ApiResponse<Void> handleNotFound(MatchService.MatchRecordNotFoundException e) {
        return ApiResponse.error(1404, "匹配记录不存在或无权访问");
    }

    /** HR 洞察报告不存在或越权 —— 与「不存在」返回同一个码，不泄露资源是否存在。 */
    @ExceptionHandler(HrInsightService.HrInsightNotFoundException.class)
    public ApiResponse<Void> handleInsightNotFound(HrInsightService.HrInsightNotFoundException e) {
        return ApiResponse.error(1404, "HR 洞察报告不存在或无权访问");
    }

    /** 素材不存在或越权 —— 和「不存在」返回同一个码，不泄露资源是否存在。 */
    @ExceptionHandler(MatchSourceClient.SourceNotFoundException.class)
    public ApiResponse<Void> handleSourceNotFound(MatchSourceClient.SourceNotFoundException e) {
        return ApiResponse.error(1404, e.getMessage());
    }

    /** 依赖服务不可用，用 1501 和 1500 区分开，前端能给不同提示。 */
    @ExceptionHandler(MatchSourceClient.SourceUnavailableException.class)
    public ApiResponse<Void> handleSourceDown(MatchSourceClient.SourceUnavailableException e) {
        return ApiResponse.error(1501, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return ApiResponse.error(1500, "服务内部错误");
    }
}
