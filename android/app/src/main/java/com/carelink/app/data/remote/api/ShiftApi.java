package com.carelink.app.data.remote.api;

import com.carelink.app.data.remote.dto.BaseResponse;
import retrofit2.Call;
import retrofit2.http.*;
import java.util.List;
import java.util.Map;

public interface ShiftApi {
    @GET("/api/shifts")
    Call<BaseResponse<List<Map<String, Object>>>> getShifts(@Query("elderId") long elderId);

    @POST("/api/shifts")
    Call<BaseResponse<Long>> createShift(@Body Map<String, Object> body);

    @POST("/api/shifts/{id}/swap")
    Call<BaseResponse<Void>> requestSwap(@Path("id") long shiftId, @Body Map<String, Object> body);
}
