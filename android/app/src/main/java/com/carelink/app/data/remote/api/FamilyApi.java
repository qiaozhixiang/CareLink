package com.carelink.app.data.remote.api;

import com.carelink.app.data.remote.dto.BaseResponse;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

public interface FamilyApi {

    /** 获取家庭成员列表 */
    @GET("/api/family/members")
    Call<BaseResponse<List<Map<String, Object>>>> getMembers();

    /** 获取家庭老人列表 */
    @GET("/api/family/elders")
    Call<BaseResponse<List<Map<String, Object>>>> getElders();

    @GET("/api/family/info")
    Call<BaseResponse<Map<String, Object>>> getFamilyInfo(@Query("familyId") Long familyId);

    /** 验证邀请码是否有效 */
    @GET("/api/family/invite/validate")
    Call<BaseResponse<Map<String, Object>>> validateInviteCode(@Query("code") String code);

    /** 创建新家庭 */
    @POST("/api/family/create")
    Call<BaseResponse<Map<String, Object>>> createFamily(@Body Map<String, Object> body);

    /** 加入已有家庭（使用邀请码） */
    @POST("/api/family/join")
    Call<BaseResponse<Map<String, Object>>> joinFamily(@Body Map<String, Object> body);

    /** 当前用户主动退出家庭 */
    @POST("/api/family/leave")
    Call<BaseResponse<Map<String, Object>>> leaveFamily();

    /** 生成新的邀请码 */
    @POST("/api/family/invite")
    Call<BaseResponse<Map<String, Object>>> inviteMember(@Body Map<String, Object> body);

    /** 移除家庭成员 */
    @DELETE("/api/family/members/{userId}")
    Call<BaseResponse<Void>> removeMember(@Path("userId") long userId);

    /** 转移家庭创建者 */
    @POST("/api/family/creator/transfer")
    Call<BaseResponse<Map<String, Object>>> transferCreator(@Body Map<String, Object> body);

    /** 解散家庭 */
    @DELETE("/api/family/dissolve")
    Call<BaseResponse<Map<String, Object>>> dissolveFamily();

    /** 解散家庭（原始响应容错，用于兼容旧后端返回体） */
    @DELETE("/api/family/dissolve")
    Call<ResponseBody> dissolveFamilyRaw();



}
