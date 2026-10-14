package com.careerpilot.resumeservice.controller;

import com.careerpilot.resumeservice.dto.ApiResponse;
import com.careerpilot.resumeservice.dto.InternalCallbackRequest;
import com.careerpilot.resumeservice.service.ResumeCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部回调入口，**不经网关**，只允许 ai-task-scheduler 在内网调用。
 *
 * <p>路径挂在 {@code /internal} 下，网关没有这条路由，所以外部访问不到。
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class ResumeCallbackController {

    private final ResumeCallbackService callbackService;

    @PostMapping("/resume-analyze-callback")
    public ApiResponse<Void> onAnalyzed(@RequestBody InternalCallbackRequest request) {
        log.info("收到简历分析回调: taskId={}, resumeId={}, success={}",
                request.getTaskId(), request.getBizId(), request.isSuccess());

        callbackService.handleCallback(
                request.getBizId(),
                request.isSuccess(),
                request.getOutput(),
                request.getErrorMessage());

        return ApiResponse.success();
    }
}
