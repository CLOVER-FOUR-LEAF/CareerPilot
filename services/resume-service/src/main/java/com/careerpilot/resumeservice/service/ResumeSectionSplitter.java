package com.careerpilot.resumeservice.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历章节切分。
 *
 * <p>章节单独存一份（{@code t_resume_section}）是为了让后续 Agent 能只取需要的部分，
 * 也方便按章节做局部改写。切不准不影响主流程 —— 拿不到章节时整体退化成单段。
 */
@Component
public class ResumeSectionSplitter {

    /** 章节标题的典型写法。命中后，下一个标题之前的内容都归到这一节。 */
    private static final Pattern SECTION_HEADING = Pattern.compile(
            "^\\s*(?:[【\\[]\\s*)?([\\u4e00-\\u9fa5A-Za-z][\\u4e00-\\u9fa5A-Za-z /·]{1,20})"
                    + "(?:\\s*[】\\]])?\\s*[:：]?\\s*$");

    /** 归一化后的章节类型 → 别名表。 */
    private static final List<String[]> SECTION_ALIASES = List.of(
            new String[]{"education", "教育背景", "教育经历", "教育", "学历", "education"},
            new String[]{"experience", "工作经历", "工作经验", "工作", "实习经历", "work experience", "employment"},
            new String[]{"project", "项目经历", "项目经验", "项目", "project experience", "projects"},
            new String[]{"skill", "专业技能", "技能", "技术栈", "skills", "technical skills"},
            new String[]{"summary", "自我评价", "个人简介", "个人总结", "summary", "profile", "objective"},
            new String[]{"certificate", "证书", "荣誉", "获奖", "certificates", "awards"}
    );

    public record Section(String type, String title, int order, String content) {
    }

    public List<Section> split(String text) {
        List<Section> sections = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return sections;
        }

        String[] lines = text.split("\n");
        String currentType = null;
        String currentTitle = null;
        StringBuilder buffer = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            String headingType = matchHeading(line);

            if (headingType != null && !line.isEmpty() && line.length() <= 24) {
                // 遇到新章节标题，先把上一节落袋
                flush(sections, currentType, currentTitle, buffer);
                currentType = headingType;
                currentTitle = line;
                buffer.setLength(0);
                continue;
            }

            if (line.isEmpty()) {
                buffer.append('\n');
            } else {
                buffer.append(line).append('\n');
            }
        }
        flush(sections, currentType, currentTitle, buffer);

        if (sections.isEmpty()) {
            // 完全没识别出章节：整体作为一段，免得 section 表空着
            sections.add(new Section("other", "简历正文", 0, text.trim()));
        }
        return sections;
    }

    private void flush(List<Section> sections, String type, String title, StringBuilder buffer) {
        String content = buffer.toString().trim();
        if (type == null || content.isEmpty()) {
            buffer.setLength(0);
            return;
        }
        sections.add(new Section(type, title, sections.size(), content));
        buffer.setLength(0);
    }

    /**
     * 判断一行是不是章节标题，并归一化成章节类型。
     *
     * <p>用「短 + 命中别名表」双重条件，是因为简历正文里
     * "Java 熟练使用" 这类以技能词开头的行很容易被误判成标题。
     */
    private String matchHeading(String line) {
        if (line.isEmpty() || line.length() > 24) {
            return null;
        }
        Matcher matcher = SECTION_HEADING.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        String candidate = matcher.group(1).trim();
        // 去掉行内的项目符号前缀后再判断
        String normalized = candidate.replaceAll("^[•·\\-*\\d.、\\s]+", "").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }

        for (String[] aliasGroup : SECTION_ALIASES) {
            for (int i = 1; i < aliasGroup.length; i++) {
                if (normalized.equals(aliasGroup[i]) || normalized.startsWith(aliasGroup[i])) {
                    return aliasGroup[0];
                }
            }
        }
        return "other";
    }
}
