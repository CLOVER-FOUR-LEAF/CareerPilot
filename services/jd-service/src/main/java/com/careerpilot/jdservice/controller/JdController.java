package com.careerpilot.jdservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.careerpilot.jdservice.dto.ApiResponse;
import com.careerpilot.jdservice.dto.JdCreatedVO;
import com.careerpilot.jdservice.dto.JdDetailVO;
import com.careerpilot.jdservice.dto.JdListItemVO;
import com.careerpilot.jdservice.dto.JdUploadRequest;
import com.careerpilot.jdservice.service.JdService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/jd")
@RequiredArgsConstructor
public class JdController {

    private final JdService jdService;

    /**
     * 新建 JD。落库后立即登记解析任务并返回，**不等** Agent 跑完。
     * 前端拿 taskId 轮询进度，或用 SSE。
     */
    @PostMapping("/upload")
    public ApiResponse<JdCreatedVO> create(@RequestHeader("X-User-Id") String userId,
                                            @Valid @RequestBody JdUploadRequest request) {
        return ApiResponse.success(jdService.create(userId, request));
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(@RequestHeader("X-User-Id") String userId,
                                                 @RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size,
                                                 @RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "company", required = false) String company) {

        Page<JdListItemVO> result = jdService.list(userId, page, size, keyword, company);

        Map<String, Object> body = new HashMap<>();
        body.put("total", result.getTotal());
        body.put("page", page);
        body.put("size", size);
        body.put("items", result.getRecords());
        return ApiResponse.success(body);
    }

    @GetMapping("/{id}")
    public ApiResponse<JdDetailVO> detail(@RequestHeader("X-User-Id") String userId,
                                          @PathVariable("id") Long id) {
        return ApiResponse.success(jdService.detail(userId, id));
    }

    /**
     * 解析结果。直接返回 Agent 的 JdChunk 原样结构（前端就按这个渲染），
     * 未解析完成时返回 1404，前端据此显示"解析中"并继续轮询。
     */
    @GetMapping("/{id}/parse/result")
    public ApiResponse<Map<String, Object>> parseResult(@RequestHeader("X-User-Id") String userId,
                                                        @PathVariable("id") Long id) {
        Map<String, Object> result = jdService.parseResult(userId, id);
        if (result == null) {
            return ApiResponse.error(1404, "JD 尚未解析完成");
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/reparse")
    public ApiResponse<Map<String, String>> reparse(@RequestHeader("X-User-Id") String userId,
                                                    @PathVariable("id") Long id) {
        return ApiResponse.success(jdService.reparse(userId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@RequestHeader("X-User-Id") String userId,
                                    @PathVariable("id") Long id) {
        jdService.delete(userId, id);
        return ApiResponse.success();
    }
}
