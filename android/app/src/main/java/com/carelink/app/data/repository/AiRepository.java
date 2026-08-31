package com.carelink.app.data.repository;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.carelink.app.BuildConfig;
import com.carelink.app.data.remote.api.AiApi;
import com.carelink.app.data.remote.dto.AiChatRequest;
import com.carelink.app.data.remote.dto.AiChatResponse;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class AiRepository {

    private static final String AUTHORIZATION_PREFIX = "Bearer ";
    private static final String DEFAULT_TEXT_MODEL = "ep-20260423154150-vzrbf";
    private static final String DEFAULT_VISION_MODEL = "doubao-1-5-vision-pro-250328";
    private static final String SYSTEM_PROMPT =
            "你是 CareLink 老人关怀助手。回答要温和、直接、易懂，优先给老人清晰可执行的建议；遇到紧急不适要提醒尽快联系家属或医护。";
    private static final int IMAGE_MAX_BYTES = 4 * 1024 * 1024;

    public interface ResultCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    private final AiApi aiApi;
    private final Context appContext;

    @Inject
    public AiRepository(AiApi aiApi, @ApplicationContext Context appContext) {
        this.aiApi = aiApi;
        this.appContext = appContext;
    }

    public void sendMessage(List<String[]> history, String userMessage, @Nullable String imageUri,
                            ResultCallback<String> callback) {
        if (callback == null) {
            return;
        }
        String normalized = userMessage == null ? "" : userMessage.trim();
        if (normalized.isEmpty() && TextUtils.isEmpty(imageUri)) {
            callback.onError("请输入你想说的话，或选择一张图片。");
            return;
        }

        List<AiChatRequest.Message> messages = new ArrayList<>();
        messages.add(new AiChatRequest.Message("system", SYSTEM_PROMPT));
        if (history != null) {
            for (String[] item : history) {
                if (item == null || item.length < 2) {
                    continue;
                }
                String role = item[0];
                String content = item[1];
                if (TextUtils.isEmpty(role) || TextUtils.isEmpty(content)) {
                    continue;
                }
                messages.add(new AiChatRequest.Message(role, content));
            }
        }

        boolean withImage = !TextUtils.isEmpty(imageUri);
        if (withImage) {
            String dataUrl = toDataUrl(imageUri);
            if (TextUtils.isEmpty(dataUrl)) {
                callback.onError("图片读取失败或图片过大，请重新选择后再试。");
                return;
            }
            List<AiChatRequest.ContentItem> contentItems = new ArrayList<>();
            if (!normalized.isEmpty()) {
                contentItems.add(AiChatRequest.ContentItem.text(normalized));
            } else {
                contentItems.add(AiChatRequest.ContentItem.text("请根据图片内容给出建议。"));
            }
            contentItems.add(AiChatRequest.ContentItem.imageDataUrl(dataUrl));
            messages.add(new AiChatRequest.Message("user", contentItems));
        } else {
            messages.add(new AiChatRequest.Message("user", normalized));
        }

        List<String> models = buildModelCandidates(withImage);
        requestWithModel(messages, models, 0, callback);
    }

    private void requestWithModel(List<AiChatRequest.Message> messages, List<String> models, int modelIndex,
                                  ResultCallback<String> callback) {
        if (modelIndex >= models.size()) {
            callback.onError("AI 服务暂不可用：请检查豆包 API Key、模型权限和网络连接。");
            return;
        }
        String model = models.get(modelIndex);
        String apiKey = BuildConfig.ARK_API_KEY == null ? "" : BuildConfig.ARK_API_KEY.trim();
        if (apiKey.isEmpty()) {
            callback.onError("未配置 ARK_API_KEY，请在 local.properties 中添加 ARK_API_KEY 后重装应用。");
            return;
        }
        AiChatRequest request = new AiChatRequest(
                model,
                messages.toArray(new AiChatRequest.Message[0]),
                0.7f,
                1024
        );

        aiApi.chatCompletions(AUTHORIZATION_PREFIX + apiKey, request).enqueue(new Callback<AiChatResponse>() {
            @Override
            public void onResponse(Call<AiChatResponse> call, Response<AiChatResponse> response) {
                if (!response.isSuccessful()) {
                    String errorText = "";
                    try {
                        if (response.errorBody() != null) {
                            errorText = response.errorBody().string();
                        }
                    } catch (Exception ignored) {
                    }

                    boolean maybeModelIssue = response.code() == 400
                            || response.code() == 401
                            || response.code() == 403
                            || response.code() == 404;
                    if (maybeModelIssue && modelIndex + 1 < models.size()) {
                        requestWithModel(messages, models, modelIndex + 1, callback);
                        return;
                    }

                    if (response.code() == 404) {
                        callback.onError("AI 接口返回 404，请确认豆包 endpoint 与模型配置。当前模型：" + model);
                        return;
                    }
                    if (response.code() == 401 || response.code() == 403) {
                        callback.onError("AI 鉴权失败，请检查 API Key 是否有效并具备模型调用权限。");
                        return;
                    }
                    callback.onError("AI 请求失败（HTTP " + response.code() + "）"
                            + (TextUtils.isEmpty(errorText) ? "" : "：" + compact(errorText)));
                    return;
                }

                AiChatResponse body = response.body();
                if (body == null || body.getChoices() == null || body.getChoices().isEmpty()
                        || body.getChoices().get(0) == null || body.getChoices().get(0).getMessage() == null) {
                    callback.onError("AI 返回格式异常，请稍后重试。");
                    return;
                }
                String answer = body.getChoices().get(0).getMessage().extractTextContent();
                if (answer == null || answer.trim().isEmpty()) {
                    callback.onError("AI 返回内容为空，请稍后重试。");
                    return;
                }
                callback.onSuccess(answer.trim());
            }

            @Override
            public void onFailure(Call<AiChatResponse> call, Throwable t) {
                String message = t == null || t.getMessage() == null || t.getMessage().trim().isEmpty()
                        ? "AI 服务连接失败，请检查网络后重试。"
                        : t.getMessage().trim();
                if (message.toLowerCase().contains("expected")) {
                    message = "AI 服务响应解析失败，请重试。";
                }
                callback.onError(message);
            }
        });
    }

    private List<String> buildModelCandidates(boolean withImage) {
        List<String> models = new ArrayList<>();
        addModelIfPresent(models, BuildConfig.ARK_CHAT_MODEL);
        if (withImage) {
            addModelIfPresent(models, BuildConfig.ARK_VISION_MODEL);
            addModelIfPresent(models, DEFAULT_VISION_MODEL);
        }
        addModelIfPresent(models, DEFAULT_TEXT_MODEL);
        return models;
    }

    private void addModelIfPresent(List<String> models, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return;
        }
        if (!models.contains(normalized)) {
            models.add(normalized);
        }
    }

    @Nullable
    private String toDataUrl(String uriText) {
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;
        try {
            Uri uri = Uri.parse(uriText);
            inputStream = appContext.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }
            outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8 * 1024];
            int len;
            int total = 0;
            while ((len = inputStream.read(buffer)) != -1) {
                total += len;
                if (total > IMAGE_MAX_BYTES) {
                    return null;
                }
                outputStream.write(buffer, 0, len);
            }
            String base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
            return "data:image/jpeg;base64," + base64;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private String compact(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.replace('\n', ' ').replace('\r', ' ').trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }
}
