package com.carelink.app.ui.elder;

import java.util.List;

/** 老人首页 UI 状态 */
public class ElderHomeUiState {
    public boolean loading = false;
    public List<com.carelink.app.domain.model.TodayTaskItem> todayTasks;
    public String greeting = "";
    public String error = null;
}
