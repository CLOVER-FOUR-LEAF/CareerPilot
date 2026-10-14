package com.careerpilot.resumeservice.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.careerpilot.resumeservice.client.AiTaskClient;
import com.careerpilot.resumeservice.config.CareerPilotProperties;
import com.careerpilot.resumeservice.dto.ResumeCreatedVO;
import com.careerpilot.resumeservice.dto.ResumeDetailVO;
import com.careerpilot.resumeservice.dto.ResumeListItemVO;
import com.careerpilot.resumeservice.dto.ResumeSectionVO;
import com.careerpilot.resumeservice.dto.ResumeVersionVO;
import com.careerpilot.resumeservice.entity.FileRecord;
import com.careerpilot.resumeservice.entity.Resume;
import com.careerpilot.resumeservice.entity.ResumeSection;
import com.careerpilot.resumeservice.entity.ResumeVersion;
import com.careerpilot.resumeservice.mapper.FileMapper;
import com.careerpilot.resumeservice.mapper.ResumeMapper;
import com.careerpilot.resumeservice.mapper.ResumeSectionMapper;
import com.careerpilot.resumeservice.mapper.ResumeVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历业务服务。
 *
 * <p>上传链路：落盘 → 抽正文 → 建版本 → 切章节 → 登记 AI 任务 → 立即返回。
 * ATS 评分由 Agent 异步算完再回调写回，前端拿 taskId 轮询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper versionMapper;
    private final ResumeSectionMapper sectionMapper;
    private final FileMapper fileMapper;
    private final ResumeTextExtractor textExtractor;
    private final ResumeSectionSplitter sectionSplitter;
    private final FileStorageService fileStorage;
    private final AiTaskClient aiTaskClient;
    private final CareerPilotProperties properties;

    // ── 上传 ────────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public ResumeCreatedVO upload(String userId, MultipartFile file, String title) {
        if (file == null || file.isEmpty()) {
            throw new ResumeUploadException("请选择要上传的简历文件");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new ResumeUploadException("文件超过大小限制（"
                    + properties.getMaxFileSize() / 1024 / 1024 + "MB）");
        }

        // 1) 落盘 + 记文件元信息
        FileStorageService.StoredFile stored = storeFile(userId, file);

        // 2) 抽正文。抽不出来直接失败并清理已落盘的文件，不留垃圾数据
        String text;
        try {
            text = textExtractor.extract(file);
        } catch (IOException | RuntimeException e) {
            fileStorage.deleteQuietly(stored.getStorageKey());
            throw new ResumeUploadException(e.getMessage());
        }

        // 3) 建简历 + 初始版本
        String resumeBizId = "res_" + IdUtil.fastSimpleUUID().substring(0, 16);
        String versionId = "ver_" + IdUtil.fastSimpleUUID().substring(0, 16);
        LocalDateTime now = LocalDateTime.now();

        Resume resume = new Resume();
        resume.setResumeId(resumeBizId);
        resume.setUserId(userId);
        resume.setTitle(title == null || title.isBlank() ? stored.getFilename() : title.trim());
        resume.setCurrentVersionId(versionId);
        resume.setAtsScore(0);
        resume.setParseStatus("PENDING");
        resume.setFileId(stored.getFileId());
        resume.setIsPrimary(0);
        resume.setIsDeleted(0);
        resume.setCreatedAt(now);
        resume.setUpdatedAt(now);
        resumeMapper.insert(resume);

        ResumeVersion version = new ResumeVersion();
        version.setVersionId(versionId);
        version.setResumeId(resumeBizId);
        version.setVersionNo(1);
        version.setParsedText(text);
        version.setChangeNote("初始上传");
        version.setCreatedAt(now);
        versionMapper.insert(version);

        // 4) 切章节（反范式，供后续 Agent 局部调用）
        int sectionCount = saveSections(resumeBizId, versionId, text);

        log.info("简历上传完成: resumeId={}, 字数={}, 章节数={}",
                resumeBizId, text.length(), sectionCount);

        // 5) 登记分析任务（失败不影响上传成功）
        String taskId = triggerAnalyze(resume, text);

        ResumeCreatedVO vo = new ResumeCreatedVO();
        vo.setResumeId(resume.getId());
        vo.setResumeBizId(resumeBizId);
        vo.setVersionId(versionId);
        vo.setTitle(resume.getTitle());
        vo.setParseStatus(taskId != null ? "PROCESSING" : "PENDING");
        vo.setTaskId(taskId);
        vo.setSectionCount(sectionCount);
        vo.setWordCount(text.length());
        vo.setCreatedAt(resume.getCreatedAt());
        return vo;
    }

    private FileStorageService.StoredFile storeFile(String userId, MultipartFile file) {
        try {
            FileStorageService.StoredFile stored = fileStorage.store(file);
            FileRecord record = new FileRecord();
            record.setFileId(stored.getFileId());
            record.setUserId(userId);
            record.setFilename(stored.getFilename());
            record.setContentType(stored.getContentType());
            record.setSizeBytes(stored.getSizeBytes());
            record.setStorageKey(stored.getStorageKey());
            record.setUploadStatus(1);
            record.setMd5(stored.getMd5());
            record.setCreatedAt(LocalDateTime.now());
            fileMapper.insert(record);
            return stored;
        } catch (IOException e) {
            throw new ResumeUploadException("文件保存失败: " + e.getMessage());
        }
    }

    private int saveSections(String resumeBizId, String versionId, String text) {
        List<ResumeSectionSplitter.Section> sections = sectionSplitter.split(text);
        LocalDateTime now = LocalDateTime.now();
        for (ResumeSectionSplitter.Section s : sections) {
            ResumeSection entity = new ResumeSection();
            entity.setResumeId(resumeBizId);
            entity.setVersionId(versionId);
            entity.setSectionType(s.type());
            entity.setSectionOrder(s.order());
            entity.setSectionTitle(s.title());
            entity.setContentText(s.content());
            entity.setCreatedAt(now);
            sectionMapper.insert(entity);
        }
        return sections.size();
    }

    /** 登记分析任务。成功则把 parse_status 推到 PROCESSING。 */
    public String triggerAnalyze(Resume resume, String text) {
        String taskId = aiTaskClient.submitResumeAnalyze(
                resume.getUserId(), resume.getResumeId(), resume.getCurrentVersionId(), text);
        if (taskId != null) {
            Resume patch = new Resume();
            patch.setId(resume.getId());
            patch.setParseStatus("PROCESSING");
            patch.setUpdatedAt(LocalDateTime.now());
            resumeMapper.updateById(patch);
        }
        return taskId;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> reparse(String userId, Long id) {
        Resume resume = requireOwned(userId, id);
        ResumeVersion version = currentVersion(resume);
        if (version == null || version.getParsedText() == null || version.getParsedText().isBlank()) {
            throw new ResumeNotFoundException(id);
        }

        Resume patch = new Resume();
        patch.setId(resume.getId());
        patch.setParseStatus("PENDING");
        patch.setUpdatedAt(LocalDateTime.now());
        resumeMapper.updateById(patch);

        String taskId = triggerAnalyze(resume, version.getParsedText());

        Map<String, String> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("parseStatus", taskId != null ? "PROCESSING" : "PENDING");
        return result;
    }

    // ── 查询 ────────────────────────────────────────────────────

    public Page<ResumeListItemVO> list(String userId, int page, int size, String keyword) {
        LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .eq(Resume::getIsDeleted, 0)
                .orderByDesc(Resume::getUpdatedAt);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Resume::getTitle, keyword.trim());
        }

        Page<Resume> result = resumeMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ResumeListItemVO> vo = new Page<>(page, size, result.getTotal());
        vo.setRecords(result.getRecords().stream().map(r -> {
            ResumeListItemVO item = new ResumeListItemVO();
            item.setResumeId(r.getId());
            item.setResumeBizId(r.getResumeId());
            item.setTitle(r.getTitle());
            item.setAtsScore(r.getAtsScore());
            item.setParseStatus(r.getParseStatus());
            item.setIsPrimary(r.getIsPrimary() != null && r.getIsPrimary() == 1);
            item.setUpdatedAt(r.getUpdatedAt());
            return item;
        }).toList());
        return vo;
    }

    public ResumeDetailVO detail(String userId, Long id) {
        Resume resume = requireOwned(userId, id);
        ResumeVersion version = currentVersion(resume);

        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setResumeId(resume.getId());
        vo.setResumeBizId(resume.getResumeId());
        vo.setTitle(resume.getTitle());
        vo.setAtsScore(resume.getAtsScore());
        vo.setParseStatus(resume.getParseStatus());
        vo.setTextContent(version == null ? null : version.getParsedText());
        vo.setCreatedAt(resume.getCreatedAt());
        vo.setUpdatedAt(resume.getUpdatedAt());
        return vo;
    }

    /**
     * ATS 评分详情。直接返回 Agent 的 AnalysisResult 原样结构，
     * 前端已经按 {@code overall_score / dimensions / suggestions} 在渲染了。
     */
    public Map<String, Object> atsScore(String userId, Long id) {
        Resume resume = requireOwned(userId, id);
        ResumeVersion version = currentVersion(resume);
        if (version == null || version.getAnalysisJson() == null || version.getAnalysisJson().isBlank()) {
            return null;
        }
        Map<String, Object> result = JSONUtil.parseObj(version.getAnalysisJson()).toBean(Map.class);
        result.put("updatedAt", version.getCreatedAt() == null ? null : version.getCreatedAt().toString());
        return result;
    }

    public List<ResumeVersionVO> versions(String userId, Long id) {
        Resume resume = requireOwned(userId, id);
        return versionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getResumeId, resume.getResumeId())
                        .orderByDesc(ResumeVersion::getVersionNo))
                .stream()
                .map(v -> {
                    ResumeVersionVO vo = new ResumeVersionVO();
                    vo.setVersionId(v.getVersionId());
                    vo.setVersionNo(v.getVersionNo());
                    vo.setAtsScore(v.getAtsScore());
                    vo.setChangeNote(v.getChangeNote());
                    vo.setCreatedAt(v.getCreatedAt());
                    return vo;
                })
                .toList();
    }

    public List<ResumeSectionVO> sections(String userId, Long id) {
        Resume resume = requireOwned(userId, id);
        return sectionMapper.selectList(new LambdaQueryWrapper<ResumeSection>()
                        .eq(ResumeSection::getResumeId, resume.getResumeId())
                        .eq(ResumeSection::getVersionId, resume.getCurrentVersionId())
                        .orderByAsc(ResumeSection::getSectionOrder))
                .stream()
                .map(s -> {
                    ResumeSectionVO vo = new ResumeSectionVO();
                    vo.setSectionType(s.getSectionType());
                    vo.setSectionTitle(s.getSectionTitle());
                    vo.setSectionOrder(s.getSectionOrder());
                    vo.setContentText(s.getContentText());
                    return vo;
                })
                .toList();
    }

    // ── 删除 ────────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public void delete(String userId, Long id) {
        Resume resume = requireOwned(userId, id);
        Resume patch = new Resume();
        patch.setId(resume.getId());
        patch.setIsDeleted(1);
        patch.setUpdatedAt(LocalDateTime.now());
        resumeMapper.updateById(patch);
    }

    /** 设为主简历：同一个人只能有一份主简历。 */
    @Transactional(rollbackFor = Exception.class)
    public void setPrimary(String userId, Long id) {
        Resume resume = requireOwned(userId, id);

        Resume clear = new Resume();
        clear.setUserId(userId);
        clear.setIsPrimary(0);
        clear.setUpdatedAt(LocalDateTime.now());
        resumeMapper.update(clear, new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .eq(Resume::getIsPrimary, 1));

        Resume patch = new Resume();
        patch.setId(resume.getId());
        patch.setIsPrimary(1);
        patch.setUpdatedAt(LocalDateTime.now());
        resumeMapper.updateById(patch);
    }

    // ── 内部 ────────────────────────────────────────────────────

    public Resume findByBizId(String resumeBizId) {
        return resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getResumeId, resumeBizId)
                .eq(Resume::getIsDeleted, 0));
    }

    public ResumeVersion currentVersion(Resume resume) {
        if (resume == null || resume.getCurrentVersionId() == null) {
            return null;
        }
        return versionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getVersionId, resume.getCurrentVersionId()));
    }

    /** 按归属校验取简历；越权与不存在统一报 1404。 */
    public Resume requireOwned(String userId, Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null || resume.getIsDeleted() == 1 || !resume.getUserId().equals(userId)) {
            throw new ResumeNotFoundException(id);
        }
        return resume;
    }

    public static class ResumeNotFoundException extends RuntimeException {
        public ResumeNotFoundException(Long id) {
            super("简历不存在: " + id);
        }
    }

    public static class ResumeUploadException extends RuntimeException {
        public ResumeUploadException(String message) {
            super(message);
        }
    }
}
