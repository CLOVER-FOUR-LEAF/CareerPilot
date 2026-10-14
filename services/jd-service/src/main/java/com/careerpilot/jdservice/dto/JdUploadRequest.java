package com.careerpilot.jdservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建 JD 的请求。字段名与前端 {@code jdApi.upload} 严格一致。
 */
@Data
public class JdUploadRequest {

    @Size(max = 128, message = "职位名称过长")
    private String jobTitle;

    @Size(max = 128, message = "公司名称过长")
    private String companyName;

    @NotBlank(message = "职位描述不能为空")
    private String rawText;

    /** MANUAL / URL / FILE，缺省 MANUAL。 */
    @Pattern(regexp = "MANUAL|URL|FILE", message = "来源类型只能是 MANUAL / URL / FILE")
    private String sourceType;

    @Size(max = 512, message = "来源链接过长")
    private String sourceUrl;

    @Size(max = 64, message = "薪资范围过长")
    private String salaryRange;
}
