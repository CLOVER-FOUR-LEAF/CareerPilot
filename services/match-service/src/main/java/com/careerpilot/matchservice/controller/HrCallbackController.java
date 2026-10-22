package com.careerpilot.matchservice.controller;

import com.careerpilot.matchservice.dto.ApiResponse;
import com.careerpilot.matchservice.dto.InternalCallbackRequest;
import com.careerpilot.matchservice.service.HrInsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HR 侧内部回调入口，**不经网关**。
 *
 * <p>网关的 JwtAuthFilter 会拦掉所有 {@code /internal/**} 前缀的请求，
 * 所以这些接口只能由调度服务在服务内网调用。
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class HrCallbackController {

    private final HrInsightService hrInsightService;

    /** HR 洞察报告生成完成。 */
    @PostMapping("/hr-insight-callback")
    public ApiResponse<Void> onInsightReady(@RequestBody InternalCallbackRequest request) {
        log.info("收到 HR 洞察回调: taskId={}, insightId={}, success={}",
                request.getTaskId(), request.getBizId(), request.isSuccess());

        if (request.isSuccess()) {
            hrInsightService.applyInsightResult(request.getBizId(), request.getOutput());
        }
        return ApiResponse.success();
    }

    /** 追问判定完成。 */
    @PostMapping("/hr-verify-callback")
    public ApiResponse<Void> onVerifyReady(@RequestBody InternalCallbackRequest request) {
        log.info("收到追问判定回调: taskId={}, recordId={}, success={}",
                request.getTaskId(), request.getBizId(), request.isSuccess());

        if (request.isSuccess()) {
            hrInsightService.applyVerifyResult(request.getBizId(), request.getOutput());
        }
        return ApiResponse.success();
    }
}
