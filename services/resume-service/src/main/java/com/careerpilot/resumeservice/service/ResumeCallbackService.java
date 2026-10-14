package com.careerpilot.resumeservice.service;

import cn.hutool.json.JSONUtil;
import com.careerpilot.resumeservice.entity.Resume;
import com.careerpilot.resumeservice.entity.ResumeVersion;
import com.careerpilot.resumeservice.mapper.ResumeMapper;
import com.careerpilot.resumeservice.mapper.ResumeVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 接收 ai-task-scheduler 的简历分析回调，把 Agent 的 AnalysisResult 落库，
 * 并把总分回写到简历主表，让列表页不用展开 JSON 就能显示 ATS 分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeCallbackService {

    private final ResumeService resumeService;
    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper versionMapper;

    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(String resumeBizId, boolean success, Map<String, Object> output,
                               String errorMessage) {
        Resume resume = resumeService.findByBizId(resumeBizId);
        if (resume == null) {
            log.warn("回调对应的简历不存在: resumeId={}", resumeBizId);
            return;
        }

        if (!success || output == null || output.isEmpty()) {
            markStatus(resume, "FAILED", null);
            return;
        }

        int overallScore = extractOverallScore(output);

        // 1) 把完整评分报告写到当前版本
        ResumeVersion version = resumeService.currentVersion(resume);
        if (version != null) {
            ResumeVersion patch = new ResumeVersion();
            patch.setId(version.getId());
            patch.setAnalysisJson(JSONUtil.toJsonStr(output));
            patch.setAtsScore(overallScore);
            versionMapper.updateById(patch);
        }

        // 2) 总分回写主表，列表页直接显示
        markStatus(resume, "SUCCESS", overallScore);

        log.info("简历分析完成: resumeId={}, ATS={}", resumeBizId, overallScore);
    }

    private void markStatus(Resume resume, String status, Integer atsScore) {
        Resume patch = new Resume();
        patch.setId(resume.getId());
        patch.setParseStatus(status);
        if (atsScore != null) {
            patch.setAtsScore(atsScore);
        }
        patch.setUpdatedAt(LocalDateTime.now());
        resumeMapper.updateById(patch);
    }

    /**
     * 从 AnalysisResult 里取总分。
     *
     * <p>{@code overall_score} 是 0-100 的浮点，主表里存整数四舍五入。
     * Agent 返回缺字段时返回 null，绝不因此让整个回调失败。
     */
    private Integer extractOverallScore(Map<String, Object> output) {
        Object raw = output.get("overall_score");
        if (raw == null) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(String.valueOf(raw));
            return value.setScale(0, RoundingMode.HALF_UP).intValue();
        } catch (NumberFormatException e) {
            log.warn("overall_score 不是合法数字: {}", raw);
            return null;
        }
    }
}
