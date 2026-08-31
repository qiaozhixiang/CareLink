package com.carelink.app.di;

import android.content.Context;

import com.carelink.app.data.local.dao.AlertDao;
import com.carelink.app.data.local.dao.AppointmentDao;
import com.carelink.app.data.local.dao.CareNoteDao;
import com.carelink.app.data.local.dao.CheckinDao;
import com.carelink.app.data.local.dao.CheckinTaskDao;
import com.carelink.app.data.local.dao.ShiftAssignmentDao;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.remote.api.AlertApi;
import com.carelink.app.data.remote.api.AppointmentApi;
import com.carelink.app.data.remote.api.AuthApi;
import com.carelink.app.data.remote.api.CheckinApi;
import com.carelink.app.data.remote.api.FamilyApi;
import com.carelink.app.data.remote.api.HealthApi;
import com.carelink.app.data.remote.api.LocationApi;
import com.carelink.app.data.remote.api.NoteApi;
import com.carelink.app.data.remote.api.ShiftApi;
import com.carelink.app.data.repository.AlertRepository;
import com.carelink.app.data.repository.AppointmentRepository;
import com.carelink.app.data.repository.AuthRepository;
import com.carelink.app.data.repository.CareNoteRepository;
import com.carelink.app.data.repository.CheckinRepository;
import com.carelink.app.data.repository.FamilyRepository;
import com.carelink.app.data.repository.HealthRepository;
import com.carelink.app.data.repository.LocationRepository;
import com.carelink.app.data.repository.ShiftRepository;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class RepositoryModule {

    @Provides @Singleton
    public AuthRepository provideAuthRepository(AuthApi authApi,
                                                PreferenceManager preferenceManager,
                                                @ApplicationContext Context appContext) {
        return new AuthRepository(authApi, preferenceManager, appContext);
    }

    @Provides @Singleton
    public CheckinRepository provideCheckinRepository(CheckinDao checkinDao,
                                                       CheckinTaskDao taskDao,
                                                       CheckinApi checkinApi) {
        return new CheckinRepository(checkinDao, taskDao, checkinApi);
    }

    @Provides @Singleton
    public AlertRepository provideAlertRepository(AlertDao alertDao, AlertApi alertApi) {
        return new AlertRepository(alertDao, alertApi);
    }

    @Provides @Singleton
    public AppointmentRepository provideAppointmentRepository(AppointmentDao appointmentDao,
                                                               AppointmentApi appointmentApi) {
        return new AppointmentRepository(appointmentDao, appointmentApi);
    }

    @Provides @Singleton
    public CareNoteRepository provideCareNoteRepository(CareNoteDao careNoteDao, NoteApi noteApi) {
        return new CareNoteRepository(careNoteDao, noteApi);
    }

    @Provides @Singleton
    public ShiftRepository provideShiftRepository(ShiftAssignmentDao shiftDao, ShiftApi shiftApi) {
        return new ShiftRepository(shiftDao, shiftApi);
    }

    @Provides @Singleton
    public FamilyRepository provideFamilyRepository(@ApplicationContext Context appContext,
                                                    FamilyApi familyApi,
                                                    PreferenceManager preferenceManager) {
        return new FamilyRepository(appContext, familyApi, preferenceManager);
    }

    @Provides @Singleton
    public LocationRepository provideLocationRepository(@ApplicationContext Context appContext,
                                                        LocationApi locationApi,
                                                        PreferenceManager preferenceManager) {
        return new LocationRepository(appContext, locationApi, preferenceManager);
    }

    @Provides @Singleton
    public HealthRepository provideHealthRepository(@ApplicationContext Context appContext,
                                                    HealthApi healthApi,
                                                    PreferenceManager preferenceManager) {
        return new HealthRepository(appContext, healthApi, preferenceManager);
    }
}

