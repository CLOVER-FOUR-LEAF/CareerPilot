package com.careerpilot.jdservice.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.careerpilot.jdservice.client.AiTaskClient;
import com.careerpilot.jdservice.dto.JdCreatedVO;
import com.careerpilot.jdservice.dto.JdDetailVO;
import com.careerpilot.jdservice.dto.JdListItemVO;
import com.careerpilot.jdservice.dto.JdUploadRequest;
import com.careerpilot.jdservice.entity.Jd;
import com.careerpilot.jdservice.entity.JdParseResult;
import com.careerpilot.jdservice.mapper.JdMapper;
import com.careerpilot.jdservice.mapper.JdParseResultMapper;
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
 * JD 业务服务。
 *
 * <p>职责：JD 增删查 + 触发解析 + 接收解析结果写回。
 * 解析本身跑在 Python Agent 侧，本服务只负责编排和持久化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JdService {

    private final JdMapper jdMapper;
    private final JdParseResultMapper jdParseResultMapper;
    private final AiTaskClient aiTaskClient;

    // ── 新建 ────────────────────────────────────────────────────

    /**
     * 新建 JD 并立即登记解析任务。
     *
     * <p>接口不等 AI 解析结果，先返回 PENDING 让前端展示，
     * 解析完成后由 ai-task-scheduler 回调 {@link JdCallbackService} 写回。
     */
    @Transactional(rollbackFor = Exception.class)
    public JdCreatedVO create(String userId, JdUploadRequest request) {
        String bizId = "jd_" + IdUtil.fastSimpleUUID().substring(0, 16);

        Jd jd = new Jd();
        jd.setJdId(bizId);
        jd.setUserId(userId);
        jd.setSourceType(request.getSourceType() == null ? "MANUAL" : request.getSourceType());
        jd.setSourceUrl(request.getSourceUrl());
        jd.setCompanyName(blankToNull(request.getCompanyName()));
        jd.setPositionName(blankToNull(request.getJobTitle()));
        jd.setSalaryRange(blankToNull(request.getSalaryRange()));
        jd.setRawText(request.getRawText());
        jd.setStatus("PENDING");
        jd.setIsDeleted(0);
        jd.setCreatedAt(LocalDateTime.now());
        jd.setUpdatedAt(LocalDateTime.now());
        jdMapper.insert(jd);

        String taskId = triggerParse(jd);
        return JdCreatedVO.of(jd, taskId);
    }

    /**
     * 触发解析。登记成功就把 status 推到 PROCESSING；
     * 登记失败（调度服务不可用）保持 PENDING，用户可以手动重新解析。
     */
    public String triggerParse(Jd jd) {
        String taskId = aiTaskClient.submitJdParse(jd.getUserId(), jd.getJdId(), jd.getRawText());
        if (taskId != null) {
            Jd patch = new Jd();
            patch.setId(jd.getId());
            patch.setStatus("PROCESSING");
            patch.setUpdatedAt(LocalDateTime.now());
            jdMapper.updateById(patch);
        }
        return taskId;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> reparse(String userId, Long id) {
        Jd jd = requireOwned(userId, id);
        Jd patch = new Jd();
        patch.setId(jd.getId());
        patch.setStatus("PENDING");
        patch.setUpdatedAt(LocalDateTime.now());
        jdMapper.updateById(patch);

        String taskId = triggerParse(jd);

        Map<String, String> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("parseStatus", taskId != null ? "PROCESSING" : "PENDING");
        return result;
    }

    // ── 查询 ────────────────────────────────────────────────────

    public Page<JdListItemVO> list(String userId, int page, int size, String keyword, String company) {
        LambdaQueryWrapper<Jd> wrapper = new LambdaQueryWrapper<Jd>()
                .eq(Jd::getUserId, userId)
                .eq(Jd::getIsDeleted, 0)
                .orderByDesc(Jd::getUpdatedAt);

        if (hasText(keyword)) {
            wrapper.and(w -> w.like(Jd::getPositionName, keyword).or().like(Jd::getRawText, keyword));
        }
        if (hasText(company)) {
            wrapper.like(Jd::getCompanyName, company);
        }

        Page<Jd> result = jdMapper.selectPage(new Page<>(page, size), wrapper);

        Page<JdListItemVO> vo = new Page<>(page, size, result.getTotal());
        vo.setRecords(result.getRecords().stream().map(this::toListItem).toList());
        return vo;
    }

    private JdListItemVO toListItem(Jd jd) {
        JdListItemVO vo = new JdListItemVO();
        vo.setJdId(jd.getId());
        vo.setJdBizId(jd.getJdId());
        vo.setTitle(jd.getPositionName());
        vo.setCompany(jd.getCompanyName());
        vo.setParseStatus(jd.getStatus());
        vo.setSourceUrl(jd.getSourceUrl());
        vo.setRawTextPreview(preview(jd.getRawText(), 200));
        vo.setCreatedAt(jd.getCreatedAt());
        return vo;
    }

    public JdDetailVO detail(String userId, Long id) {
        Jd jd = requireOwned(userId, id);
        JdDetailVO vo = new JdDetailVO();
        vo.setJdId(jd.getId());
        vo.setJdBizId(jd.getJdId());
        vo.setTitle(jd.getPositionName());
        vo.setCompany(jd.getCompanyName());
        vo.setSalaryRange(jd.getSalaryRange());
        vo.setRawText(jd.getRawText());
        vo.setSourceUrl(jd.getSourceUrl());
        vo.setSourceType(jd.getSourceType());
        vo.setParseStatus(jd.getStatus());
        vo.setCreatedAt(jd.getCreatedAt());
        vo.setUpdatedAt(jd.getUpdatedAt());
        return vo;
    }

    /**
     * 取解析结果。
     *
     * <p>直接把 Agent 返回的 JdChunk 原样吐出去，再补上后端派生的
     * {@code seniority} / {@code industry} / {@code createdAt}。
     * 前端渲染的就是这份结构，不要再包一层。
     */
    public Map<String, Object> parseResult(String userId, Long id) {
        Jd jd = requireOwned(userId, id);

        JdParseResult result = findParseResult(jd.getJdId());
        if (result == null || result.getRawResultJson() == null) {
            return null;
        }

        JSONObject chunk = JSONUtil.parseObj(result.getRawResultJson());
        chunk.set("seniority", result.getSeniority());
        chunk.set("industry", result.getIndustry());
        chunk.set("createdAt", result.getCreatedAt() == null ? null : result.getCreatedAt().toString());
        return chunk.toBean(Map.class);
    }

    // ── 删除 ────────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public void delete(String userId, Long id) {
        Jd jd = requireOwned(userId, id);
        Jd patch = new Jd();
        patch.setId(jd.getId());
        patch.setIsDeleted(1);
        patch.setUpdatedAt(LocalDateTime.now());
        jdMapper.updateById(patch);
    }

    // ── 内部 ────────────────────────────────────────────────────

    Jd findByBizId(String jdBizId) {
        return jdMapper.selectOne(
                new LambdaQueryWrapper<Jd>()
                        .eq(Jd::getJdId, jdBizId)
                        .eq(Jd::getIsDeleted, 0));
    }

    /** 供内部接口读取已解析结果。 */
    public JdParseResult findParseResult(String jdBizId) {
        return jdParseResultMapper.selectOne(
                new LambdaQueryWrapper<JdParseResult>().eq(JdParseResult::getJdId, jdBizId));
    }

    /** 按归属校验取 JD，越权与不存在统一报 1404，不泄露资源是否存在。 */
    public Jd requireOwned(String userId, Long id) {
        Jd jd = jdMapper.selectById(id);
        if (jd == null || jd.getIsDeleted() == 1 || !jd.getUserId().equals(userId)) {
            throw new JdNotFoundException(id);
        }
        return jd;
    }

    static String preview(String text, int max) {
        if (text == null) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从 JdChunk 里抽出技能列表，序列化进 t_jd_skill 供匹配检索。 */
    static List<JSONObject> extractSkills(JSONObject chunk) {
        Object skills = chunk.get("skills");
        if (!(skills instanceof JSONArray array)) {
            return List.of();
        }
        return array.toList(JSONObject.class);
    }

    public static class JdNotFoundException extends RuntimeException {
        public JdNotFoundException(Long id) {
            super("JD 不存在: " + id);
        }
    }
}
