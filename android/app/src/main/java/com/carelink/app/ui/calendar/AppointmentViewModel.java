package com.carelink.app.ui.calendar;

import androidx.lifecycle.LiveData;
import com.carelink.app.base.BaseViewModel;
import com.carelink.app.data.local.entity.AppointmentEntity;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.repository.AppointmentRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.util.List;
import javax.inject.Inject;

@HiltViewModel
public class AppointmentViewModel extends BaseViewModel {

    private final AppointmentRepository appointmentRepository;
    private final PreferenceManager preferenceManager;

    @Inject
    public AppointmentViewModel(AppointmentRepository appointmentRepository,
                                 PreferenceManager preferenceManager) {
        this.appointmentRepository = appointmentRepository;
        this.preferenceManager = preferenceManager;
    }

    public LiveData<List<AppointmentEntity>> getUpcomingAppointments() {
        return appointmentRepository.getUpcomingAppointments(preferenceManager.getElderId());
    }

    public void save(AppointmentEntity entity) {
        entity.elderId = preferenceManager.getElderId();
        appointmentRepository.saveAppointment(entity);
    }

    public void update(AppointmentEntity entity) {
        appointmentRepository.updateAppointment(entity);
    }

    public void delete(long id) {
        appointmentRepository.deleteAppointment(id);
    }
}
