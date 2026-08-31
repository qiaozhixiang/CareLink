package com.carelink.app.data.remote.api;

import com.carelink.app.data.remote.dto.BaseResponse;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface ReminderApi {

    @POST("/api/family/reminders")
    Call<BaseResponse<Map<String, Object>>> sendReminder(@Body Map<String, Object> body);

    @Multipart
    @POST("/api/family/reminders/upload-image")
    Call<BaseResponse<Map<String, String>>> uploadReminderImage(@Part MultipartBody.Part file);

    @GET("/api/family/reminders/unread")
    Call<BaseResponse<List<Map<String, Object>>>> getUnreadReminders();

    @GET("/api/family/reminders/sent")
    Call<BaseResponse<List<Map<String, Object>>>> getSentReminders();

    @POST("/api/family/reminders/{id}/read")
    Call<BaseResponse<Void>> markReminderRead(@Path("id") long id);

    @DELETE("/api/family/reminders/{id}")
    Call<BaseResponse<Void>> deleteReminder(@Path("id") long id);
}
