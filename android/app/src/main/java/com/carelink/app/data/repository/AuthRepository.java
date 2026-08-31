package com.carelink.app.data.repository;

import android.content.Context;
import android.net.Uri;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.remote.api.AuthApi;
import com.carelink.app.data.remote.dto.BaseResponse;
import com.carelink.app.data.remote.dto.LoginRequest;
import com.carelink.app.data.remote.dto.LoginResponse;
import com.carelink.app.data.remote.dto.ProfileUpdateRequest;
import com.carelink.app.data.remote.dto.SendCodeRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class AuthRepository {

    private final AuthApi authApi;
    private final PreferenceManager preferenceManager;
    private final Context appContext;

    @Inject
    public AuthRepository(AuthApi authApi,
                          PreferenceManager preferenceManager,
                          @ApplicationContext Context appContext) {
        this.authApi = authApi;
        this.preferenceManager = preferenceManager;
        this.appContext = appContext;
    }

    public interface ResultCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    public void sendCode(String phone, ResultCallback<Void> callback) {
        authApi.sendCode(new SendCodeRequest(phone)).enqueue(new Callback<BaseResponse<Void>>() {
            @Override
            public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {
                BaseResponse<Void> body = response.body();
                if (response.isSuccessful() && body != null && body.isSuccess()) {
                    callback.onSuccess(null);
                } else {
                    callback.onError(extractErrorMessage(response, body, "发送验证码失败"));
                }
            }
            @Override
            public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                callback.onError(getSafeErrorMessage(t, "发送验证码失败，请稍后重试"));
            }

        });
    }

    public void login(String email, String password, ResultCallback<LoginResponse> callback) {
        authApi.login(new LoginRequest(email, password)).enqueue(new Callback<BaseResponse<LoginResponse>>() {
            @Override
            public void onResponse(Call<BaseResponse<LoginResponse>> call,
                                   Response<BaseResponse<LoginResponse>> response) {
                BaseResponse<LoginResponse> body = response.body();
                if (response.isSuccessful() && body != null && body.isSuccess()) {
                    LoginResponse data = body.getData();
                    if (data == null || data.getToken() == null || data.getToken().trim().isEmpty()) {
                        callback.onError("登录失败，服务器返回数据不完整");
                        return;
                    }
                    preferenceManager.saveToken(data.getToken());
                    preferenceManager.saveUserId(data.getUserId());
                    preferenceManager.saveEmail(data.getEmail() != null ? data.getEmail() : "");
                    preferenceManager.saveNickname(data.getNickname() != null ? data.getNickname() : "");
                    preferenceManager.saveAvatarUrl(data.getAvatarUrl() != null ? data.getAvatarUrl() : "");
                    callback.onSuccess(data);
                } else {
                    callback.onError(extractErrorMessage(response, body, "登录失败，请检查账号或密码"));
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<LoginResponse>> call, Throwable t) {
                callback.onError(getSafeErrorMessage(t, "登录失败，请稍后重试"));
            }

        });
    }

    public void updateProfile(String nickname, String avatarUrl, ResultCallback<LoginResponse> callback) {
        authApi.updateProfile(new ProfileUpdateRequest(nickname, avatarUrl))
                .enqueue(new Callback<BaseResponse<LoginResponse>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<LoginResponse>> call,
                                           Response<BaseResponse<LoginResponse>> response) {
                        BaseResponse<LoginResponse> body = response.body();
                        if (response.isSuccessful() && body != null && body.isSuccess() && body.getData() != null) {
                            LoginResponse data = body.getData();
                            applyProfileToLocal(data, nickname, avatarUrl);
                            callback.onSuccess(data);
                        } else {
                            callback.onError(extractErrorMessage(response, body, "资料更新失败，请稍后重试"));
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<LoginResponse>> call, Throwable t) {
                        callback.onError(getSafeErrorMessage(t, "资料更新失败，请稍后重试"));
                    }
                });
    }

    public void logout() {
        authApi.logout().enqueue(new Callback<BaseResponse<Void>>() {
            @Override public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {}
            @Override public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {}
        });
        preferenceManager.clear();
    }

    public void uploadAvatar(Uri uri, ResultCallback<String> callback) {
        try {
            File tempFile = createTempFileFromUri(uri);
            if (tempFile == null || !tempFile.exists()) {
                callback.onError("头像读取失败，请重新选择图片");
                return;
            }
            String mimeType = appContext.getContentResolver().getType(uri);
            if (mimeType == null || mimeType.trim().isEmpty()) {
                mimeType = "image/jpeg";
            }
            RequestBody fileBody = RequestBody.create(tempFile, MediaType.parse(mimeType));
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", tempFile.getName(), fileBody);
            authApi.uploadAvatar(filePart).enqueue(new Callback<BaseResponse<Map<String, String>>>() {
                @Override
                public void onResponse(Call<BaseResponse<Map<String, String>>> call,
                                       Response<BaseResponse<Map<String, String>>> response) {
                    safeDeleteFile(tempFile);
                    BaseResponse<Map<String, String>> body = response.body();
                    if (response.isSuccessful() && body != null && body.isSuccess() && body.getData() != null) {
                        String avatarUrl = body.getData().get("avatarUrl");
                        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                            preferenceManager.saveAvatarUrl(avatarUrl.trim());
                            callback.onSuccess(avatarUrl.trim());
                            return;
                        }
                    }
                    callback.onError(extractErrorMessage(response, body, "头像上传失败，请稍后重试"));
                }

                @Override
                public void onFailure(Call<BaseResponse<Map<String, String>>> call, Throwable t) {
                    safeDeleteFile(tempFile);
                    callback.onError(getSafeErrorMessage(t, "头像上传失败，请稍后重试"));
                }
            });
        } catch (Exception e) {
            callback.onError("头像上传失败，请稍后重试");
        }
    }

    private void applyProfileToLocal(LoginResponse data, String fallbackNickname, String fallbackAvatarUrl) {
        String resolvedNickname = data.getNickname() != null && !data.getNickname().trim().isEmpty()
                ? data.getNickname().trim()
                : (fallbackNickname == null ? "" : fallbackNickname.trim());
        String resolvedAvatarUrl = data.getAvatarUrl() != null && !data.getAvatarUrl().trim().isEmpty()
                ? data.getAvatarUrl().trim()
                : (fallbackAvatarUrl == null ? "" : fallbackAvatarUrl.trim());
        preferenceManager.saveNickname(resolvedNickname);
        preferenceManager.saveAvatarUrl(resolvedAvatarUrl);
    }

    private String extractErrorMessage(Response<?> response, BaseResponse<?> body, String fallback) {
        if (body != null) {
            String message = body.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                return message.trim();
            }
        }
        if (response != null) {
            int code = response.code();
            if (code == 401) {
                return "账号或密码错误";
            }
            if (code >= 500) {
                return "服务器开小差了，请稍后重试";
            }
        }
        return fallback;
    }

    private String getSafeErrorMessage(Throwable throwable, String fallback) {
        if (throwable == null) {
            return fallback;
        }
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message;
    }

    private File createTempFileFromUri(Uri uri) throws Exception {
        InputStream inputStream = appContext.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            return null;
        }
        File tempFile = File.createTempFile("avatar_", ".jpg", appContext.getCacheDir());
        try (InputStream in = inputStream; FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
        return tempFile;
    }

    private void safeDeleteFile(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}

