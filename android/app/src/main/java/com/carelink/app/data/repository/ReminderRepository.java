package com.carelink.app.data.repository;

import android.content.Context;
import android.net.Uri;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.remote.api.ReminderApi;
import com.carelink.app.data.remote.dto.BaseResponse;
import com.carelink.app.utils.NetworkErrorHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
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
public class ReminderRepository {

    public interface ResultCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    private final Context appContext;
    private final ReminderApi reminderApi;

    @Inject
    public ReminderRepository(@ApplicationContext Context appContext, ReminderApi reminderApi) {
        this.appContext = appContext;
        this.reminderApi = reminderApi;
    }

    public void sendReminder(Map<String, Object> body, ResultCallback<Map<String, Object>> callback) {
        reminderApi.sendReminder(body).enqueue(new RepositoryCallback<>(appContext, callback));
    }

    public void uploadReminderImage(Uri uri, ResultCallback<String> callback) {
        if (uri == null) {
            callback.onError("未选择图片");
            return;
        }
        File tempFile = null;
        try {
            tempFile = createTempFileFromUri(uri);
            if (tempFile == null || !tempFile.exists()) {
                callback.onError("图片读取失败，请重新选择");
                return;
            }

            String mimeType = appContext.getContentResolver().getType(uri);
            if (mimeType == null || mimeType.trim().isEmpty()) {
                mimeType = "image/jpeg";
            }

            RequestBody fileBody = RequestBody.create(tempFile, MediaType.parse(mimeType));
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", tempFile.getName(), fileBody);

            File finalTempFile = tempFile;
            reminderApi.uploadReminderImage(filePart).enqueue(new Callback<BaseResponse<Map<String, String>>>() {
                @Override
                public void onResponse(Call<BaseResponse<Map<String, String>>> call,
                                       Response<BaseResponse<Map<String, String>>> response) {
                    safeDeleteFile(finalTempFile);
                    if (!response.isSuccessful() || response.body() == null) {
                        NetworkErrorHandler.NetworkError error = NetworkErrorHandler.handleResponse(response);
                        callback.onError(error != null ? error.userMessage : "上传图片失败，请稍后重试");
                        return;
                    }
                    BaseResponse<Map<String, String>> body = response.body();
                    if (!body.isSuccess() || body.getData() == null) {
                        callback.onError(body.getMessage() == null || body.getMessage().trim().isEmpty()
                                ? "上传图片失败，请稍后重试"
                                : body.getMessage());
                        return;
                    }
                    String imageUrl = body.getData().get("imageUrl");
                    if (imageUrl == null || imageUrl.trim().isEmpty()) {
                        callback.onError("上传图片失败，服务端未返回图片地址");
                        return;
                    }
                    callback.onSuccess(imageUrl.trim());
                }

                @Override
                public void onFailure(Call<BaseResponse<Map<String, String>>> call, Throwable t) {
                    safeDeleteFile(finalTempFile);
                    NetworkErrorHandler.NetworkError error = NetworkErrorHandler.handleFailure(appContext, t);
                    callback.onError(error.userMessage);
                }
            });
        } catch (Exception e) {
            safeDeleteFile(tempFile);
            callback.onError("上传图片失败，请稍后重试");
        }
    }

    public void getUnreadReminders(ResultCallback<List<Map<String, Object>>> callback) {
        reminderApi.getUnreadReminders().enqueue(new RepositoryCallback<>(appContext, callback));
    }

    public void getSentReminders(ResultCallback<List<Map<String, Object>>> callback) {
        reminderApi.getSentReminders().enqueue(new RepositoryCallback<>(appContext, callback));
    }

    public void markReminderRead(long id, ResultCallback<Void> callback) {
        reminderApi.markReminderRead(id).enqueue(new RepositoryCallback<>(appContext, callback));
    }

    public void deleteReminder(long id, ResultCallback<Void> callback) {
        reminderApi.deleteReminder(id).enqueue(new RepositoryCallback<>(appContext, callback));
    }

    private File createTempFileFromUri(Uri uri) throws Exception {
        InputStream inputStream = appContext.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            return null;
        }
        File tempFile = File.createTempFile("reminder_", ".jpg", appContext.getCacheDir());
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

    private static class RepositoryCallback<T> implements Callback<BaseResponse<T>> {
        private final Context context;
        private final ResultCallback<T> callback;

        RepositoryCallback(Context context, ResultCallback<T> callback) {
            this.context = context;
            this.callback = callback;
        }

        @Override
        public void onResponse(Call<BaseResponse<T>> call, Response<BaseResponse<T>> response) {
            if (!response.isSuccessful() || response.body() == null) {
                NetworkErrorHandler.NetworkError error = NetworkErrorHandler.handleResponse(response);
                if (error != null && error.shouldForceLogin) {
                    new PreferenceManager(context).redirectToLogin(context, error.userMessage);
                    callback.onError(error.userMessage);
                    return;
                }
                callback.onError(error != null ? error.userMessage : "请求失败，请稍后重试");
                return;
            }

            BaseResponse<T> body = response.body();
            if (body.isSuccess()) {
                callback.onSuccess(body.getData());
            } else {
                NetworkErrorHandler.NetworkError error =
                        NetworkErrorHandler.handleBusinessError(body.getMessage(), response.code());
                if (error.shouldForceLogin) {
                    new PreferenceManager(context).redirectToLogin(context, error.userMessage);
                    callback.onError(error.userMessage);
                    return;
                }
                callback.onError(error.userMessage);
            }
        }

        @Override
        public void onFailure(Call<BaseResponse<T>> call, Throwable t) {
            NetworkErrorHandler.NetworkError error = NetworkErrorHandler.handleFailure(context, t);
            callback.onError(error.userMessage);
        }
    }
}
