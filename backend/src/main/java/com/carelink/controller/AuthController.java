package com.carelink.controller;

import com.carelink.dto.*;
import com.carelink.entity.User;
import com.carelink.service.AuthService;
import com.carelink.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/sendCode")
    public ResponseEntity<ApiResponse<Void>> sendCode(@Valid @RequestBody EmailCodeRequest request) {
        EmailVerificationService.Result result = emailVerificationService.sendRegisterCode(request.getEmail());
        if (!result.success()) {
            return ResponseEntity.ok(ApiResponse.fail(result.message()));
        }
        return ResponseEntity.ok(ApiResponse.ok(result.message(), null));
    }

    /** 鍙戦€佸繕璁板瘑鐮侀獙璇佺爜锛堟棤闇€鐧诲綍锛?*/
    @PostMapping("/sendResetCode")
    public ResponseEntity<ApiResponse<Void>> sendResetCode(@Valid @RequestBody EmailCodeRequest request) {
        return ResponseEntity.ok(authService.sendResetPasswordCode(request.getEmail()));
    }

    /** 鎻愪氦鏂板瘑鐮侊紙楠岃瘉鐮?鏂板瘑鐮侊紝鏃犻渶鐧诲綍锛?*/
    @PostMapping("/resetPassword")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<LoginResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** 寰俊鐧诲綍锛氭帴鏀?App 浼犳潵鐨勫井淇℃巿鏉冪爜 */
    @PostMapping("/wechat")
    public ResponseEntity<ApiResponse<LoginResponse>> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return ResponseEntity.ok(authService.wechatLogin(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(authService.logout());
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LoginResponse>> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("鐧诲綍鐘舵€佸凡杩囨湡锛岃閲嶆柊鐧诲綍"));
        }
        return ResponseEntity.ok(authService.getCurrentUser(user.getId()));
    }

    @PostMapping("/role")
    public ResponseEntity<ApiResponse<Map<String, Object>>> selectRole(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RoleSelectRequest request) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("鐧诲綍鐘舵€佸凡杩囨湡锛岃閲嶆柊鐧诲綍"));
        }
        return ResponseEntity.ok(authService.selectRole(user.getId(), request));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<LoginResponse>> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody ProfileUpdateRequest request) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("鐧诲綍鐘舵€佸凡杩囨湡锛岃閲嶆柊鐧诲綍"));
        }
        return ResponseEntity.ok(authService.updateProfile(user.getId(), request));
    }

    @PostMapping("/profile/avatar")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAvatar(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("鐧诲綍鐘舵€佸凡杩囨湡锛岃閲嶆柊鐧诲綍"));
        }
        return ResponseEntity.ok(authService.uploadAvatar(user.getId(), file));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "CareLinkApp Backend"));
    }
}

