package com.careerpilot.aitaskscheduler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 统一响应包络。各微服务保持同一形状，前端拦截器只需一套解析逻辑。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, traceId());
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(0, "success", null, traceId());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, traceId());
    }

    private static String traceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
