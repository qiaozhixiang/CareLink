package com.carelink.app.data.repository;

import android.content.Context;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.remote.api.HealthApi;
import com.carelink.app.data.remote.dto.BaseResponse;
import com.carelink.app.utils.NetworkErrorHandler;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class HealthRepository {

    public interface ResultCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    private final Context appContext;
    private final HealthApi healthApi;
    private final PreferenceManager preferenceManager;

    @Inject
    public HealthRepository(@ApplicationContext Context appContext,
                            HealthApi healthApi,
                            PreferenceManager preferenceManager) {
        this.appContext = appContext;
        this.healthApi = healthApi;
        this.preferenceManager = preferenceManager;
    }

    public void reportHealthSnapshot(int heartRate,
                                     int bloodOxygen,
                                     int systolic,
                                     int diastolic,
                                     int steps,
                                     String source,
                                     boolean fallDetected,
                                     ResultCallback<Map<String, Object>> callback) {
        Map<String, Object> body = new HashMap<>();
        if (heartRate > 0) {
            body.put("heartRate", heartRate);
        }
        if (bloodOxygen > 0) {
            body.put("bloodOxygen", bloodOxygen);
        }
        if (systolic > 0) {
            body.put("systolic", systolic);
        }
        if (diastolic > 0) {
            body.put("diastolic", diastolic);
        }
        if (steps > 0) {
            body.put("steps", steps);
        }
        if (source != null && !source.trim().isEmpty()) {
            body.put("source", source.trim());
        }
        body.put("fallDetected", fallDetected);
        body.put("reportedAt", System.currentTimeMillis());

        reportHealthSnapshot(body, callback);
    }

    public void reportHealthSnapshot(Map<String, Object> body,
                                     ResultCallback<Map<String, Object>> callback) {
        Map<String, Object> payload = body == null ? new HashMap<>() : new HashMap<>(body);
        payload.put("reportedAt", payload.getOrDefault("reportedAt", System.currentTimeMillis()));

        healthApi.report(payload).enqueue(new RepositoryCallback<Map<String, Object>>(appContext, callback) {
            @Override
            protected void onSuccessData(Map<String, Object> data) {
                persistHealthToLocal(payload, data);
                super.onSuccessData(data);
            }
        });
    }

    public void fetchFamilyLatestHealth(ResultCallback<Map<String, Object>> callback) {
        healthApi.fetchFamilyLatest().enqueue(new RepositoryCallback<Map<String, Object>>(appContext, callback));
    }

    private void persistHealthToLocal(Map<String, Object> payload, Map<String, Object> responseData) {
        int heartRate = toInt(payload.get("heartRate"), preferenceManager.getHealthHeartRate());
        int bloodOxygen = toInt(payload.get("bloodOxygen"), preferenceManager.getHealthBloodOxygen());
        int systolic = toInt(payload.get("systolic"), preferenceManager.getHealthSystolic());
        int diastolic = toInt(payload.get("diastolic"), preferenceManager.getHealthDiastolic());
        int steps = toInt(payload.get("steps"), preferenceManager.getHealthSteps());

        preferenceManager.saveHealthHeartRate(Math.max(0, heartRate));
        preferenceManager.saveHealthBloodOxygen(Math.max(0, bloodOxygen));
        preferenceManager.saveHealthSystolic(Math.max(0, systolic));
        preferenceManager.saveHealthDiastolic(Math.max(0, diastolic));
        preferenceManager.saveHealthSteps(Math.max(0, steps));

        Object reportedAt = responseData == null ? payload.get("reportedAt") : responseData.get("reportedAt");
        if (reportedAt == null) {
            reportedAt = System.currentTimeMillis();
        }
        preferenceManager.saveHealthUpdatedAt(String.valueOf(reportedAt));
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
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
                onSuccessData(body.getData());
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

        protected void onSuccessData(T data) {
            callback.onSuccess(data);
        }
    }
}

