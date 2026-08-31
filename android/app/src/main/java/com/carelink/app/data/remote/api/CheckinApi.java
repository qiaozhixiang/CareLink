package com.carelink.app.data.remote.api;

import com.carelink.app.data.remote.dto.BaseResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import java.util.List;
import java.util.Map;

public interface CheckinApi {

    @GET("/api/checkin/tasks")
    Call<BaseResponse<List<Map<String, Object>>>> getTasks(@Query("elderId") long elderId);

    @POST("/api/checkin/record")
    Call<BaseResponse<Long>> submitRecord(@Body Map<String, Object> body);

    @GET("/api/checkin/records/today")
    Call<BaseResponse<List<Map<String, Object>>>> getTodayRecords(@Query("elderId") long elderId);
}
