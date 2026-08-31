package com.carelink.app.domain.usecase;

import com.carelink.app.data.local.entity.CheckinRecordEntity;
import com.carelink.app.data.local.entity.CheckinTaskEntity;
import com.carelink.app.data.repository.CheckinRepository;
import com.carelink.app.domain.model.TodayTaskItem;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.inject.Inject;

public class GetTodayTasksUseCase {

    private final CheckinRepository checkinRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public GetTodayTasksUseCase(CheckinRepository checkinRepository) {
        this.checkinRepository = checkinRepository;
    }

    /** 异步获取今日任务列表（任务 + 是否已打卡） */
    public void execute(long elderId, Consumer<List<TodayTaskItem>> callback) {
        executor.execute(() -> {
            // 此处实际应结合 Room LiveData，简化示例用同步方式
            callback.accept(new ArrayList<>());
        });
    }
}
