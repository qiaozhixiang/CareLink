package com.carelink.app.domain.usecase;

import com.carelink.app.data.repository.CheckinRepository;
import javax.inject.Inject;

public class SubmitCheckinUseCase {

    private final CheckinRepository checkinRepository;

    @Inject
    public SubmitCheckinUseCase(CheckinRepository checkinRepository) {
        this.checkinRepository = checkinRepository;
    }

    public void execute(long elderId, long taskId, String type, String value, String note,
                        Runnable onSuccess, java.util.function.Consumer<String> onError) {
        checkinRepository.submitCheckin(elderId, taskId, type, value, note, onSuccess, onError);
    }
}
