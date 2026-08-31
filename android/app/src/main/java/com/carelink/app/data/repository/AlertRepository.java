package com.carelink.app.data.repository;

import androidx.lifecycle.LiveData;

import com.carelink.app.data.local.dao.AlertDao;
import com.carelink.app.data.local.entity.AlertEventEntity;
import com.carelink.app.data.local.entity.AlertRuleEntity;
import com.carelink.app.data.remote.api.AlertApi;
import com.carelink.app.data.remote.dto.BaseResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class AlertRepository {

    public interface ResultCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    private final AlertDao alertDao;
    private final AlertApi alertApi;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public AlertRepository(AlertDao alertDao, AlertApi alertApi) {
        this.alertDao = alertDao;
        this.alertApi = alertApi;
    }

    public LiveData<List<AlertEventEntity>> getPendingAlerts(long elderId) {
        return alertDao.getPendingAlerts(elderId);
    }

    public LiveData<List<AlertEventEntity>> getAlertEvents(long elderId) {
        return alertDao.getAlertEvents(elderId);
    }

    public List<AlertRuleEntity> getEnabledRules(long elderId) {
        return alertDao.getEnabledRules(elderId);
    }

    public void saveAlert(AlertEventEntity entity) {
        executor.execute(() -> alertDao.insertEvent(entity));
    }

    public void handleAlert(long alertId, String status) {
        executor.execute(() -> alertDao.updateStatus(alertId, status));
        handleAlertRemote(alertId, status, null, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    public void handleAlertRemote(long alertId, String status, String handleNote, ResultCallback<Void> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("status", status);
        if (handleNote != null && !handleNote.trim().isEmpty()) {
            body.put("handleNote", handleNote.trim());
        }
        alertApi.handleAlert(alertId, body).enqueue(new Callback<BaseResponse<Void>>() {
            @Override
            public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {
                BaseResponse<Void> respBody = response.body();
                if (response.isSuccessful() && respBody != null && respBody.isSuccess()) {
                    if (callback != null) {
                        callback.onSuccess(null);
                    }
                    return;
                }
                if (callback != null) {
                    String message = respBody != null && respBody.getMessage() != null
                            && !respBody.getMessage().trim().isEmpty()
                            ? respBody.getMessage().trim()
                            : "处理提醒失败";
                    callback.onError(message);
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                if (callback == null) {
                    return;
                }
                String message = t == null || t.getMessage() == null || t.getMessage().trim().isEmpty()
                        ? "处理提醒失败"
                        : t.getMessage().trim();
                callback.onError(message);
            }
        });
    }

    public void batchHandleAlerts(List<Long> alertIds, String status, String handleNote,
                                  ResultCallback<Integer> callback) {
        if (alertIds == null || alertIds.isEmpty()) {
            if (callback != null) {
                callback.onSuccess(0);
            }
            return;
        }
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger finishedCount = new AtomicInteger(0);
        AtomicBoolean hasFailed = new AtomicBoolean(false);
        final int total = alertIds.size();

        for (Long alertId : alertIds) {
            if (alertId == null || alertId <= 0) {
                if (finishedCount.incrementAndGet() >= total && !hasFailed.get() && callback != null) {
                    callback.onSuccess(successCount.get());
                }
                continue;
            }
            handleAlertRemote(alertId, status, handleNote, new ResultCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    successCount.incrementAndGet();
                    if (finishedCount.incrementAndGet() >= total && !hasFailed.get() && callback != null) {
                        callback.onSuccess(successCount.get());
                    }
                }

                @Override
                public void onError(String message) {
                    finishedCount.incrementAndGet();
                    if (hasFailed.compareAndSet(false, true) && callback != null) {
                        callback.onError(message);
                    }
                }
            });
        }
    }

    public void triggerEmergency(long elderId, double lat, double lng) {
        triggerEmergency(elderId, lat, lng, "", "SOS", 3);
    }

    public void triggerEmergency(long elderId, double lat, double lng,
                                 String description, String alertType, int level) {
        Map<String, Object> body = new HashMap<>();
        body.put("elderId", elderId);
        body.put("lat", lat);
        body.put("lng", lng);
        body.put("timestamp", System.currentTimeMillis());
        if (description != null && !description.trim().isEmpty()) {
            body.put("description", description.trim());
        }
        if (alertType != null && !alertType.trim().isEmpty()) {
            body.put("alertType", alertType.trim().toUpperCase());
        }
        if (level > 0) {
            body.put("level", level);
        }
        alertApi.triggerEmergency(body).enqueue(new Callback<BaseResponse<Void>>() {
            @Override
            public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {
            }

            @Override
            public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
            }
        });
    }

    public void fetchPendingFamilyAlerts(ResultCallback<List<Map<String, Object>>> callback) {
        alertApi.getPendingAlerts().enqueue(new Callback<BaseResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<BaseResponse<List<Map<String, Object>>>> call,
                                   Response<BaseResponse<List<Map<String, Object>>>> response) {
                BaseResponse<List<Map<String, Object>>> body = response.body();
                if (response.isSuccessful() && body != null && body.isSuccess()) {
                    callback.onSuccess(body.getData());
                    return;
                }
                String message = body != null && body.getMessage() != null && !body.getMessage().trim().isEmpty()
                        ? body.getMessage().trim()
                        : "加载协助请求失败";
                callback.onError(message);
            }

            @Override
            public void onFailure(Call<BaseResponse<List<Map<String, Object>>>> call, Throwable t) {
                String message = t == null || t.getMessage() == null || t.getMessage().trim().isEmpty()
                        ? "加载协助请求失败"
                        : t.getMessage().trim();
                callback.onError(message);
            }
        });
    }
}
