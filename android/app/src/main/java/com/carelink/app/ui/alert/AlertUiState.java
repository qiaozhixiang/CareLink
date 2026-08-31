package com.carelink.app.ui.alert;

import com.carelink.app.data.local.entity.AlertEventEntity;
import java.util.List;

public class AlertUiState {
    public boolean loading = false;
    public List<AlertEventEntity> alerts;
    public String error = null;
}
