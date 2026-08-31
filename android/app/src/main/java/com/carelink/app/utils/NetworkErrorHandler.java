package com.carelink.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.ConnectException;

import retrofit2.Response;

/**
 * 统一网络错误处理工具类 - 正式上线版
 *
 * 错误分类：
 *   NETWORK_UNAVAILABLE  - 设备无网络连接
 *   TIMEOUT              - 请求超时
 *   SERVER_ERROR         - 服务器 5xx 错误
 *   CLIENT_ERROR         - 客户端请求错误 4xx
 *   AUTH_ERROR           - 401/403 鉴权失效
 *   NOT_FOUND            - 404 资源不存在
 *   RATE_LIMITED         - 429 请求过于频繁
 *   BUSINESS_ERROR       - 业务层返回的错误（非 HTTP）
 *   UNKNOWN              - 未知异常
 */
public class NetworkErrorHandler {

    public static final String TAG = "NetworkErrorHandler";

    // 错误类型枚举（用于业务层统一判断处理）
    public enum ErrorType {
        NETWORK_UNAVAILABLE,
        TIMEOUT,
        SERVER_ERROR,
        CLIENT_ERROR,
        AUTH_ERROR,
        NOT_FOUND,
        RATE_LIMITED,
        BUSINESS_ERROR,
        UNKNOWN
    }

    // 错误结果封装
    public static class NetworkError {
        public final ErrorType type;
        public final String userMessage;
        public final int httpCode;       // HTTP 状态码（-1 表示非 HTTP）
        public final String serverMsg;   // 服务端原始 message
        public final boolean shouldForceLogin;

        public NetworkError(ErrorType type, String userMessage,
                            int httpCode, String serverMsg) {
            this(type, userMessage, httpCode, serverMsg, false);
        }

        public NetworkError(ErrorType type, String userMessage,
                            int httpCode, String serverMsg, boolean shouldForceLogin) {
            this.type = type;
            this.userMessage = userMessage;
            this.httpCode = httpCode;
            this.serverMsg = serverMsg;
            this.shouldForceLogin = shouldForceLogin;
        }

        public static NetworkError networkUnavailable() {
            return new NetworkError(ErrorType.NETWORK_UNAVAILABLE,
                    "当前无网络连接，请检查网络设置", -1, null);
        }

        public static NetworkError timeout() {
            return new NetworkError(ErrorType.TIMEOUT,
                    "网络超时，请检查网络后重试", -1, null);
        }

        public static NetworkError serverError(int code) {
            return new NetworkError(ErrorType.SERVER_ERROR,
                    "服务器异常，请稍后重试", code, null);
        }

        public static NetworkError authError(int code) {
            return new NetworkError(ErrorType.AUTH_ERROR,
                    "登录已过期，请重新登录", code, null, true);
        }

        public static NetworkError businessError(String serverMsg, int code) {
            return new NetworkError(ErrorType.BUSINESS_ERROR,
                    serverMsg != null ? serverMsg : "操作失败，请稍后重试", code, serverMsg);
        }

        public static NetworkError unknown(Throwable t) {
            return new NetworkError(ErrorType.UNKNOWN,
                    "操作失败，请稍后重试", -1, t != null ? t.getMessage() : null);
        }
    }

