package com.carelink.app.data.remote.api;

import com.carelink.app.data.remote.dto.BaseResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface AlertApi {

    @GET("/api/alert/events")
    Call<BaseResponse<List<Map<String, Object>>>> getAlertEvents(@Query("elderId") long elderId);

    @GET("/api/alert/events/pending")
    Call<BaseResponse<List<Map<String, Object>>>> getPendingAlerts();

    @PUT("/api/alert/events/{id}/handle")
    Call<BaseResponse<Void>> handleAlert(@Path("id") long alertId, @Body Map<String, String> body);

    @POST("/api/emergency/trigger")
    Call<BaseResponse<Void>> triggerEmergency(@Body Map<String, Object> body);
}
