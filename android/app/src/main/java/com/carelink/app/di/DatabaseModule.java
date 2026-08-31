package com.carelink.app.di;

import android.content.Context;
import androidx.room.Room;
import com.carelink.app.data.local.db.AppDatabase;
import com.carelink.app.data.local.dao.AlertDao;
import com.carelink.app.data.local.dao.AppointmentDao;
import com.carelink.app.data.local.dao.CareNoteDao;
import com.carelink.app.data.local.dao.CheckinDao;
import com.carelink.app.data.local.dao.CheckinTaskDao;
import com.carelink.app.data.local.dao.ShiftAssignmentDao;
import com.carelink.app.data.local.pref.PreferenceManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {

    @Provides
    @Singleton
    public AppDatabase provideDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, "carelink_db")
                .fallbackToDestructiveMigration()
                .build();
    }

    @Provides @Singleton
    public CheckinDao provideCheckinDao(AppDatabase db) { return db.checkinDao(); }

    @Provides @Singleton
    public CheckinTaskDao provideCheckinTaskDao(AppDatabase db) { return db.checkinTaskDao(); }

    @Provides @Singleton
    public AlertDao provideAlertDao(AppDatabase db) { return db.alertDao(); }

    @Provides @Singleton
    public AppointmentDao provideAppointmentDao(AppDatabase db) { return db.appointmentDao(); }

    @Provides @Singleton
    public CareNoteDao provideCareNoteDao(AppDatabase db) { return db.careNoteDao(); }

    @Provides @Singleton
    public ShiftAssignmentDao provideShiftAssignmentDao(AppDatabase db) { return db.shiftAssignmentDao(); }

    @Provides
    @Singleton
    public PreferenceManager providePreferenceManager(@ApplicationContext Context context) {
        return new PreferenceManager(context);
    }
}
