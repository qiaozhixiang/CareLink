package com.carelink.app.di;

import android.util.Log;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.remote.api.AiApi;
import com.carelink.app.data.remote.api.AlertApi;
import com.carelink.app.data.remote.api.AppointmentApi;
import com.carelink.app.data.remote.api.AuthApi;
import com.carelink.app.data.remote.api.CheckinApi;
import com.carelink.app.data.remote.api.FamilyApi;
import com.carelink.app.data.remote.api.HealthApi;
import com.carelink.app.data.remote.api.LocationApi;
import com.carelink.app.data.remote.api.NoteApi;
import com.carelink.app.data.remote.api.ReminderApi;
import com.carelink.app.data.remote.api.ShiftApi;
import com.carelink.app.data.remote.service.AuthInterceptor;
import com.carelink.app.utils.ApiConfig;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import javax.inject.Qualifier;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 网络层配置 - 正式上线版
 *
 * 超时配置：
 *   connectTimeout  : 20秒（连接建立）
 *   readTimeout     : 30秒（服务器响应）
 *   writeTimeout    : 30秒（请求体上传）
 *
 * 重试策略（指数退避）：
 *   仅对可恢复性错误重试：超时 / DNS失败 / 连接拒绝 / 502 / 503 / 504
 *   最多重试 2 次，首次失败等 1s，第二次失败等 2s（退避）
 *   5xx 服务端错误也加入重试（短暂波动时有效）
 */
@Module
@InstallIn(SingletonComponent.class)
public class NetworkModule {

    @Qualifier
    @interface DefaultApiClient {}

    @Qualifier
    @interface AiApiClient {}

    private static final String BASE_URL = ApiConfig.HTTP_BASE_URL;
    private static final String AI_BASE_URL = ApiConfig.AI_BASE_URL;
    private static final String TAG = "NetworkModule";

    // 超时配置常量
    private static final int CONNECT_TIMEOUT_SEC = 20;
    private static final int READ_TIMEOUT_SEC = 45;
    private static final int WRITE_TIMEOUT_SEC = 30;
    // 重试配置常量
    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY_BASE_MS = 1000; // 1s, 2s



    @Provides
    @Singleton
    public HttpLoggingInterceptor provideLoggingInterceptor() {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(
                msg -> Log.d(TAG, msg)
        );
        // 调试环境保留基础请求信息，避免响应体和敏感字段直接写入日志
        interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        return interceptor;
    }

    @Provides
    @Singleton
    public AuthInterceptor provideAuthInterceptor(PreferenceManager preferenceManager) {
        return new AuthInterceptor(preferenceManager);
    }

