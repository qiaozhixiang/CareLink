package com.carelink.app.data.local.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import com.carelink.app.data.local.dao.AlertDao;
import com.carelink.app.data.local.dao.AppointmentDao;
import com.carelink.app.data.local.dao.CareNoteDao;
import com.carelink.app.data.local.dao.CheckinDao;
import com.carelink.app.data.local.dao.CheckinTaskDao;
import com.carelink.app.data.local.dao.ShiftAssignmentDao;
import com.carelink.app.data.local.entity.AlertEventEntity;
import com.carelink.app.data.local.entity.AlertRuleEntity;
import com.carelink.app.data.local.entity.AppointmentEntity;
import com.carelink.app.data.local.entity.CareNoteEntity;
import com.carelink.app.data.local.entity.CheckinRecordEntity;
import com.carelink.app.data.local.entity.CheckinTaskEntity;
import com.carelink.app.data.local.entity.ShiftAssignmentEntity;

@Database(
    entities = {
        CheckinTaskEntity.class,
        CheckinRecordEntity.class,
        AlertRuleEntity.class,
        AlertEventEntity.class,
        AppointmentEntity.class,
        CareNoteEntity.class,
        ShiftAssignmentEntity.class
    },
    version = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract CheckinDao checkinDao();
    public abstract CheckinTaskDao checkinTaskDao();
    public abstract AlertDao alertDao();
    public abstract AppointmentDao appointmentDao();
    public abstract CareNoteDao careNoteDao();
    public abstract ShiftAssignmentDao shiftAssignmentDao();
}
