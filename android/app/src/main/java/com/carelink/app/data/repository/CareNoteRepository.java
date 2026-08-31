package com.carelink.app.data.repository;

import androidx.lifecycle.LiveData;
import com.carelink.app.data.local.dao.CareNoteDao;
import com.carelink.app.data.local.entity.CareNoteEntity;
import com.carelink.app.data.remote.api.NoteApi;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CareNoteRepository {

    private final CareNoteDao careNoteDao;
    private final NoteApi noteApi;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public CareNoteRepository(CareNoteDao careNoteDao, NoteApi noteApi) {
        this.careNoteDao = careNoteDao;
        this.noteApi = noteApi;
    }

    public LiveData<List<CareNoteEntity>> getNotes(long elderId) {
        return careNoteDao.getNotes(elderId);
    }

    public LiveData<List<CareNoteEntity>> getImportantNotes(long elderId) {
        return careNoteDao.getImportantNotes(elderId);
    }

    public void saveNote(CareNoteEntity entity) {
        executor.execute(() -> careNoteDao.insert(entity));
    }

    public void deleteNote(long id) {
        executor.execute(() -> careNoteDao.deleteById(id));
    }
}
