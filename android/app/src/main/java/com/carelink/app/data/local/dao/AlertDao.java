package com.carelink.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.carelink.app.data.local.entity.AlertEventEntity;
import com.carelink.app.data.local.entity.AlertRuleEntity;
import java.util.List;

@Dao
public interface AlertDao {

    @Insert
    long insertEvent(AlertEventEntity entity);

    @Update
    void updateEvent(AlertEventEntity entity);

    @Query("SELECT * FROM alert_event WHERE elderId = :elderId ORDER BY createdAt DESC")
    LiveData<List<AlertEventEntity>> getAlertEvents(long elderId);

    @Query("SELECT * FROM alert_event WHERE elderId = :elderId AND status = 'PENDING' ORDER BY level DESC")
    LiveData<List<AlertEventEntity>> getPendingAlerts(long elderId);

    @Query("UPDATE alert_event SET status = :status WHERE id = :id")
    void updateStatus(long id, String status);

    @Insert
    long insertRule(AlertRuleEntity entity);

    @Query("SELECT * FROM alert_rule WHERE elderId = :elderId AND enabled = 1")
    List<AlertRuleEntity> getEnabledRules(long elderId);
}
