package com.carelink.app.data.remote.api;

import com.carelink.app.data.remote.dto.BaseResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface HealthApi {

    @POST("/api/health/report")
    Call<BaseResponse<Map<String, Object>>> report(@Body Map<String, Object> body);

    @GET("/api/health/family/latest")
    Call<BaseResponse<Map<String, Object>>> fetchFamilyLatest();
}

