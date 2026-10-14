package com.careerpilot.resumeservice.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.IdUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

/**
 * 简历文件落盘。
 *
 * <p>当前实现写本地磁盘，按 {@code yyyy/MM/} 分目录。二期切 MinIO 时
 * 只要换掉 {@link #store} 这一个方法，上层不用动。
 *
 * <p>存储 key 里用随机业务 ID 而不是原始文件名 —— 原始文件名可能带路径分隔符、
 * 中文或超长字符，直接拼路径有目录穿越风险。
 */
@Slf4j
@Component
public class FileStorageService {

    @Value("${careerpilot.storage-root:./data/uploads}")
    private String storageRoot;

    @Data
    public static class StoredFile {
        private String fileId;
        private String filename;
        private String contentType;
        private Long sizeBytes;
        /** 相对 storage-root 的存储 key，落库用。 */
        private String storageKey;
        private String md5;
    }

    public StoredFile store(MultipartFile file) throws IOException {
        String fileId = "file_" + IdUtil.fastSimpleUUID().substring(0, 16);
        String extension = ResumeTextExtractor.extensionOf(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String safeName = fileId + (extension.isBlank() ? "" : "." + extension);

        LocalDate today = LocalDate.now();
        String storageKey = String.format("%d/%02d/%s", today.getYear(), today.getMonthValue(), safeName);
        Path target = resolve(storageKey);

        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        }

        StoredFile stored = new StoredFile();
        stored.setFileId(fileId);
        stored.setFilename(file.getOriginalFilename() == null ? safeName : file.getOriginalFilename());
        stored.setContentType(file.getContentType());
        stored.setSizeBytes(file.getSize());
        stored.setStorageKey(storageKey);
        stored.setMd5(DigestUtil.md5Hex(target.toFile()));
        return stored;
    }

    /**
     * 解析 storage key 指向的真实路径。
     *
     * <p>key 来自数据库而不是用户输入，但仍然做一次归一化校验，
     * 防止历史上写入的脏数据把读取操作引到存储根目录之外。
     */
    public Path resolve(String storageKey) {
        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的存储路径: " + storageKey);
        }
        return target;
    }

    public boolean exists(String storageKey) {
        return storageKey != null && Files.exists(resolve(storageKey));
    }

    public void deleteQuietly(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            FileUtil.del(resolve(storageKey));
        } catch (Exception e) {
            log.warn("删除文件失败: key={}, err={}", storageKey, e.getMessage());
        }
    }
}
