package com.careerpilot.jdservice.controller;

import com.careerpilot.jdservice.dto.ApiResponse;
import com.careerpilot.jdservice.dto.InternalCallbackRequest;
import com.careerpilot.jdservice.service.JdCallbackService;
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
 * 生产环境还应该在内网做一次来源 IP 限制。
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class JdCallbackController {

    private final JdCallbackService callbackService;

    @PostMapping("/jd-parse-callback")
    public ApiResponse<Void> onJdParsed(@RequestBody InternalCallbackRequest request) {
        log.info("收到 JD 解析回调: taskId={}, jdId={}, success={}",
                request.getTaskId(), request.getBizId(), request.isSuccess());

        callbackService.handleCallback(
                request.getBizId(),
                request.isSuccess(),
                request.getOutput(),
                request.getErrorMessage());

        return ApiResponse.success();
    }
}
