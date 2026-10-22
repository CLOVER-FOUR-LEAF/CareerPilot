package com.careerpilot.matchservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.careerpilot.matchservice.dto.ApiResponse;
import com.careerpilot.matchservice.dto.HrInsightRequest;
import com.careerpilot.matchservice.dto.HrVerifyAnswerRequest;
import com.careerpilot.matchservice.service.HrInsightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HR 侧对外接口。
 *
 * <p><b>合规提示</b>：本接口只提供「证据覆盖情况」与「验证问题」，
 * 不提供任何录用建议或淘汰结论。判定枚举只有
 * demonstrated / adjacent / not_evidenced 三个值。
 */
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
public class HrController {

    private final HrInsightService hrInsightService;

    /**
     * 发起 HR 洞察报告。
     *
     * <p>同步做完「取素材 + 归属校验 + 建记录」，异步跑报告生成。
     * 返回成功即意味着任务已经在跑。
     */
    @PostMapping("/insight")
    public ApiResponse<Map<String, Object>> createInsight(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody HrInsightRequest request) {
        return ApiResponse.success(
                hrInsightService.createInsight(userId, request));
    }

    /** 报告详情：含逐 gap 的验证问题。 */
    @GetMapping("/insight/{insightId}")
    public ApiResponse<Map<String, Object>> detail(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("insightId") String insightId) {
        return ApiResponse.success(hrInsightService.detail(userId, insightId));
    }

    /** 我的报告列表（分页）。 */
    @GetMapping("/insight/list")
    public ApiResponse<Map<String, Object>> list(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<Map<String, Object>> result = hrInsightService.list(userId, page, size);

        Map<String, Object> body = new HashMap<>();
        body.put("total", result.getTotal());
        body.put("page", page);
        body.put("size", size);
        body.put("items", result.getRecords());
        return ApiResponse.success(body);
    }

    /**
     * 提交面试官录入的回答，触发一次覆盖判定。
     *
     * <p>判定信号由服务端从报告里取，不接受前端传入。
     */
    @PostMapping("/verify/answer")
    public ApiResponse<Map<String, Object>> verifyAnswer(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody HrVerifyAnswerRequest request) {
        return ApiResponse.success(
                hrInsightService.submitAnswer(userId, request));
    }

    /** 某份报告下的全部追问记录（人工决策留痕）。 */
    @GetMapping("/verify/records/{insightId}")
    public ApiResponse<List<Map<String, Object>>> records(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("insightId") String insightId) {
        return ApiResponse.success(hrInsightService.records(userId, insightId));
    }
}
