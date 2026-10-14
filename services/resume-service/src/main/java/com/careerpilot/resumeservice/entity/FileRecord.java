package com.careerpilot.resumeservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元信息。对应 {@code t_file}。
 */
@Data
@TableName("t_file")
public class FileRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("file_id")
    private String fileId;

    @TableField("user_id")
    private String userId;

    private String filename;

    @TableField("content_type")
    private String contentType;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("storage_key")
    private String storageKey;

    /** 0=uploading 1=completed 2=failed */
    @TableField("upload_status")
    private Integer uploadStatus;

    private String md5;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
