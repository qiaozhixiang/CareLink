package com.carelink.app.data.repository;

import androidx.lifecycle.LiveData;
import com.carelink.app.data.local.dao.CheckinDao;
import com.carelink.app.data.local.dao.CheckinTaskDao;
import com.carelink.app.data.local.entity.CheckinRecordEntity;
import com.carelink.app.data.local.entity.CheckinTaskEntity;
import com.carelink.app.data.remote.api.CheckinApi;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class CheckinRepository {

    private final CheckinDao checkinDao;
    private final CheckinTaskDao taskDao;
    private final CheckinApi checkinApi;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public CheckinRepository(CheckinDao checkinDao, CheckinTaskDao taskDao, CheckinApi checkinApi) {
        this.checkinDao = checkinDao;
        this.taskDao = taskDao;
        this.checkinApi = checkinApi;
    }

    public LiveData<List<CheckinTaskEntity>> getEnabledTasks(long elderId) {
        return taskDao.getEnabledTasks(elderId);
    }

    public LiveData<List<CheckinRecordEntity>> getTodayRecords(long elderId) {
        return checkinDao.getTodayRecords(elderId);
    }

    public void submitCheckin(long elderId, long taskId, String type, String value, String note,
                               Runnable onSuccess, java.util.function.Consumer<String> onError) {
        executor.execute(() -> {
            CheckinRecordEntity entity = new CheckinRecordEntity();
            entity.elderId = elderId;
            entity.taskId = taskId;
            entity.type = type;
            entity.actualTime = System.currentTimeMillis();
            entity.value = value;
            entity.note = note;
            entity.source = "USER";
            checkinDao.insert(entity);

            // 异步同步服务器
            Map<String, Object> body = new HashMap<>();
            body.put("elderId", elderId);
            body.put("taskId", taskId);
            body.put("type", type);
            body.put("value", value);
            body.put("note", note);
            body.put("actualTime", entity.actualTime);
            checkinApi.submitRecord(body).enqueue(new Callback<com.carelink.app.data.remote.dto.BaseResponse<Long>>() {
                @Override
                public void onResponse(Call<com.carelink.app.data.remote.dto.BaseResponse<Long>> call,
                                       Response<com.carelink.app.data.remote.dto.BaseResponse<Long>> response) {}
                @Override
                public void onFailure(Call<com.carelink.app.data.remote.dto.BaseResponse<Long>> call, Throwable t) {}
            });

            if (onSuccess != null) onSuccess.run();
        });
    }
}
