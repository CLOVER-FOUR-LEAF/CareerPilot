package com.careerpilot.resumeservice.controller;

import com.careerpilot.resumeservice.dto.ApiResponse;
import com.careerpilot.resumeservice.entity.Resume;
import com.careerpilot.resumeservice.entity.ResumeVersion;
import com.careerpilot.resumeservice.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 供其他微服务读取简历正文的内部接口，**不经网关**。
 *
 * <p>match-service 需要简历全文做语义匹配，但简历数据归本服务所有，
 * 所以由本服务来校验归属并吐出正文，而不是让 match-service 直连本服务的表。
 */
@RestController
@RequestMapping("/internal/resumes")
@RequiredArgsConstructor
public class InternalResumeController {

    private final ResumeService resumeService;

    /**
     * 按数据库主键取简历正文。{@code userId} 必传且会被校验归属 ——
     * 没有归属校验的话，任何服务都能通过猜 ID 把别人的简历读走。
     */
    @GetMapping("/{id}/text")
    public ApiResponse<Map<String, Object>> textById(@PathVariable("id") Long id,
                                                      @RequestParam("userId") String userId) {
        Resume resume = resumeService.requireOwned(userId, id);
        ResumeVersion version = resumeService.currentVersion(resume);
        if (version == null || version.getParsedText() == null || version.getParsedText().isBlank()) {
            return ApiResponse.error(1404, "简历正文尚未抽取");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("resumeBizId", resume.getResumeId());
        body.put("title", resume.getTitle());
        body.put("atsScore", resume.getAtsScore());
        body.put("text", version.getParsedText());
        return ApiResponse.success(body);
    }
}
