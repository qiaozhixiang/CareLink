package com.carelink.app.data.remote.service;

import com.carelink.app.data.local.pref.PreferenceManager;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 自动添加 Bearer Token 的拦截器
 * 不需要认证的接口（登录/注册/版本检查）不附加 token，
 * 避免旧 token 干扰或导致 401 错误。
 */
public class AuthInterceptor implements Interceptor {

    // 不需要认证的路径前缀（不走此拦截器直接放行）
    private static final String[] NO_AUTH_PATHS = {
        "/api/auth/login",
        "/api/auth/register",
        "/api/auth/sendCode",
        "/api/auth/sendResetCode",
        "/api/auth/resetPassword",
        "/api/auth/wechat",
        "/api/app/version",
        "/api/admin/"
    };

    private final PreferenceManager preferenceManager;

    public AuthInterceptor(PreferenceManager preferenceManager) {
        this.preferenceManager = preferenceManager;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        String token = preferenceManager.getToken();
        Request original = chain.request();
        String path = original.url().encodedPath();

        // 无 token 或在免认证路径上，不附加 Authorization
        if (token == null || token.trim().isEmpty()) {
            return chain.proceed(original);
        }

        // 检查是否在免认证路径列表中（用 endsWith 更精确）
        for (String noAuthPath : NO_AUTH_PATHS) {
            if (path.endsWith(noAuthPath)) {
                return chain.proceed(original);
            }
        }

        Request request = original.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();
        return chain.proceed(request);
    }
}
