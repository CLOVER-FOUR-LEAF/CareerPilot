package com.careerpilot.resumeservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.careerpilot.resumeservice.dto.ApiResponse;
import com.careerpilot.resumeservice.dto.ResumeCreatedVO;
import com.careerpilot.resumeservice.dto.ResumeDetailVO;
import com.careerpilot.resumeservice.dto.ResumeListItemVO;
import com.careerpilot.resumeservice.dto.ResumeSectionVO;
import com.careerpilot.resumeservice.dto.ResumeVersionVO;
import com.careerpilot.resumeservice.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /**
     * 上传简历。落盘 → 抽正文 → 建版本 → 登记 AI 任务，然后立即返回，
     * 不等 ATS 评分跑完。前端拿 taskId 轮询进度。
     */
    @PostMapping("/upload")
    public ApiResponse<ResumeCreatedVO> upload(@RequestHeader("X-User-Id") String userId,
                                               @RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "title", required = false) String title) {
        return ApiResponse.success(resumeService.upload(userId, file, title));
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(@RequestHeader("X-User-Id") String userId,
                                                 @RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size,
                                                 @RequestParam(value = "keyword", required = false) String keyword) {
        Page<ResumeListItemVO> result = resumeService.list(userId, page, size, keyword);

        Map<String, Object> body = new HashMap<>();
        body.put("total", result.getTotal());
        body.put("page", page);
        body.put("size", size);
        body.put("items", result.getRecords());
        return ApiResponse.success(body);
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeDetailVO> detail(@RequestHeader("X-User-Id") String userId,
                                              @PathVariable("id") Long id) {
        return ApiResponse.success(resumeService.detail(userId, id));
    }

    /**
     * ATS 评分报告。直接返回 Agent 的 AnalysisResult 原样结构，
     * 未分析完成时返回 1404，前端据此继续轮询。
     */
    @GetMapping("/{id}/ats-score")
    public ApiResponse<Map<String, Object>> atsScore(@RequestHeader("X-User-Id") String userId,
                                                     @PathVariable("id") Long id) {
        Map<String, Object> result = resumeService.atsScore(userId, id);
        if (result == null) {
            return ApiResponse.error(1404, "简历尚未完成分析");
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<ResumeVersionVO>> versions(@RequestHeader("X-User-Id") String userId,
                                                       @PathVariable("id") Long id) {
        return ApiResponse.success(resumeService.versions(userId, id));
    }

    @GetMapping("/{id}/sections")
    public ApiResponse<List<ResumeSectionVO>> sections(@RequestHeader("X-User-Id") String userId,
                                                       @PathVariable("id") Long id) {
        return ApiResponse.success(resumeService.sections(userId, id));
    }

    @PostMapping("/{id}/reparse")
    public ApiResponse<Map<String, String>> reparse(@RequestHeader("X-User-Id") String userId,
                                                    @PathVariable("id") Long id) {
        return ApiResponse.success(resumeService.reparse(userId, id));
    }

    @PostMapping("/{id}/primary")
    public ApiResponse<Void> setPrimary(@RequestHeader("X-User-Id") String userId,
                                        @PathVariable("id") Long id) {
        resumeService.setPrimary(userId, id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@RequestHeader("X-User-Id") String userId,
                                    @PathVariable("id") Long id) {
        resumeService.delete(userId, id);
        return ApiResponse.success();
    }
}
