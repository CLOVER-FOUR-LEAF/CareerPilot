package com.careerpilot.adminservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一响应包络，与各业务服务保持一致（code/message/data/traceId）。
 */
@Data
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, "");
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, "");
    }
}
