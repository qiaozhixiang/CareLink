package com.carelink.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.carelink.app.data.local.entity.CheckinTaskEntity;
import java.util.List;

@Dao
public interface CheckinTaskDao {

    @Insert
    long insert(CheckinTaskEntity entity);

    @Update
    void update(CheckinTaskEntity entity);

    @Query("SELECT * FROM checkin_task WHERE elderId = :elderId AND enabled = 1")
    LiveData<List<CheckinTaskEntity>> getEnabledTasks(long elderId);

    @Query("SELECT * FROM checkin_task WHERE elderId = :elderId")
    List<CheckinTaskEntity> getAllTasksSync(long elderId);

    @Query("SELECT * FROM checkin_task WHERE id = :id LIMIT 1")
    CheckinTaskEntity getById(long id);
}
