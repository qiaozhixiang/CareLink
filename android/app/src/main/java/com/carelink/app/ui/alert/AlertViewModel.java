package com.carelink.app.ui.alert;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.carelink.app.base.BaseViewModel;
import com.carelink.app.data.local.entity.AlertEventEntity;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.repository.AlertRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.util.List;
import javax.inject.Inject;

@HiltViewModel
public class AlertViewModel extends BaseViewModel {

    private final AlertRepository alertRepository;
    private final PreferenceManager preferenceManager;
    private final MutableLiveData<AlertUiState> uiState = new MutableLiveData<>();

    @Inject
    public AlertViewModel(AlertRepository alertRepository, PreferenceManager preferenceManager) {
        this.alertRepository = alertRepository;
        this.preferenceManager = preferenceManager;
    }

    public LiveData<AlertUiState> getUiState() { return uiState; }

    public void load() {
        long elderId = preferenceManager.getElderId();
        alertRepository.getPendingAlerts(elderId).observeForever(alerts -> {
            AlertUiState state = new AlertUiState();
            state.alerts = alerts;
            uiState.postValue(state);
        });
    }

    public void handleAlert(long alertId) {
        alertRepository.handleAlert(alertId, "HANDLED");
    }
}
