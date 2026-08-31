package com.carelink.app.data.remote.dto;

/**
 * 统一服务端响应包装
 * 后端返回格式：{"success":true/false, "message":"xxx", "data":{...}}
 * 注意：后端用 success(boolean)，不用 code(int)
 */
public class BaseResponse<T> {
    private boolean success;   // 后端字段：true=成功，false=失败
    private String message;     // 后端字段：提示信息
    private T data;             // 后端字段：数据载荷

    /** 兼容旧接口：如果 success 未定义，则根据 code 判断 */
    private int code;

    /** 主判断方法：优先用 success，fallback 到 code==200 */
    public boolean isSuccess() {
        // 后端返回了 success 字段
        return success || code == 200;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}
