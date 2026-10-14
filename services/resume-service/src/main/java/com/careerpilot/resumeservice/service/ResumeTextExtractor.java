package com.careerpilot.resumeservice.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 简历正文抽取。
 *
 * <p>ATS 7 维评分、语义相似度、关键词覆盖率全都建立在"一份纯文本"之上，
 * 所以这一步是整条链路的入口，抽不出文本后面全都白搭。
 *
 * <p>放在 Java 侧而不是 Agent 侧，是因为抽出来的正文要存进
 * {@code t_resume_version.parsed_text}，供后续的重新解析和匹配打分复用 ——
 * 没必要每次都把文件再传一遍给 Python。
 */
@Slf4j
@Component
public class ResumeTextExtractor {

    /** 支持的最大正文长度，超出截断。中文简历正常在 3 万字以内。 */
    private static final int MAX_TEXT_LENGTH = 200_000;

    /**
     * @throws UnsupportedResumeFormatException 格式不支持
     * @throws IOException                        读取失败或文件损坏
     */
    public String extract(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extensionOf(filename);

        byte[] bytes = file.getBytes();
        if (bytes.length == 0) {
            throw new IOException("上传的文件为空");
        }

        String text = switch (ext) {
            case "pdf" -> fromPdf(bytes);
            case "docx" -> fromDocx(bytes);
            case "doc" -> fromDoc(bytes);
            case "txt", "md", "markdown", "json" -> fromPlainText(bytes);
            default -> throw new UnsupportedResumeFormatException(ext);
        };

        text = normalize(text);
        if (text.isBlank()) {
            // 扫描件 PDF 是最常见的"抽不出字"场景，要给出可操作的提示而不是干瘪的异常
            throw new EmptyResumeTextException(
                    "未能从文件中提取到文字。若是扫描件/图片版简历，请先做 OCR 或改为可复制文字的版本。");
        }
        return text;
    }

    // ── 各格式 ──────────────────────────────────────────────────

    private String fromPdf(byte[] bytes) throws IOException {
        // PDFBox 3.x 移除了 PDDocument.load(byte[])，统一走 Loader
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String fromDocx(byte[] bytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String fromDoc(byte[] bytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes);
             HWPFDocument document = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String fromPlainText(byte[] bytes) {
        // 中文简历基本都是 UTF-8；先按 UTF-8 试，出现大量替换字符再退到 GBK
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (countReplacementChars(text) > bytes.length / 100) {
            text = new String(bytes, Charset.forName("GBK"));
        }
        return text;
    }

    // ── 规范化 ──────────────────────────────────────────────────

    /**
     * 统一换行、压缩空白、修掉最常见的 PDF 抽取噪声（页眉页脚、断字）。
     * Agent 侧的规则打分对空白敏感度很高，脏文本会直接拉低分数。
     */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace(' ', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        // 连续空行压到最多一个（保留段落感）
        text = text.replaceAll("\n{3,}", "\n\n");
        // 行尾空白
        text = text.replaceAll("[ \t]+\n", "\n");
        // 连续空格压成一个（但保留缩进语义，2 空格即可）
        text = text.replaceAll(" {4,}", "  ");
        // PDF 常见的软连字符断词：exam- ple -> example
        text = text.replaceAll("(\\w)-\\s+(\\w)", "$1$2");

        text = text.trim();
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        return text;
    }

    private static int countReplacementChars(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\uFFFD') {
                count++;
            }
        }
        return count;
    }

    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // ── 异常 ────────────────────────────────────────────────────

    public static class UnsupportedResumeFormatException extends RuntimeException {
        public UnsupportedResumeFormatException(String ext) {
            super("不支持的简历格式: " + (ext == null || ext.isBlank() ? "未知" : ext)
                    + "，请上传 PDF / DOCX / DOC / TXT");
        }
    }

    public static class EmptyResumeTextException extends RuntimeException {
        public EmptyResumeTextException(String message) {
            super(message);
        }
    }
}
