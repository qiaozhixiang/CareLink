package com.carelink.app.data.repository;

import androidx.lifecycle.LiveData;
import com.carelink.app.data.local.dao.ShiftAssignmentDao;
import com.carelink.app.data.local.entity.ShiftAssignmentEntity;
import com.carelink.app.data.remote.api.ShiftApi;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ShiftRepository {

    private final ShiftAssignmentDao shiftDao;
    private final ShiftApi shiftApi;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public ShiftRepository(ShiftAssignmentDao shiftDao, ShiftApi shiftApi) {
        this.shiftDao = shiftDao;
        this.shiftApi = shiftApi;
    }

    public LiveData<List<ShiftAssignmentEntity>> getShifts(long elderId) {
        return shiftDao.getShifts(elderId);
    }

    public void saveShift(ShiftAssignmentEntity entity) {
        executor.execute(() -> shiftDao.insert(entity));
    }

    public void updateShift(ShiftAssignmentEntity entity) {
        executor.execute(() -> shiftDao.update(entity));
    }
}
