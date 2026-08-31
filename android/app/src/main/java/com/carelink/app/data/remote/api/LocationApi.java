package com.carelink.app.data.remote.api;

import com.carelink.app.data.remote.dto.BaseResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface LocationApi {

    @POST("/api/location/update")
    Call<BaseResponse<Map<String, Object>>> updateLocation(@Body Map<String, Object> body);

    @POST("/api/location/member/update")
    Call<BaseResponse<Map<String, Object>>> updateMemberLocation(@Body Map<String, Object> body);

    @GET("/api/location/latest")
    Call<BaseResponse<Map<String, Object>>> getLatestLocation(@Query("elderId") long elderId);

    @GET("/api/location/family/latest")
    Call<BaseResponse<List<Map<String, Object>>>> getFamilyLatestLocations();

    @POST("/api/location/toggle")
    Call<BaseResponse<Map<String, Object>>> toggleSharing(@Body Map<String, Object> body);
}
