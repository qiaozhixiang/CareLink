package com.carelink.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.carelink.app.data.local.entity.ShiftAssignmentEntity;
import java.util.List;

@Dao
public interface ShiftAssignmentDao {

    @Insert
    long insert(ShiftAssignmentEntity entity);

    @Update
    void update(ShiftAssignmentEntity entity);

    @Query("SELECT * FROM shift_assignments WHERE elderId = :elderId ORDER BY dutyDate ASC")
    LiveData<List<ShiftAssignmentEntity>> getShifts(long elderId);

    @Query("SELECT * FROM shift_assignments WHERE elderId = :elderId AND dutyDate = :date LIMIT 1")
    ShiftAssignmentEntity getTodayShift(long elderId, String date);

    @Query("SELECT * FROM shift_assignments WHERE familyUserId = :userId AND status = 1")
    LiveData<List<ShiftAssignmentEntity>> getActiveShiftsByUser(long userId);
}
