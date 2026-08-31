package com.carelink.app.ui.elder;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.carelink.app.base.BaseViewModel;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.domain.usecase.GetTodayTasksUseCase;
import com.carelink.app.domain.usecase.SubmitCheckinUseCase;
import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;

@HiltViewModel
public class ElderHomeViewModel extends BaseViewModel {

    private final GetTodayTasksUseCase getTodayTasksUseCase;
    private final SubmitCheckinUseCase submitCheckinUseCase;
    private final PreferenceManager preferenceManager;

    private final MutableLiveData<ElderHomeUiState> uiState = new MutableLiveData<>();

    @Inject
    public ElderHomeViewModel(GetTodayTasksUseCase getTodayTasksUseCase,
                               SubmitCheckinUseCase submitCheckinUseCase,
                               PreferenceManager preferenceManager) {
        this.getTodayTasksUseCase = getTodayTasksUseCase;
        this.submitCheckinUseCase = submitCheckinUseCase;
        this.preferenceManager = preferenceManager;
    }

    public LiveData<ElderHomeUiState> getUiState() { return uiState; }

    public void load() {
        long elderId = preferenceManager.getElderId();
        ElderHomeUiState state = new ElderHomeUiState();
        state.loading = true;
        uiState.setValue(state);

        getTodayTasksUseCase.execute(elderId, tasks -> {
            ElderHomeUiState newState = new ElderHomeUiState();
            newState.loading = false;
            newState.todayTasks = tasks;
            newState.greeting = buildGreeting();
            uiState.postValue(newState);
        });
    }

    public void submitCheckin(long taskId, String type, String value, String note) {
        long elderId = preferenceManager.getElderId();
        submitCheckinUseCase.execute(elderId, taskId, type, value, note,
                this::load,
                error -> {
                    ElderHomeUiState s = new ElderHomeUiState();
                    s.error = error;
                    uiState.postValue(s);
                });
    }

    private String buildGreeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12) return "早上好！";
        else if (hour < 18) return "下午好！";
        else return "晚上好！";
    }
}
