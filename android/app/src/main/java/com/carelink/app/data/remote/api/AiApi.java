package com.carelink.app.data.remote.api;

import com.carelink.app.data.remote.dto.AiChatRequest;
import com.carelink.app.data.remote.dto.AiChatResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface AiApi {

    @Headers({
            "Content-Type: application/json",
            "Accept: application/json"
    })
    @POST("api/v3/chat/completions")
    Call<AiChatResponse> chatCompletions(@Header("Authorization") String authorization,
                                         @Body AiChatRequest request);
}
