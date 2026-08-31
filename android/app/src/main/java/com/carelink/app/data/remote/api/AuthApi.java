package com.carelink.app.data.remote.api;

import com.carelink.app.data.remote.dto.BaseResponse;
import com.carelink.app.data.remote.dto.EmailCodeRequest;
import com.carelink.app.data.remote.dto.LoginRequest;
import com.carelink.app.data.remote.dto.LoginResponse;
import com.carelink.app.data.remote.dto.ProfileUpdateRequest;
import com.carelink.app.data.remote.dto.RegisterRequest;
import com.carelink.app.data.remote.dto.ResetPasswordRequest;
import com.carelink.app.data.remote.dto.SendCodeRequest;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.PUT;
import retrofit2.http.Query;
import okhttp3.MultipartBody;

public interface AuthApi {

    /** 邮箱+密码登录 */
    @POST("/api/auth/login")
    Call<BaseResponse<LoginResponse>> login(@Body LoginRequest request);

    /** 邮箱注册 */
    @POST("/api/auth/register")
    Call<BaseResponse<LoginResponse>> register(@Body RegisterRequest request);

    /** 兼容旧版后端注册路径 */
    @POST("/auth/register")
    Call<BaseResponse<LoginResponse>> registerCompat(@Body RegisterRequest request);

    /** 发送邮箱验证码 */
    @POST("/api/auth/sendCode")
    Call<BaseResponse<Void>> sendEmailCode(@Body EmailCodeRequest request);

    /** 旧发送验证码接口（保留兼容） */
    @POST("/api/auth/sendCode")
    Call<BaseResponse<Void>> sendCode(@Body SendCodeRequest request);

    /** 发送忘记密码验证码 */
    @POST("/api/auth/sendResetCode")
    Call<BaseResponse<Void>> sendResetCode(@Body EmailCodeRequest request);

    /** 提交新密码（验证码重置） */
    @POST("/api/auth/resetPassword")
    Call<BaseResponse<Void>> resetPassword(@Body ResetPasswordRequest request);

    @POST("/api/auth/logout")
    Call<BaseResponse<Void>> logout();

    /** 获取当前用户资料 */
    @GET("/api/auth/me")
    Call<BaseResponse<LoginResponse>> getCurrentUser();

    /** 更新个人资料 */
    @PUT("/api/auth/profile")
    Call<BaseResponse<LoginResponse>> updateProfile(@Body ProfileUpdateRequest request);

    @Multipart
    @POST("/api/auth/profile/avatar")
    Call<BaseResponse<Map<String, String>>> uploadAvatar(@Part MultipartBody.Part file);

    /** 主动退出当前家庭 */
    @POST("/api/family/leave")
    Call<BaseResponse<Void>> leaveFamily();

    /** 角色选择（同步到后端） */
    @POST("/api/auth/role")
    Call<BaseResponse<Map<String, Object>>> selectRole(@Body Map<String, String> request);

    /** App 版本检查 */
    @GET("/api/app/version/check")
    Call<BaseResponse<Object>> checkVersion(
            @Query("platform") String platform,
            @Query("currentVersion") String currentVersion,
            @Query("currentVersionCode") Integer currentVersionCode);


}

