package com.careerpilot.resumeservice.controller;

import com.careerpilot.resumeservice.dto.ApiResponse;
import com.careerpilot.resumeservice.service.ResumeService;
import com.careerpilot.resumeservice.service.ResumeTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @ExceptionHandler(ResumeService.ResumeNotFoundException.class)
    public ApiResponse<Void> handleNotFound(ResumeService.ResumeNotFoundException e) {
        return ApiResponse.error(1404, "简历不存在或无权访问");
    }

    /** 格式不支持 / 抽不出文字：都是用户可操作的问题，给 1400 而不是 1500。 */
    @ExceptionHandler({ResumeService.ResumeUploadException.class,
            ResumeTextExtractor.UnsupportedResumeFormatException.class,
            ResumeTextExtractor.EmptyResumeTextException.class})
    public ApiResponse<Void> handleUpload(RuntimeException e) {
        return ApiResponse.error(1400, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleTooLarge(MaxUploadSizeExceededException e) {
        return ApiResponse.error(1400, "文件超过大小限制，请上传 10MB 以内的简历");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return ApiResponse.error(1500, "服务内部错误");
    }
}
