package com.carelink.controller;

import com.carelink.dto.ApiResponse;
import com.carelink.dto.FamilyMemberResponse;
import com.carelink.entity.User;
import com.carelink.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createFamily(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) Map<String, String> body) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("登录已过期，请重新登录"));
        }
        String name = body == null ? null : body.get("name");
        Map<String, Object> result = familyService.createFamily(user.getId(), name);
        return ResponseEntity.ok(ApiResponse.ok("家庭创建成功", result));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<Map<String, Object>>> joinFamily(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> body) {
        String inviteCode = body.get("inviteCode");
        Map<String, Object> result = familyService.joinFamily(user.getId(), inviteCode);
        return ResponseEntity.ok(ApiResponse.ok("加入家庭成功", result));
    }

    @GetMapping("/invite/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateInviteCode(
            @RequestParam String code) {
        Map<String, Object> result = familyService.validateInviteCode(code);
        return ResponseEntity.ok(ApiResponse.ok("邀请码有效", result));
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFamilyInfo(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long familyId) {
        Long resolvedFamilyId = familyId != null ? familyId : user.getFamilyId();
        Map<String, Object> result = familyService.getFamilyInfo(resolvedFamilyId, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/members")
    public ResponseEntity<ApiResponse<List<FamilyMemberResponse>>> getMembers(
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("登录已过期，请重新登录"));
        }
        Long familyId = user.getFamilyId();
        if (familyId == null) {
            return ResponseEntity.ok(ApiResponse.fail("尚未加入任何家庭"));
        }
        List<FamilyMemberResponse> members = familyService.getFamilyMembers(familyId, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(members));
    }

    @GetMapping("/elders")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getElders(
            @AuthenticationPrincipal User user) {
        Long familyId = user.getFamilyId();
        if (familyId == null) {
            return ResponseEntity.ok(ApiResponse.fail("尚未加入任何家庭"));
        }
        List<Map<String, Object>> elders = familyService.getFamilyElders(familyId);
        return ResponseEntity.ok(ApiResponse.ok(elders));
    }

    @DeleteMapping("/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @AuthenticationPrincipal User user,
            @PathVariable Long userId) {
        familyService.removeMember(user.getId(), userId);
        return ResponseEntity.ok(ApiResponse.ok("成员已移除", null));
    }

    @PostMapping("/creator/transfer")
    public ResponseEntity<ApiResponse<Map<String, Object>>> transferCreator(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {
        Object targetUserIdObj = body == null ? null : body.get("targetUserId");
        Long targetUserId = targetUserIdObj instanceof Number
                ? ((Number) targetUserIdObj).longValue()
                : targetUserIdObj == null ? null : Long.parseLong(targetUserIdObj.toString());
        Map<String, Object> result = familyService.transferCreator(user.getId(), targetUserId);
        return ResponseEntity.ok(ApiResponse.ok("家庭创建者已转移", result));
    }

    @DeleteMapping("/dissolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dissolveFamily(@AuthenticationPrincipal User user) {
        Map<String, Object> result = familyService.dissolveFamily(user.getId());
        return ResponseEntity.ok(ApiResponse.ok("家庭已解散", result));
    }


    @PostMapping("/leave")
    public ResponseEntity<ApiResponse<Map<String, Object>>> leaveFamily(@AuthenticationPrincipal User user) {
        Map<String, Object> result = familyService.leaveFamily(user.getId());
        return ResponseEntity.ok(ApiResponse.ok("已退出当前家庭", result));
    }

}



