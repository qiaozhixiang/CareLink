package com.carelink.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.carelink.app.data.local.entity.CheckinRecordEntity;
import java.util.List;

@Dao
public interface CheckinDao {

    @Insert
    long insert(CheckinRecordEntity entity);

    @Update
    void update(CheckinRecordEntity entity);

    @Query("SELECT * FROM checkin_record WHERE elderId = :elderId ORDER BY actualTime DESC")
    LiveData<List<CheckinRecordEntity>> getRecords(long elderId);

    @Query("SELECT * FROM checkin_record WHERE actualTime BETWEEN :startTime AND :endTime ORDER BY actualTime DESC")
    List<CheckinRecordEntity> getRecordsBetween(long startTime, long endTime);

    @Query("SELECT * FROM checkin_record WHERE elderId = :elderId " +
           "AND date(actualTime / 1000, 'unixepoch', 'localtime') = date('now', 'localtime')")
    LiveData<List<CheckinRecordEntity>> getTodayRecords(long elderId);

    @Query("SELECT * FROM checkin_record WHERE elderId = :elderId AND taskId = :taskId " +
           "AND date(actualTime / 1000, 'unixepoch', 'localtime') = date('now', 'localtime') LIMIT 1")
    CheckinRecordEntity getTodayRecordByTask(long elderId, long taskId);

    @Query("DELETE FROM checkin_record WHERE elderId = :elderId")
    void deleteAll(long elderId);
}
