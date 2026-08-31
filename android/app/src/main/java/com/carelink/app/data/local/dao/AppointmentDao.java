package com.carelink.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.carelink.app.data.local.entity.AppointmentEntity;
import java.util.List;

@Dao
public interface AppointmentDao {

    @Insert
    long insert(AppointmentEntity entity);

    @Update
    void update(AppointmentEntity entity);

    @Query("SELECT * FROM appointments WHERE elderId = :elderId ORDER BY startTime ASC")
    LiveData<List<AppointmentEntity>> getAppointments(long elderId);

    @Query("SELECT * FROM appointments WHERE elderId = :elderId " +
           "AND date(startTime / 1000, 'unixepoch', 'localtime') >= date('now', 'localtime') " +
           "AND status != 2 ORDER BY startTime ASC")
    LiveData<List<AppointmentEntity>> getUpcomingAppointments(long elderId);

    @Query("SELECT * FROM appointments WHERE id = :id LIMIT 1")
    AppointmentEntity getById(long id);

    @Query("DELETE FROM appointments WHERE id = :id")
    void deleteById(long id);
}
