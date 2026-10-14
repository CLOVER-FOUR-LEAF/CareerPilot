package com.careerpilot.matchservice.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.careerpilot.matchservice.client.MatchSourceClient;
import com.careerpilot.matchservice.dto.MatchCreatedVO;
import com.careerpilot.matchservice.entity.MatchGap;
import com.careerpilot.matchservice.entity.MatchRecord;
import com.careerpilot.matchservice.mapper.MatchGapMapper;
import com.careerpilot.matchservice.mapper.MatchRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 匹配业务服务。
 *
 * <p>流程：取素材（顺带校验归属）→ 建记录 → 登记 AI 任务 → 立即返回。
 * 打分本身在 match-agent 里跑，异步完成后回调写回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRecordMapper recordMapper;
    private final MatchGapMapper gapMapper;
    private final MatchSourceClient sourceClient;

    // ── 发起评估 ────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public MatchCreatedVO evaluate(String userId, Long resumeId, Long jdId) {
        // 归属校验在这里发生：素材服务会校验 userId，越权直接抛 1404
        MatchSourceClient.ResumeSource resume = sourceClient.fetchResume(resumeId, userId);
        MatchSourceClient.JdSource jd = sourceClient.fetchJd(jdId, userId);

        String recordBizId = "mch_" + IdUtil.fastSimpleUUID().substring(0, 16);
        LocalDateTime now = LocalDateTime.now();

        MatchRecord record = new MatchRecord();
        record.setRecordId(recordBizId);
        record.setUserId(userId);
        record.setResumeId(resume.getResumeBizId());
        record.setJdId(jd.getJdBizId());
        record.setStatus("PENDING");
        record.setCreatedAt(now);
        recordMapper.insert(record);

        Map<String, Object> input = buildAgentInput(resume, jd);
        String taskId = sourceClient.submitMatchScore(userId, recordBizId, input);

        if (taskId == null) {
            log.warn("匹配任务登记失败，记录保持 PENDING: recordId={}", recordBizId);
            return MatchCreatedVO.of(recordBizId, "PENDING", null, now);
        }

        MatchRecord patch = new MatchRecord();
        patch.setId(record.getId());
        patch.setStatus("PROCESSING");
        patch.setTaskId(taskId);
        recordMapper.updateById(patch);

        return MatchCreatedVO.of(recordBizId, "PROCESSING", taskId, now);
    }

    /**
     * 组装给 match-agent 的输入。
     *
     * <p>把 JD 已解析出的硬技能显式传过去，是为了让关键词覆盖率
     * 以"JD 解析结果"为准，而不是让 Agent 重新对全文做一遍抽取 ——
     * 两边抽出来的技能集合不一致，评分就会前后矛盾。
     */
    private Map<String, Object> buildAgentInput(MatchSourceClient.ResumeSource resume,
                                                MatchSourceClient.JdSource jd) {
        Map<String, Object> input = new HashMap<>();
        input.put("resume_id", resume.getResumeBizId());
        input.put("jd_id", jd.getJdBizId());
        input.put("job_title", jd.getTitle());
        input.put("company_name", jd.getCompany());
        input.put("jd_text", jd.getText());
        input.put("resume_text", resume.getText());

        if (jd.getHardSkills() != null && !jd.getHardSkills().isEmpty()) {
            input.put("jd_skills", jd.getHardSkills());
        }
        if (jd.getSeniority() != null) {
            input.put("seniority", jd.getSeniority());
        }
        if (jd.getIndustry() != null) {
            input.put("industry", jd.getIndustry());
        }
        if (jd.getEducationRequired() != null) {
            input.put("education_required", jd.getEducationRequired());
        }
        if (jd.getExperienceMinYears() != null) {
            input.put("experience_min_years", jd.getExperienceMinYears());
        }
        return input;
    }

    // ── 查询 ────────────────────────────────────────────────────

    /** 完整结果：含三段式分数与 Gap List。 */
    public Map<String, Object> detail(String userId, String recordId) {
        MatchRecord record = requireOwned(userId, recordId);

        Map<String, Object> body = new HashMap<>();
        body.put("recordId", record.getRecordId());
        body.put("status", record.getStatus());
        body.put("taskId", record.getTaskId());
        body.put("resumeId", record.getResumeId());
        body.put("jdId", record.getJdId());

        if (record.getResultJson() == null || record.getResultJson().isBlank()) {
            // 未完成时分数为 null，前端据此显示"评分中"而不是渲染 0 分
            body.put("semanticScore", null);
            body.put("keywordScore", null);
            body.put("businessScore", null);
            body.put("finalScore", null);
            body.put("matchedSkills", List.of());
            body.put("gapList", List.of());
        } else {
            JSONObject result = JSONUtil.parseObj(record.getResultJson());
            body.put("semanticScore", record.getSemanticScore());
            body.put("keywordScore", record.getKeywordScore());
            body.put("businessScore", record.getBusinessScore());
            body.put("finalScore", record.getFinalScore());
            body.put("matchedSkills", toList(result.getJSONArray("matched_skills")));
            body.put("gapList", buildGapList(record.getRecordId()));
        }

        body.put("errorMessage", record.getErrorMsg());
        body.put("createdAt", record.getCreatedAt());
        body.put("completedAt", record.getCompletedAt());
        return body;
    }

    /** 历史列表：只给分数，不展开 gapList。 */
    public Page<Map<String, Object>> list(String userId, int page, int size) {
        LambdaQueryWrapper<MatchRecord> wrapper = new LambdaQueryWrapper<MatchRecord>()
                .eq(MatchRecord::getUserId, userId)
                .orderByDesc(MatchRecord::getCreatedAt);

        Page<MatchRecord> result = recordMapper.selectPage(new Page<>(page, size), wrapper);

        Page<Map<String, Object>> vo = new Page<>(page, size, result.getTotal());
        vo.setRecords(result.getRecords().stream().map(r -> {
            Map<String, Object> item = new HashMap<>();
            item.put("recordId", r.getRecordId());
            item.put("resumeId", r.getResumeId());
            item.put("jdId", r.getJdId());
            item.put("status", r.getStatus());
            item.put("finalScore", r.getFinalScore());
            item.put("createdAt", r.getCreatedAt());
            return item;
        }).toList());
        return vo;
    }

    private List<Map<String, Object>> buildGapList(String recordId) {
        return gapMapper.selectList(new LambdaQueryWrapper<MatchGap>()
                        .eq(MatchGap::getRecordId, recordId)
                        .orderByDesc(MatchGap::getSeverity))
                .stream()
                .map(g -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("gapType", g.getGapType());
                    item.put("gapContent", g.getGapContent());
                    item.put("severity", g.getSeverity());
                    item.put("suggestion", g.getSuggestion());
                    return item;
                })
                .toList();
    }

    private static List<String> toList(JSONArray array) {
        if (array == null) {
            return List.of();
        }
        return array.toList(String.class);
    }

    /** 按归属校验取记录；越权与不存在统一报 1404。 */
    public MatchRecord requireOwned(String userId, String recordId) {
        MatchRecord record = recordMapper.selectOne(
                new LambdaQueryWrapper<MatchRecord>().eq(MatchRecord::getRecordId, recordId));
        if (record == null || !record.getUserId().equals(userId)) {
            throw new MatchRecordNotFoundException(recordId);
        }
        return record;
    }

    public MatchRecord findByBizId(String recordId) {
        return recordMapper.selectOne(
                new LambdaQueryWrapper<MatchRecord>().eq(MatchRecord::getRecordId, recordId));
    }

    // ── 回调写回 ────────────────────────────────────────────────

    /**
     * 把 match-agent 的 MatchResult 落库。
     *
     * <p>分数同时写进两处：{@code t_match_record} 的独立列（列表页直接读）
     * 和 {@code result_json}（一次性取回完整结构）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyResult(String recordId, Map<String, Object> output) {
        MatchRecord record = findByBizId(recordId);
        if (record == null) {
            log.warn("回调对应的匹配记录不存在: recordId={}", recordId);
            return;
        }
        if (output == null || output.isEmpty()) {
            markFailed(record, "匹配结果为空");
            return;
        }

        JSONObject result = JSONUtil.parseObj(output);

        MatchRecord patch = new MatchRecord();
        patch.setId(record.getId());
        patch.setStatus("SUCCESS");
        patch.setSemanticScore(toInt(result.get("semantic_score")));
        patch.setKeywordScore(toInt(result.get("keyword_score")));
        patch.setBusinessScore(toInt(result.get("business_score")));
        patch.setFinalScore(toInt(result.get("final_score")));
        patch.setResultJson(JSONUtil.toJsonStr(result));
        patch.setCompletedAt(LocalDateTime.now());
        recordMapper.updateById(patch);

        replaceGaps(recordId, result.getJSONArray("gap_list"));

        log.info("匹配完成: recordId={}, final={}", recordId, patch.getFinalScore());
    }
    private void replaceGaps(String recordId, JSONArray gapList) {
        gapMapper.delete(new LambdaQueryWrapper<MatchGap>().eq(MatchGap::getRecordId, recordId));
        if (gapList == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Object o : gapList) {
            if (!(o instanceof JSONObject gap)) {
                continue;
            }
            MatchGap entity = new MatchGap();
            entity.setRecordId(recordId);
            entity.setGapType(gap.getStr("gap_type", "missing_skill"));
            entity.setGapContent(truncate(gap.getStr("gap_content"), 256));
            entity.setSeverity(gap.getInt("severity", 3));
            entity.setSuggestion(gap.getStr("suggestion"));
            entity.setCreatedAt(now);
            gapMapper.insert(entity);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailed(MatchRecord record, String errorMessage) {
        if (record == null) {
            return;
        }
        MatchRecord patch = new MatchRecord();
        patch.setId(record.getId());
        patch.setStatus("FAILED");
        patch.setErrorMsg(truncate(errorMessage, 500));
        patch.setCompletedAt(LocalDateTime.now());
        recordMapper.updateById(patch);
    }

    private static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static class MatchRecordNotFoundException extends RuntimeException {
        public MatchRecordNotFoundException(String recordId) {
            super("匹配记录不存在: " + recordId);
        }
    }
}
