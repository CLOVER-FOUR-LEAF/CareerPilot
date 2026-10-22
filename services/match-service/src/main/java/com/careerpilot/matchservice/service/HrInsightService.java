package com.careerpilot.matchservice.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.careerpilot.matchservice.client.MatchSourceClient;
import com.careerpilot.matchservice.dto.HrInsightRequest;
import com.careerpilot.matchservice.dto.HrVerifyAnswerRequest;
import com.careerpilot.matchservice.entity.HrInsight;
import com.careerpilot.matchservice.entity.VerificationRecord;
import com.careerpilot.matchservice.mapper.HrInsightMapper;
import com.careerpilot.matchservice.mapper.VerificationRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HR 侧业务服务。
 *
 * <p>流程与 {@code MatchService} 同构：
 * 取素材（顺带校验归属）→ 建记录 → 登记 AI 任务 → 立即返回。
 * 分析本身在 interview-agent 的 recruiter 模式里跑，异步完成后回调写回。
 *
 * <p><b>合规提示</b>：本服务只产出「证据覆盖情况」，不产出任何录用建议。
 * 判定枚举只有 demonstrated / adjacent / not_evidenced 三个值（R1）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrInsightService {

    private final HrInsightMapper insightMapper;
    private final VerificationRecordMapper recordMapper;
    private final MatchSourceClient sourceClient;

    // ── 发起洞察报告 ────────────────────────────────────────────

    /**
     * 发起一份 HR 洞察报告。
     *
     * <p>同步做完「取素材 + 归属校验 + 建记录」，然后登记 AI 任务异步跑。
     * 返回成功即意味着任务已经在跑。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createInsight(String userId, HrInsightRequest request) {
        // 归属校验：素材服务会校验 userId，越权直接抛 1404
        MatchSourceClient.ResumeSource resume = sourceClient.fetchResume(request.getResumeId(), userId);
        MatchSourceClient.JdSource jd = sourceClient.fetchJd(request.getJdId(), userId);

        String insightBizId = "ins_" + IdUtil.fastSimpleUUID().substring(0, 16);
        LocalDateTime now = LocalDateTime.now();

        HrInsight insight = new HrInsight();
        insight.setInsightId(insightBizId);
        insight.setUserId(userId);
        insight.setResumeId(resume.getResumeBizId());
        insight.setJdId(jd.getJdBizId());
        insight.setMode("RECRUITER");
        insight.setCreatedAt(now);
        insight.setUpdatedAt(now);
        insightMapper.insert(insight);

        Map<String, Object> input = new HashMap<>();
        input.put("jd_id", jd.getJdBizId());
        input.put("resume_id", resume.getResumeBizId());
        input.put("jd_text", jd.getText());
        input.put("resume_text", resume.getText());
        input.put("jd_skills", jd.getHardSkills() == null ? List.of() : jd.getHardSkills());
        input.put("max_questions", request.getMaxQuestions() == null ? 12 : request.getMaxQuestions());

        String taskId = sourceClient.submitTask(userId, "hr_insight", insightBizId, input);

        if (taskId == null) {
            log.warn("HR 洞察任务登记失败，记录保持 PENDING: insightId={}", insightBizId);
            return buildCreated(insightBizId, null, "PENDING");
        }

        HrInsight patch = new HrInsight();
        patch.setId(insight.getId());
        patch.setTaskId(taskId);
        patch.setUpdatedAt(LocalDateTime.now());
        insightMapper.updateById(patch);

        return buildCreated(insightBizId, taskId, "PROCESSING");
    }

    private Map<String, Object> buildCreated(String insightId, String taskId, String status) {
        Map<String, Object> body = new HashMap<>();
        body.put("insightId", insightId);
        body.put("taskId", taskId);
        body.put("status", status);
        return body;
    }

    // ── 查询报告 ────────────────────────────────────────────────

    /** 报告详情：含逐 gap 的验证问题。 */
    public Map<String, Object> detail(String userId, String insightId) {
        HrInsight insight = requireOwned(userId, insightId);

        Map<String, Object> body = new HashMap<>();
        body.put("insightId", insight.getInsightId());
        body.put("taskId", insight.getTaskId());
        body.put("jdId", insight.getJdId());
        body.put("resumeId", insight.getResumeId());
        body.put("status", insight.getReportJson() == null ? "PROCESSING" : "SUCCESS");
        body.put("report", parseJsonObject(insight.getReportJson()));
        body.put("capabilityCoverage", parseJsonArray(insight.getCapabilityJson()));
        body.put("gapPlan", parseJsonArray(insight.getGapPlanJson()));
        body.put("createdAt", insight.getCreatedAt());
        return body;
    }

    /** 我的报告列表（分页）。 */
    public Page<Map<String, Object>> list(String userId, int page, int size) {
        LambdaQueryWrapper<HrInsight> wrapper = new LambdaQueryWrapper<HrInsight>()
                .eq(HrInsight::getUserId, userId)
                .orderByDesc(HrInsight::getCreatedAt);

        Page<HrInsight> result = insightMapper.selectPage(new Page<>(page, size), wrapper);

        Page<Map<String, Object>> vo = new Page<>(page, size, result.getTotal());
        vo.setRecords(result.getRecords().stream().map(r -> {
            Map<String, Object> item = new HashMap<>();
            item.put("insightId", r.getInsightId());
            item.put("jdId", r.getJdId());
            item.put("resumeId", r.getResumeId());
            item.put("status", r.getReportJson() == null ? "PROCESSING" : "SUCCESS");
            item.put("createdAt", r.getCreatedAt());
            return item;
        }).toList());
        return vo;
    }

    // ── 提交回答（追问判定）─────────────────────────────────────

    /**
     * 提交面试官录入的回答，登记一次判定任务。
     *
     * <p>判定信号由服务端从已保存的报告里取，不接受前端传入 ——
     * 否则改一下信号就能让判定对自己有利。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitAnswer(String userId, HrVerifyAnswerRequest request) {
        HrInsight insight = requireOwned(userId, request.getInsightId());

        JSONObject question = findQuestion(insight, request.getVqId());
        if (question == null) {
            throw new HrInsightNotFoundException("报告里找不到验证问题：" + request.getVqId());
        }

        String recordBizId = "ver_" + IdUtil.fastSimpleUUID().substring(0, 16);
        VerificationRecord record = new VerificationRecord();
        record.setRecordId(recordBizId);
        record.setInsightId(insight.getInsightId());
        record.setVqId(request.getVqId());
        record.setRoundNo(request.getRoundNo() == null ? 1 : request.getRoundNo());
        record.setQuestion(question.getStr("question"));
        record.setAnswer(request.getAnswer());
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);

        Map<String, Object> input = new HashMap<>();
        input.put("question", question);
        input.put("answer", request.getAnswer());
        input.put("round_no", record.getRoundNo());

        String taskId = sourceClient.submitTask(userId, "hr_verify", recordBizId, input);
        if (taskId == null) {
            log.warn("判定任务登记失败，记录保持 PENDING: recordId={}", recordBizId);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("recordId", recordBizId);
        body.put("taskId", taskId);
        body.put("status", taskId == null ? "PENDING" : "PROCESSING");
        body.put("insightId", insight.getInsightId());
        body.put("vqId", request.getVqId());
        return body;
    }

    /** 某份报告下的全部追问记录。 */
    public List<Map<String, Object>> records(String userId, String insightId) {
        requireOwned(userId, insightId);

        return recordMapper.selectList(new LambdaQueryWrapper<VerificationRecord>()
                        .eq(VerificationRecord::getInsightId, insightId)
                        .orderByAsc(VerificationRecord::getId))
                .stream()
                .map(r -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("recordId", r.getRecordId());
                    item.put("vqId", r.getVqId());
                    item.put("roundNo", r.getRoundNo());
                    item.put("question", r.getQuestion());
                    item.put("answer", r.getAnswer());
                    item.put("coverage", r.getCoverage());
                    item.put("confidence", r.getConfidence());
                    item.put("reason", r.getReason());
                    item.put("evidenceRef", r.getEvidenceRef());
                    item.put("createdAt", r.getCreatedAt());
                    return item;
                })
                .toList();
    }

    // ── 回调写回 ────────────────────────────────────────────────

    /** HR 洞察结果落库。 */
    @Transactional(rollbackFor = Exception.class)
    public void applyInsightResult(String insightId, Map<String, Object> output) {
        HrInsight insight = findByBizId(insightId);
        if (insight == null) {
            log.warn("回调对应的 HR 洞察记录不存在: insightId={}", insightId);
            return;
        }
        if (output == null || output.isEmpty()) {
            log.warn("HR 洞察结果为空: insightId={}", insightId);
            return;
        }

        HrInsight patch = new HrInsight();
        patch.setId(insight.getId());
        patch.setReportJson(JSONUtil.toJsonStr(output));
        patch.setCapabilityJson(JSONUtil.toJsonStr(output.get("capability_coverage")));
        patch.setGapPlanJson(JSONUtil.toJsonStr(output.get("verification_plan")));
        patch.setUpdatedAt(LocalDateTime.now());
        insightMapper.updateById(patch);

        log.info("HR 洞察完成: insightId={}", insightId);
    }

    /** 判定结果落库。 */
    @Transactional(rollbackFor = Exception.class)
    public void applyVerifyResult(String recordId, Map<String, Object> output) {
        VerificationRecord record = recordMapper.selectOne(
                new LambdaQueryWrapper<VerificationRecord>().eq(VerificationRecord::getRecordId, recordId));
        if (record == null) {
            log.warn("回调对应的追问记录不存在: recordId={}", recordId);
            return;
        }
        if (output == null || output.isEmpty()) {
            return;
        }

        VerificationRecord patch = new VerificationRecord();
        patch.setId(record.getId());
        patch.setCoverage(output.get("coverage") == null ? null : String.valueOf(output.get("coverage")));
        patch.setReason(output.get("reason") == null ? null : String.valueOf(output.get("reason")));
        patch.setEvidenceRef(output.get("evidence_ref") == null ? null : String.valueOf(output.get("evidence_ref")));
        Object confidence = output.get("confidence");
        if (confidence instanceof Number n) {
            patch.setConfidence(BigDecimal.valueOf(n.doubleValue()));
        }
        recordMapper.updateById(patch);

        log.info("追问判定完成: recordId={}, coverage={}", recordId, patch.getCoverage());
    }

    // ── 内部工具 ────────────────────────────────────────────────

    private HrInsight requireOwned(String userId, String insightId) {
        HrInsight insight = insightMapper.selectOne(
                new LambdaQueryWrapper<HrInsight>().eq(HrInsight::getInsightId, insightId));
        if (insight == null || !insight.getUserId().equals(userId)) {
            throw new HrInsightNotFoundException(insightId);
        }
        return insight;
    }

    private HrInsight findByBizId(String insightId) {
        return insightMapper.selectOne(
                new LambdaQueryWrapper<HrInsight>().eq(HrInsight::getInsightId, insightId));
    }

    /** 从报告里取出指定的问题（含判定信号）。 */
    private JSONObject findQuestion(HrInsight insight, String vqId) {
        JSONArray plan = parseJsonArray(insight.getGapPlanJson());
        for (int i = 0; i < plan.size(); i++) {
            JSONObject item = plan.getJSONObject(i);
            if (item != null && vqId.equals(item.getStr("id"))) {
                return item;
            }
        }
        return null;
    }

    private static JSONObject parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONArray parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return new JSONArray();
        }
        try {
            return JSONUtil.parseArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static class HrInsightNotFoundException extends RuntimeException {
        public HrInsightNotFoundException(String id) {
            super("HR 洞察报告不存在: " + id);
        }
    }
}
