package com.carelink.app.data.repository;

import androidx.lifecycle.LiveData;
import com.carelink.app.data.local.dao.AppointmentDao;
import com.carelink.app.data.local.entity.AppointmentEntity;
import com.carelink.app.data.remote.api.AppointmentApi;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppointmentRepository {

    private final AppointmentDao appointmentDao;
    private final AppointmentApi appointmentApi;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public AppointmentRepository(AppointmentDao appointmentDao, AppointmentApi appointmentApi) {
        this.appointmentDao = appointmentDao;
        this.appointmentApi = appointmentApi;
    }

    public LiveData<List<AppointmentEntity>> getUpcomingAppointments(long elderId) {
        return appointmentDao.getUpcomingAppointments(elderId);
    }

    public LiveData<List<AppointmentEntity>> getAppointments(long elderId) {
        return appointmentDao.getAppointments(elderId);
    }

    public void saveAppointment(AppointmentEntity entity) {
        executor.execute(() -> appointmentDao.insert(entity));
    }

    public void updateAppointment(AppointmentEntity entity) {
        executor.execute(() -> appointmentDao.update(entity));
    }

    public void deleteAppointment(long id) {
        executor.execute(() -> appointmentDao.deleteById(id));
    }
}
