package com.careerpilot.matchservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.careerpilot.matchservice.dto.ApiResponse;
import com.careerpilot.matchservice.dto.EvaluateRequest;
import com.careerpilot.matchservice.dto.MatchCreatedVO;
import com.careerpilot.matchservice.service.MatchService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    /**
     * 发起匹配评估。
     *
     * <p>同步做完"取素材 + 归属校验 + 建记录"，然后异步跑打分。
     * 所以这里的失败是真的失败（简历/JD 不存在、无权访问、依赖服务挂了），
     * 返回成功就意味着任务已经在跑了。
     */
    @PostMapping("/evaluate")
    public ApiResponse<MatchCreatedVO> evaluate(@RequestHeader("X-User-Id") String userId,
                                                @Valid @RequestBody EvaluateRequest request) {
        return ApiResponse.success(matchService.evaluate(userId, request.getResumeId(), request.getJdId()));
    }

    /** 完整结果：三段式分数 + Gap List。未完成时分数为 null。 */
    @GetMapping("/{recordId}")
    public ApiResponse<Map<String, Object>> detail(@RequestHeader("X-User-Id") String userId,
                                                   @PathVariable("recordId") String recordId) {
        return ApiResponse.success(matchService.detail(userId, recordId));
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(@RequestHeader("X-User-Id") String userId,
                                                 @RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<Map<String, Object>> result = matchService.list(userId, page, size);

        Map<String, Object> body = new HashMap<>();
        body.put("total", result.getTotal());
        body.put("page", page);
        body.put("size", size);
        body.put("items", result.getRecords());
        return ApiResponse.success(body);
    }
}
