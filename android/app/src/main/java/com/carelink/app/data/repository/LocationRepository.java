package com.carelink.app.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.remote.api.LocationApi;
import com.carelink.app.data.remote.dto.BaseResponse;
import com.carelink.app.utils.NetworkErrorHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class LocationRepository {

    public interface ResultCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    private final Context appContext;
    private final LocationApi locationApi;
    private final PreferenceManager preferenceManager;

    @Inject
    public LocationRepository(@ApplicationContext Context appContext,
                              LocationApi locationApi,
                              PreferenceManager preferenceManager) {
        this.appContext = appContext;
        this.locationApi = locationApi;
        this.preferenceManager = preferenceManager;
    }

    public void uploadLocation(double latitude, double longitude, String address,
                               boolean enabled, Long expireAtMs,
                               ResultCallback<Map<String, Object>> callback) {
        // 统一改走“成员位置上报”，支持老人端/家属端双向共享。
        uploadMemberLocation(latitude, longitude, address, enabled, expireAtMs, callback);
    }

    public void uploadMemberLocation(double latitude, double longitude, String address,
                                     boolean enabled, @Nullable Long expireAtMs,
                                     ResultCallback<Map<String, Object>> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("latitude", latitude);
        body.put("longitude", longitude);
        body.put("address", address == null ? "" : address);
        body.put("enabled", enabled);
        if (expireAtMs != null && expireAtMs > 0L) {
            body.put("expireAt", expireAtMs);
        }

        locationApi.updateMemberLocation(body).enqueue(new RepositoryCallback<Map<String, Object>>(appContext, callback) {
            @Override
            protected void onSuccessData(Map<String, Object> data) {
                persistLocationLocally(latitude, longitude, address, enabled, data, expireAtMs);
                super.onSuccessData(data);
            }
        });
    }

    public void fetchLatestLocation(ResultCallback<Map<String, Object>> callback) {
        long elderId = resolveElderIdForRead();
        if (elderId <= 0) {
            callback.onError("当前家庭还没有绑定老人，暂时无法查看共享位置");
            return;
        }
        fetchLatestLocationByElderId(elderId, callback);
    }

    public void fetchLatestLocationByElderId(long elderId, ResultCallback<Map<String, Object>> callback) {
        if (elderId <= 0) {
            callback.onError("老人身份无效，无法查看共享位置");
            return;
        }
        locationApi.getLatestLocation(elderId).enqueue(new RepositoryCallback<Map<String, Object>>(appContext, callback) {
            @Override
            protected void onSuccessData(Map<String, Object> data) {
                Object returnedElderId = data == null ? null : data.get("elderId");
                long snapshotElderId = returnedElderId instanceof Number
                        ? ((Number) returnedElderId).longValue() : elderId;
                persistRemoteSnapshot(data, snapshotElderId, snapshotElderId == resolveElderIdForRead());
                super.onSuccessData(data);
            }
        });
    }

    public void fetchFamilyLatestLocations(ResultCallback<List<Map<String, Object>>> callback) {
        locationApi.getFamilyLatestLocations().enqueue(new RepositoryCallback<List<Map<String, Object>>>(appContext, callback) {
            @Override
            protected void onSuccessData(List<Map<String, Object>> data) {
                syncPrimaryFamilyLocationCache(data);
                super.onSuccessData(data);
            }
        });
    }

    public void toggleSharing(boolean enabled, ResultCallback<Map<String, Object>> callback) {
        long elderId = resolveElderIdForUpload();
        if (elderId <= 0) {
            callback.onError("未获取到老人身份，暂时无法切换共享状态");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("elderId", elderId);
        body.put("enabled", enabled);
        locationApi.toggleSharing(body).enqueue(new RepositoryCallback<Map<String, Object>>(appContext, callback) {
            @Override
            protected void onSuccessData(Map<String, Object> data) {
                preferenceManager.saveShareStatus(enabled ? "临时共享中" : "已结束");
                if (!enabled) {
                    preferenceManager.saveShareEndTime("已手动结束");
                }
                super.onSuccessData(data);
            }
        });
    }

    private long extractLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return -1L;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private void syncPrimaryFamilyLocationCache(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        long currentUserId = preferenceManager.getUserId();
        Map<String, Object> primary = null;

        // 优先使用当前登录用户自己的共享快照。
        if (currentUserId > 0) {
            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }
                long userId = extractLong(item.get("userId"));
                if (userId == currentUserId) {
                    primary = item;
                    break;
                }
            }
        }

        // 如果没有自己的快照，使用第一条有位置的数据。
        if (primary == null) {
            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }
                Object hasLocation = item.get("hasLocation");
                if (!(hasLocation instanceof Boolean) || (Boolean) hasLocation) {
                    primary = item;
                    break;
                }
            }
        }

        if (primary == null) {
            return;
        }

        long userId = extractLong(primary.get("userId"));
        if (userId > 0) {
            preferenceManager.saveElderId(userId);
        }
        persistRemoteSnapshot(primary, userId, true);
    }

    private void persistLocationLocally(double latitude, double longitude, String address,
                                        boolean enabled, Map<String, Object> data, Long expireAtMs) {
        preferenceManager.saveShareLatitude(latitude);
        preferenceManager.saveShareLongitude(longitude);
        preferenceManager.saveShareLastLocation(address == null || address.trim().isEmpty() ? "位置已更新" : address.trim());
        preferenceManager.saveShareStatus(enabled ? "临时共享中" : "已结束");

        Object updatedAt = data == null ? null : data.get("updatedAt");
        preferenceManager.saveShareLastTime(updatedAt == null ? "刚刚更新" : updatedAt.toString());
        preferenceManager.saveSharedLocationOwner(safeRole(preferenceManager.getRole()));
        preferenceManager.setSharedLocationVisibleToBoth(enabled);

        if (expireAtMs != null && expireAtMs > 0L) {
            long minutes = expireAtMs / 60000L;
            if (minutes > 0) {
                preferenceManager.saveShareEndTime("预计 " + minutes + " 分钟后结束");
            }
        }
    }

    private void persistRemoteSnapshot(Map<String, Object> data, long userId, boolean syncToPrimaryCache) {
        if (data == null || !syncToPrimaryCache) {
            return;
        }

        Object lat = data.get("latitude");
        Object lng = data.get("longitude");
        Object address = data.get("address");
        Object enabled = data.get("enabled");
        Object updatedAt = data.get("updatedAt");
        Object expireAt = data.get("expireAt");

        if (lat instanceof Number) {
            preferenceManager.saveShareLatitude(((Number) lat).doubleValue());
        }
        if (lng instanceof Number) {
            preferenceManager.saveShareLongitude(((Number) lng).doubleValue());
        }
        if (address != null) {
            preferenceManager.saveShareLastLocation(address.toString());
        }
        preferenceManager.saveShareLastTime(updatedAt == null ? "暂无" : updatedAt.toString());
        preferenceManager.saveShareEndTime(expireAt == null ? "未设置" : expireAt.toString());

        boolean enabledValue = !(enabled instanceof Boolean) || (Boolean) enabled;
        preferenceManager.saveShareStatus(enabledValue ? "临时共享中" : "已结束");
        preferenceManager.saveSharedLocationOwner("CLOUD_SYNC:" + (userId > 0 ? userId : "UNKNOWN"));
        preferenceManager.setSharedLocationVisibleToBoth(enabledValue);
    }

    private String safeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "MEMBER";
        }
        return role.trim().toUpperCase();
    }

    private long resolveElderIdForUpload() {
        long userId = preferenceManager.getUserId();
        if (userId > 0) {
            return userId;
        }
        long elderId = preferenceManager.getElderId();
        return elderId > 0 ? elderId : -1;
    }

    private long resolveElderIdForRead() {
        long elderId = preferenceManager.getElderId();
        if (elderId > 0) {
            return elderId;
        }
        long userId = preferenceManager.getUserId();
        return userId > 0 ? userId : -1;
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