    /**
     * 智能重试拦截器
     * - 仅对指定可恢复错误重试
     * - 使用指数退避避免雪崩
     * - 打印详细重试日志便于排查
     */
    private Interceptor provideRetryInterceptor() {
        return chain -> {
            Request request = chain.request();
            IOException lastException = null;

            for (int retry = 0; retry <= MAX_RETRY; retry++) {
                try {
                    Response response = chain.proceed(request);
                    int code = response.code();

                    // 5xx 服务端错误也重试（短暂波动）
                    if (code >= 500 && code != 501 && code != 505 && retry < MAX_RETRY) {
                        response.close(); // 关闭旧响应再重试
                        long delay = RETRY_DELAY_BASE_MS * (1L << retry); // 1s, 2s
                        Log.w(TAG, "服务器错误 " + code + "，第 " + (retry + 1) + " 次重试，等待 " + delay + "ms");
                        Thread.sleep(delay);
                        continue;
                    }
                    return response;

                } catch (SocketTimeoutException e) {
                    lastException = e;
                    if (retry < MAX_RETRY) {
                        long delay = RETRY_DELAY_BASE_MS * (1L << retry);
                        Log.w(TAG, "连接超时，第 " + (retry + 1) + " 次重试，等待 " + delay + "ms");
                        safeSleep(delay);
                    }
                } catch (UnknownHostException e) {
                    lastException = e;
                    if (retry < MAX_RETRY) {
                        long delay = RETRY_DELAY_BASE_MS * (1L << retry);
                        Log.w(TAG, "DNS 解析失败，第 " + (retry + 1) + " 次重试，等待 " + delay + "ms");
                        safeSleep(delay);
                    }
                } catch (ConnectException e) {
                    lastException = e;
                    if (retry < MAX_RETRY) {
                        long delay = RETRY_DELAY_BASE_MS * (1L << retry);
                        Log.w(TAG, "连接被拒绝，第 " + (retry + 1) + " 次重试，等待 " + delay + "ms");
                        safeSleep(delay);
                    }
                } catch (InterruptedException e) {
                    // 被中断则立即放弃
                    Thread.currentThread().interrupt();
                    throw new IOException("重试被中断", e);
                } catch (IOException e) {
                    // 其他 IO 异常直接抛出，不再重试
                    throw e;
                }
            }

            String msg = "请求失败，已重试 " + MAX_RETRY + " 次仍无法连接";
            Log.e(TAG, msg, lastException);
            throw lastException != null ? lastException : new IOException(msg);
        };
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Provides
    @Singleton
    @DefaultApiClient
    public OkHttpClient provideOkHttpClient(HttpLoggingInterceptor loggingInterceptor,
                                             AuthInterceptor authInterceptor) {
        return new OkHttpClient.Builder()
                .addInterceptor(authInterceptor)           // 认证拦截器（Token 注入）
                .addInterceptor(provideRetryInterceptor()) // 重试拦截器（最外层）
                .addInterceptor(loggingInterceptor)        // 日志拦截器（调试用）
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)           // 连接失败时自动重试
                .build();
    }

    @Provides
    @Singleton
    @DefaultApiClient
    public Retrofit provideRetrofit(@DefaultApiClient OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    @Provides
    @Singleton
    @AiApiClient
    public OkHttpClient provideAiOkHttpClient(HttpLoggingInterceptor loggingInterceptor) {
        return new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    @Provides
    @Singleton
    @AiApiClient
    public Retrofit provideAiRetrofit(@AiApiClient OkHttpClient aiClient) {
        return new Retrofit.Builder()
                .baseUrl(AI_BASE_URL)
                .client(aiClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }


    @Provides
    @Singleton
    public AiApi provideAiApi(@AiApiClient Retrofit aiRetrofit) {
        return aiRetrofit.create(AiApi.class);
    }

    @Provides @Singleton public AuthApi provideAuthApi(@DefaultApiClient Retrofit retrofit) { return retrofit.create(AuthApi.class); }

    @Provides @Singleton public AlertApi provideAlertApi(@DefaultApiClient Retrofit retrofit) { return retrofit.create(AlertApi.class); }
    @Provides @Singleton public AppointmentApi provideAppointmentApi(@DefaultApiClient Retrofit retrofit) { return retrofit.create(AppointmentApi.class); }
    @Provides @Singleton public CheckinApi provideCheckinApi(@DefaultApiClient Retrofit retrofit) { return retrofit.create(CheckinApi.class); }
    @Provides @Singleton public FamilyApi provideFamilyApi(@DefaultApiClient Retrofit retrofit) { return retrofit.create(FamilyApi.class); }
    @Provides @Singleton public HealthApi provideHealthApi(@DefaultApiClient Retrofit retrofit) { return retrofit.create(HealthApi.class); }
    @Provides @Singleton public LocationApi provideLocationApi(@DefaultApiClient Retrofit retrofit) { return retrofit.create(LocationApi.class); }
    @Provides @Singleton public NoteApi provideNoteApi(@DefaultApiClient Retrofit retrofit) { return retrofit.create(NoteApi.class); }
    @Provides @Singleton public ReminderApi provideReminderApi(@DefaultApiClient Retrofit retrofit) { return retrofit.create(ReminderApi.class); }
    @Provides @Singleton public ShiftApi provideShiftApi(@DefaultApiClient Retrofit retrofit) { return retrofit.create(ShiftApi.class); }
}

