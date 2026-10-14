package com.careerpilot.matchservice.controller;

import com.careerpilot.matchservice.dto.ApiResponse;
import com.careerpilot.matchservice.dto.InternalCallbackRequest;
import com.careerpilot.matchservice.entity.MatchRecord;
import com.careerpilot.matchservice.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部回调入口，**不经网关**。
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class MatchCallbackController {

    private final MatchService matchService;

    @PostMapping("/match-callback")
    public ApiResponse<Void> onMatched(@RequestBody InternalCallbackRequest request) {
        log.info("收到匹配回调: taskId={}, recordId={}, success={}",
                request.getTaskId(), request.getBizId(), request.isSuccess());

        if (!request.isSuccess()) {
            matchService.markFailed(
                    matchService.findByBizId(request.getBizId()),
                    request.getErrorMessage());
            return ApiResponse.success();
        }

        matchService.applyResult(request.getBizId(), request.getOutput());
        return ApiResponse.success();
    }
}
