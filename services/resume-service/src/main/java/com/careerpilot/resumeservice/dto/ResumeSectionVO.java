package com.careerpilot.resumeservice.dto;

import lombok.Data;

/**
 * 简历章节。反范式落库，供 Agent 局部调用与前端分段渲染。
 */
@Data
public class ResumeSectionVO {

    private String sectionType;
    private String sectionTitle;
    private Integer sectionOrder;
    private String contentText;
}
