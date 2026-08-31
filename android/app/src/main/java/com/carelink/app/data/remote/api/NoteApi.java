package com.carelink.app.data.remote.api;

import com.carelink.app.data.remote.dto.BaseResponse;
import retrofit2.Call;
import retrofit2.http.*;
import java.util.List;
import java.util.Map;

public interface NoteApi {
    @GET("/api/notes")
    Call<BaseResponse<List<Map<String, Object>>>> getNotes(@Query("elderId") long elderId);

    @POST("/api/notes")
    Call<BaseResponse<Long>> createNote(@Body Map<String, Object> body);

    @DELETE("/api/notes/{id}")
    Call<BaseResponse<Void>> deleteNote(@Path("id") long id);
}
