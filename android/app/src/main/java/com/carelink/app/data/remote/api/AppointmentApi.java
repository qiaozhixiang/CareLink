package com.carelink.app.data.remote.api;

import com.carelink.app.data.remote.dto.BaseResponse;
import retrofit2.Call;
import retrofit2.http.*;
import java.util.List;
import java.util.Map;

public interface AppointmentApi {
    @GET("/api/appointments")
    Call<BaseResponse<List<Map<String, Object>>>> getAppointments(@Query("elderId") long elderId);

    @POST("/api/appointments")
    Call<BaseResponse<Long>> createAppointment(@Body Map<String, Object> body);

    @PUT("/api/appointments/{id}")
    Call<BaseResponse<Void>> updateAppointment(@Path("id") long id, @Body Map<String, Object> body);

    @DELETE("/api/appointments/{id}")
    Call<BaseResponse<Void>> deleteAppointment(@Path("id") long id);
}
