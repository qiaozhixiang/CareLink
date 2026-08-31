package com.carelink.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.carelink.app.data.local.entity.CareNoteEntity;
import java.util.List;

@Dao
public interface CareNoteDao {

    @Insert
    long insert(CareNoteEntity entity);

    @Update
    void update(CareNoteEntity entity);

    @Query("SELECT * FROM care_notes WHERE elderId = :elderId ORDER BY createdAt DESC")
    LiveData<List<CareNoteEntity>> getNotes(long elderId);

    @Query("SELECT * FROM care_notes WHERE elderId = :elderId AND isImportant = 1 ORDER BY createdAt DESC")
    LiveData<List<CareNoteEntity>> getImportantNotes(long elderId);

    @Query("DELETE FROM care_notes WHERE id = :id")
    void deleteById(long id);
}