    /** 判断当前是否有可用网络连接 */
    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }

    /**
     * 统一入口：处理 onFailure 回调
     * 页面层只需调用此方法获取 NetworkError，展示 userMessage 即可
     */
    public static NetworkError handleFailure(Context context, Throwable t) {
        if (!isNetworkAvailable(context)) {
            Log.w(TAG, "网络不可用");
            return NetworkError.networkUnavailable();
        }
        if (t instanceof SocketTimeoutException) {
            Log.w(TAG, "请求超时: " + t.getMessage());
            return NetworkError.timeout();
        } else if (t instanceof UnknownHostException) {
            Log.w(TAG, "DNS/Host 解析失败: " + t.getMessage());
            return new NetworkError(ErrorType.NETWORK_UNAVAILABLE,
                    "无法连接服务器，请检查网络", -1, null);
        } else if (t instanceof ConnectException) {
            Log.w(TAG, "连接失败: " + t.getMessage());
            return new NetworkError(ErrorType.SERVER_ERROR,
                    "连接失败，服务器可能暂时不可用", -1, null);
        } else if (t instanceof java.io.IOException) {
            Log.w(TAG, "IO 异常: " + t.getMessage());
            return new NetworkError(ErrorType.NETWORK_UNAVAILABLE,
                    "网络异常，请稍后重试", -1, null);
        } else {
            Log.e(TAG, "未知异常", t);
            return NetworkError.unknown(t);
        }
    }

    /**
     * 统一入口：处理 HTTP 响应
     * 页面层只需调用此方法获取 NetworkError
     */
    public static NetworkError handleResponse(Response<?> response) {
        if (response == null) {
            return new NetworkError(ErrorType.UNKNOWN,
                    "请求失败，请重试", -1, null);
        }
        int code = response.code();

        switch (code) {
            case 200:
                return null; // 成功，不返回错误
            case 401:
            case 403:
                return NetworkError.authError(code);
            case 404:
                return new NetworkError(ErrorType.NOT_FOUND,
                        "请求的资源不存在", code, null);
            case 429:
                return new NetworkError(ErrorType.RATE_LIMITED,
                        "操作过于频繁，请稍后再试", code, null);
            case 500:
            case 502:
            case 503:
            case 504:
                return NetworkError.serverError(code);
            default:
                if (code >= 500) {
                    return NetworkError.serverError(code);
                } else if (code >= 400) {
                    // 尝试解析服务端 message
                    String msg = parseErrorBody(response);
                    return new NetworkError(ErrorType.CLIENT_ERROR,
                            msg != null ? msg : "请求参数有误，请检查输入", code, msg);
                }
                return new NetworkError(ErrorType.UNKNOWN,
                        "未知错误", code, null);
        }
    }

    /**
     * 统一解析 BaseResponse 中的业务错误
     * 用于 response.isSuccessful() == true 但 resp.isSuccess() == false 的情况
     */
    public static NetworkError handleBusinessError(String serverMessage, int httpCode) {
        String msg = sanitizeUserMessage(serverMessage, "操作失败，请稍后重试");
        return NetworkError.businessError(msg, httpCode);
    }

    /** 解析 HTTP errorBody 字符串中的 message 字段 */
    public static String parseErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                return sanitizeUserMessage(extractMessage(body), null);
            }
        } catch (Exception e) {
            Log.w(TAG, "解析 errorBody 失败", e);
        }
        return null;
    }

    private static String extractMessage(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) return null;
        try {
            // 支持 {"message":"xxx"} 和 {"msg":"xxx"}
            String[] keys = {"\"message\"", "\"msg\""};
            for (String key : keys) {
                int start = rawBody.indexOf(key);
                if (start == -1) continue;
                int colon = rawBody.indexOf(":", start);
                int quote1 = rawBody.indexOf("\"", colon + 1);
                int quote2 = rawBody.indexOf("\"", quote1 + 1);
                if (quote1 != -1 && quote2 != -1) {
                    String msg = rawBody.substring(quote1 + 1, quote2).trim();
                    if (!msg.isEmpty()) return msg;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "extractMessage 异常", e);
        }
        return null;
    }

    private static String sanitizeUserMessage(String rawMessage, String fallback) {
        if (rawMessage == null) {
            return fallback;
        }
        String msg = rawMessage.trim();
        if (msg.isEmpty()) {
            return fallback;
        }
        String lower = msg.toLowerCase();
        if (msg.length() > 80 || lower.contains("<html") || lower.contains("<!doctype")
                || lower.contains("exception") || lower.contains("java.") || lower.contains("retrofit")
                || msg.contains("\n") || msg.contains("\r")) {
            return fallback;
        }
        return msg;
    }


    /** 兼容旧接口：获取用户友好的错误文案（直接字符串版） */
    public static String getErrorMessage(Throwable t) {
        return handleFailure(null, t).userMessage;
    }

    /** 兼容旧接口：获取 HTTP 错误文案 */
    public static String getHttpErrorMessage(Response<?> response) {
        NetworkError err = handleResponse(response);
        return err != null ? err.userMessage : "请求失败";
    }
}
