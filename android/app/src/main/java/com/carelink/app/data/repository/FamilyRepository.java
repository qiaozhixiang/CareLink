package com.carelink.app.data.repository;

import android.content.Context;
import android.util.Log;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.remote.api.FamilyApi;
import com.carelink.app.data.remote.dto.BaseResponse;
import com.carelink.app.utils.NetworkErrorHandler;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class FamilyRepository {

    public interface ResultCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    private final Context appContext;
    private final FamilyApi familyApi;
    private final PreferenceManager preferenceManager;

    @Inject
    public FamilyRepository(@ApplicationContext Context appContext,
                            FamilyApi familyApi,
                            PreferenceManager preferenceManager) {
        this.appContext = appContext;
        this.familyApi = familyApi;
        this.preferenceManager = preferenceManager;
    }

    public void getMembers(ResultCallback<List<Map<String, Object>>> callback) {
        familyApi.getMembers().enqueue(new RepositoryCallback<>(appContext, callback));
    }

    public void getElders(ResultCallback<List<Map<String, Object>>> callback) {
        familyApi.getElders().enqueue(new RepositoryCallback<List<Map<String, Object>>>(appContext, callback) {
            @Override
            protected void onSuccessData(List<Map<String, Object>> data) {
                syncPrimaryElder(data);
                super.onSuccessData(data);
            }
        });
    }

    public void getFamilyInfo(Long familyId, ResultCallback<Map<String, Object>> callback) {
        familyApi.getFamilyInfo(familyId).enqueue(new RepositoryCallback<Map<String, Object>>(appContext, callback) {
            @Override
            protected void onSuccessData(Map<String, Object> data) {
                persistFamilyState(data, null, false);
                super.onSuccessData(data);
            }
        });
    }

    public void validateInviteCode(String code, ResultCallback<Map<String, Object>> callback) {
        familyApi.validateInviteCode(code).enqueue(new RepositoryCallback<>(appContext, callback));
    }

    public void createFamily(String name, ResultCallback<Map<String, Object>> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        familyApi.createFamily(body).enqueue(new RepositoryCallback<Map<String, Object>>(appContext, callback) {
            @Override
            protected void onSuccessData(Map<String, Object> data) {
                persistFamilyState(data, name, true);
                super.onSuccessData(data);
            }
        });
    }

    public void joinFamily(String code, ResultCallback<Map<String, Object>> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("inviteCode", code);
        familyApi.joinFamily(body).enqueue(new RepositoryCallback<Map<String, Object>>(appContext, callback) {
            @Override
            protected void onSuccessData(Map<String, Object> data) {
                persistFamilyState(data, null, false);
                super.onSuccessData(data);
            }
        });
    }

    public void leaveFamily(ResultCallback<Map<String, Object>> callback) {
        familyApi.leaveFamily().enqueue(new RepositoryCallback<Map<String, Object>>(appContext, callback) {
            @Override
            protected void onSuccessData(Map<String, Object> data) {
                clearFamilyState();
                super.onSuccessData(data);
            }
        });
    }

    public void removeMember(long userId, ResultCallback<Void> callback) {
        familyApi.removeMember(userId).enqueue(new RepositoryCallback<Void>(appContext, callback) {
            @Override
            protected void onSuccessData(Void data) {
                super.onSuccessData(data);
            }
        });
    }

    public void transferCreator(long targetUserId, ResultCallback<Map<String, Object>> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("targetUserId", targetUserId);
        familyApi.transferCreator(body).enqueue(new RepositoryCallback<Map<String, Object>>(appContext, callback) {
            @Override
            protected void onSuccessData(Map<String, Object> data) {
                persistFamilyState(data, null, false);
                super.onSuccessData(data);
            }
        });
    }

    public void dissolveFamily(ResultCallback<Map<String, Object>> callback) {
        familyApi.dissolveFamilyRaw().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    NetworkErrorHandler.NetworkError error = NetworkErrorHandler.handleResponse(response);
                    if (error != null && error.shouldForceLogin) {
                        new PreferenceManager(appContext).redirectToLogin(appContext, error.userMessage);
                        callback.onError(error.userMessage);
                        return;
                    }
                    callback.onError(error != null ? error.userMessage : "解散家庭失败，请稍后重试");
                    return;
                }

                String rawBody = readResponseBody(response.body());
                if (rawBody == null || rawBody.trim().isEmpty()) {
                    clearFamilyState();
                    callback.onSuccess(new HashMap<>());
                    return;
                }

                try {
                    JSONObject json = new JSONObject(rawBody);
                    boolean success = json.optBoolean("success", false);
                    String message = json.optString("message", "");
                    if (!success) {
                        NetworkErrorHandler.NetworkError error =
                                NetworkErrorHandler.handleBusinessError(message, response.code());
                        if (error.shouldForceLogin) {
                            new PreferenceManager(appContext).redirectToLogin(appContext, error.userMessage);
                            callback.onError(error.userMessage);
                            return;
                        }
                        callback.onError(error.userMessage);
                        return;
                    }

                    Map<String, Object> data = jsonObjectToMap(json.optJSONObject("data"));
                    clearFamilyState();
                    callback.onSuccess(data);
                } catch (Exception parseEx) {
                    Log.w("FamilyRepository", "解析解散家庭响应失败，按成功兜底处理: " + parseEx.getMessage());
                    clearFamilyState();
                    callback.onSuccess(new HashMap<>());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                NetworkErrorHandler.NetworkError error = NetworkErrorHandler.handleFailure(appContext, t);
                callback.onError(error.userMessage);
            }
        });
    }




    private String readResponseBody(ResponseBody body) {
        if (body == null) {
            return null;
        }
        try {
            return body.string();
        } catch (Exception e) {
            Log.w("FamilyRepository", "读取响应体失败: " + e.getMessage());
            return null;
        }
    }

    private Map<String, Object> jsonObjectToMap(JSONObject object) {
        Map<String, Object> map = new HashMap<>();
        if (object == null) {
            return map;
        }
        try {
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = object.opt(key);
                map.put(key, value);
            }
        } catch (Exception e) {
            Log.w("FamilyRepository", "JSON 转 Map 失败: " + e.getMessage());
        }
        return map;
    }

    private void syncPrimaryElder(List<Map<String, Object>> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        for (Map<String, Object> member : members) {
            if (member == null) {
                continue;
            }
            Object role = member.get("role");
            if (role == null || !"ELDER".equalsIgnoreCase(role.toString())) {
                continue;
            }
            long elderId = extractLong(member.get("userId"));
            if (elderId > 0) {
                preferenceManager.saveElderId(elderId);
                break;
            }
        }
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

    private void persistFamilyState(Map<String, Object> data, String fallbackFamilyName, boolean saveInviteCode) {
        if (data == null) {
            return;
        }
        long familyId = extractLong(extractNestedValue(data, "familyId", "family_id", "id"));
        if (familyId > 0) {
            preferenceManager.saveFamilyId(familyId);
        }

        String familyName = extractNonEmptyText(extractNestedValue(data, "familyName", "family_name", "name"));
        if (!familyName.isEmpty()) {
            preferenceManager.saveFamilyName(familyName);
        } else if (fallbackFamilyName != null && !fallbackFamilyName.trim().isEmpty()) {
            preferenceManager.saveFamilyName(fallbackFamilyName.trim());
        }

        String inviteCode = extractNonEmptyText(extractNestedValue(data, "inviteCode", "invite_code", "code"));
        if (saveInviteCode) {
            if (!inviteCode.isEmpty()) {
                preferenceManager.saveInviteCode(inviteCode);
            }
        } else if (!inviteCode.isEmpty()) {
            preferenceManager.saveInviteCode(inviteCode);
        }
    }

    private Object extractNestedValue(Map<String, Object> source, String... keys) {
        Object direct = extractDirectValue(source, keys);
        if (direct != null) {
            return direct;
        }

        Map<String, Object> familyMap = asMap(source.get("family"));
        direct = extractDirectValue(familyMap, keys);
        if (direct != null) {
            return direct;
        }

        Map<String, Object> infoMap = asMap(source.get("familyInfo"));
        direct = extractDirectValue(infoMap, keys);
        if (direct != null) {
            return direct;
        }

        Map<String, Object> nestedData = asMap(source.get("data"));
        return extractDirectValue(nestedData, keys);
    }

    private Object extractDirectValue(Map<String, Object> source, String... keys) {
        if (source == null || keys == null || keys.length == 0) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private String extractNonEmptyText(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().trim();
        return text.isEmpty() ? "" : text;
    }

    private void clearFamilyState() {
        preferenceManager.saveFamilyId((Long) null);
        preferenceManager.saveFamilyName("");
        preferenceManager.saveInviteCode("");
        preferenceManager.saveElderId(-1L);
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
