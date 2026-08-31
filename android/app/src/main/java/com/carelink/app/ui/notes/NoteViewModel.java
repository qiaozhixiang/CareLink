package com.carelink.app.ui.notes;

import androidx.lifecycle.LiveData;
import com.carelink.app.base.BaseViewModel;
import com.carelink.app.data.local.entity.CareNoteEntity;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.repository.CareNoteRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.util.List;
import javax.inject.Inject;

@HiltViewModel
public class NoteViewModel extends BaseViewModel {

    private final CareNoteRepository careNoteRepository;
    private final PreferenceManager preferenceManager;

    @Inject
    public NoteViewModel(CareNoteRepository careNoteRepository, PreferenceManager preferenceManager) {
        this.careNoteRepository = careNoteRepository;
        this.preferenceManager = preferenceManager;
    }

    public LiveData<List<CareNoteEntity>> getNotes() {
        return careNoteRepository.getNotes(preferenceManager.getElderId());
    }

    public void save(CareNoteEntity entity) {
        entity.elderId = preferenceManager.getElderId();
        entity.authorId = preferenceManager.getUserId();
        careNoteRepository.saveNote(entity);
    }

    public void delete(long id) {
        careNoteRepository.deleteNote(id);
    }
}
