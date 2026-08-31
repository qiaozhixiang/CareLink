package com.carelink.service;

import com.carelink.dto.*;
import com.carelink.entity.User;
import com.carelink.repository.UserRepository;
import com.carelink.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final int USER_LOCK_RETRY_TIMES = 3;
    private static final long USER_LOCK_RETRY_BASE_SLEEP_MS = 180L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailVerificationService emailVerificationService;
    private final PlatformTransactionManager transactionManager;

    @Value("${wechat.app-id:}")
    private String wechatAppId;

    @Value("${wechat.app-secret:}")
    private String wechatAppSecret;

    @Value("${carelink.upload.base-dir:C:/carelink/uploads}")
    private String uploadBaseDir;

    @Value("${carelink.upload.url-prefix:http://localhost:8080/files}")
    private String uploadUrlPrefix;

    /** 注册 */
    @Transactional
    public ApiResponse<LoginResponse> register(RegisterRequest request) {
        String normalizedEmail = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            return ApiResponse.fail("该邮箱已被注册");
        }

        EmailVerificationService.Result verifyResult =
                emailVerificationService.verifyRegisterCode(normalizedEmail, request.getVerifyCode());
        if (!verifyResult.success()) {
            return ApiResponse.fail(verifyResult.message());
        }

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(resolveNickname(request, normalizedEmail))
                .emailVerified(true)
                .build();

        user = userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());

        return ApiResponse.ok("注册成功", buildLoginResponse(user, token));
    }

    /** 登录 */
    public ApiResponse<LoginResponse> login(LoginRequest request) {
        String normalizedEmail = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return ApiResponse.fail("邮箱或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ApiResponse.fail("邮箱或密码错误");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());

        return ApiResponse.ok("登录成功", buildLoginResponse(user, token));
    }

    /** 发送密码重置验证码（不需要登录） */
    public ApiResponse<Void> sendResetPasswordCode(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (!userRepository.existsByEmail(normalizedEmail)) {
            // 不暴露邮箱是否已注册，统一返回"已发送"以防止枚举攻击
            return ApiResponse.ok("如果该邮箱已注册，验证码将发送到您的邮箱", null);
        }
        EmailVerificationService.Result result = emailVerificationService.sendResetPasswordCode(normalizedEmail);
        if (!result.success()) {
            return ApiResponse.fail(result.message());
        }
        return ApiResponse.ok(result.message(), null);
    }

    /** 校验验证码并重置密码（不需要登录） */
    @Transactional
    public ApiResponse<Void> resetPassword(ResetPasswordRequest request) {
        String normalizedEmail = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return ApiResponse.fail("邮箱不存在");
        }
        EmailVerificationService.Result verifyResult =
                emailVerificationService.verifyResetPasswordCode(normalizedEmail, request.getVerifyCode());
        if (!verifyResult.success()) {
            return ApiResponse.fail(verifyResult.message());
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("用户密码已重置: email={}", normalizedEmail);
        return ApiResponse.ok("密码已重置，请使用新密码登录", null);
    }

    /** 退出登录（前端删除 Token 即可，这里仅记录） */
    public ApiResponse<Void> logout() {
        return ApiResponse.ok("已退出", null);
    }

    /** 角色选择 */
    @Transactional
    public ApiResponse<Map<String, Object>> selectRole(Long userId, RoleSelectRequest request) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }

        String role = request.getRole().toUpperCase();
        if (!role.equals("ELDER") && !role.equals("FAMILY")) {
            return ApiResponse.fail("角色只能是 ELDER 或 FAMILY");
        }

        // 1个月内不能切换（如果有角色且未验证邮箱）
        if (user.getRole() != null && !user.getRole().equals(role) && !Boolean.TRUE.equals(user.getEmailVerified())) {
            return ApiResponse.fail("请先验证邮箱，1个月内不能随意切换身份");
        }

        user.setRole(role);
        user.setRoleSelectedAt(System.currentTimeMillis());
        userRepository.save(user);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("role", user.getRole());
        result.put("familyId", user.getFamilyId());

        return ApiResponse.ok("角色选择成功", result);
    }

    /** 获取当前用户信息 */
    public ApiResponse<LoginResponse> getCurrentUser(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }
        return ApiResponse.ok(buildProfileResponse(user));
    }

    /** 更新个人资料 */
    public ApiResponse<LoginResponse> updateProfile(Long userId, ProfileUpdateRequest request) {
        try {
            return executeWithUserLockRetry("updateProfile", () -> {
                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    return ApiResponse.fail("用户不存在");
                }

                if (request.getNickname() != null) user.setNickname(request.getNickname());
                if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
                if (request.getEmergencyContactName() != null) user.setEmergencyContactName(request.getEmergencyContactName());
                if (request.getEmergencyContactPhone() != null) user.setEmergencyContactPhone(request.getEmergencyContactPhone());

                user = userRepository.saveAndFlush(user);
                return ApiResponse.ok("资料更新成功", buildProfileResponse(user));
            });
        } catch (RuntimeException ex) {
            log.error("更新用户资料失败(可能是并发冲突): userId={}, error={}", userId, rootMessage(ex), ex);
            return ApiResponse.fail("数据库繁忙，请稍后再试");
        }
    }

    private LoginResponse buildLoginResponse(User user, String token) {
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .familyId(user.getFamilyId())
                .emailVerified(user.getEmailVerified())
                .build();
    }

    private LoginResponse buildProfileResponse(User user) {
        return LoginResponse.builder()
                .token(null)
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .familyId(user.getFamilyId())
                .emailVerified(user.getEmailVerified())
                .build();
    }

    private String resolveNickname(RegisterRequest request, String normalizedEmail) {
        String nickname = request.getNickname();
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname.trim();
        }
        int atIndex = normalizedEmail.indexOf('@');
        return atIndex > 0 ? normalizedEmail.substring(0, atIndex) : normalizedEmail;
    }

    public ApiResponse<Map<String, String>> uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail("请选择头像文件");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            return ApiResponse.fail("仅支持图片文件");
        }

        String originalName = file.getOriginalFilename();
        String extension = ".jpg";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
            if (extension.length() > 10) {
                extension = ".jpg";
            }
        }

        String fileName = "avatar_" + userId + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
        Path avatarDir = Paths.get(uploadBaseDir, "avatars");
        Path target = avatarDir.resolve(fileName);
        try {
            Files.createDirectories(avatarDir);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("上传头像失败 userId={}", userId, e);
            return ApiResponse.fail("头像上传失败，请稍后重试");
        }

        String avatarUrl = uploadUrlPrefix.replaceAll("/+$", "") + "/avatars/" + fileName;
        try {
            return executeWithUserLockRetry("uploadAvatar", () -> {
                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    return ApiResponse.fail("用户不存在");
                }
                user.setAvatarUrl(avatarUrl);
                userRepository.saveAndFlush(user);

                Map<String, String> data = new HashMap<>();
                data.put("avatarUrl", avatarUrl);
                return ApiResponse.ok("头像上传成功", data);
            });
        } catch (RuntimeException ex) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ioEx) {
                log.warn("清理头像文件失败: path={}", target, ioEx);
            }
            log.error("头像入库失败(可能是并发冲突): userId={}, avatarUrl={}, error={}",
                    userId, avatarUrl, rootMessage(ex), ex);
            return ApiResponse.fail("数据库繁忙，请稍后再试");
        }
    }

    private <T> T executeWithUserLockRetry(String scene, Supplier<T> action) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= USER_LOCK_RETRY_TIMES; attempt++) {
            try {
                return runInTransaction(action);
            } catch (RuntimeException ex) {
                last = ex;
                if (!isRetryableUserLockException(ex) || attempt >= USER_LOCK_RETRY_TIMES) {
                    throw ex;
                }
                long sleepMs = USER_LOCK_RETRY_BASE_SLEEP_MS * attempt;
                log.warn("检测到用户表锁冲突，准备重试: scene={}, attempt={}/{}, waitMs={}, reason={}",
                        scene, attempt, USER_LOCK_RETRY_TIMES, sleepMs, rootMessage(ex));
                sleepQuietly(sleepMs);
            }
        }
        throw last == null ? new RuntimeException("unknown lock retry failure") : last;
    }

    private <T> T runInTransaction(Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> action.get());
    }

    private boolean isRetryableUserLockException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CannotAcquireLockException
                    || current instanceof PessimisticLockingFailureException
                    || current instanceof DeadlockLoserDataAccessException
                    || current instanceof ObjectOptimisticLockingFailureException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("lock wait timeout exceeded")
                        || lower.contains("deadlock found when trying to get lock")
                        || lower.contains("could not execute batch")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? "" : String.valueOf(current.getMessage());
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 微信登录
     * 1. 用 code 向微信服务器换取 openid
     * 2. 根据 openid 查找或创建用户
     * 3. 返回 JWT Token
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public ApiResponse<LoginResponse> wechatLogin(WechatLoginRequest request) {
        String code = request.getCode();

        // Step 1: 向微信接口服务换 openid
        if (wechatAppId == null || wechatAppId.isBlank() || wechatAppSecret == null || wechatAppSecret.isBlank()) {
            log.warn("微信登录未配置 AppID/AppSecret，尝试模拟登录（仅供开发调试）");
            // 开发调试模式：直接用 code 作为 openid
            String mockOpenid = "mock_" + code;
            return processWechatUser(mockOpenid, request.getNickname());
        }

        String wechatUrl = "https://api.weixin.qq.com/sns/jscode2session" +
                "?appid=" + wechatAppId +
                "&secret=" + wechatAppSecret +
                "&js_code=" + code +
                "&grant_type=authorization_code";

        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> wxResponse = restTemplate.getForObject(wechatUrl, Map.class);

            if (wxResponse == null || wxResponse.containsKey("errcode")) {
                Integer errcode = wxResponse == null ? -1 : (Integer) wxResponse.getOrDefault("errcode", -1);
                String errmsg = wxResponse == null ? "微信登录失败" : (String) wxResponse.getOrDefault("errmsg", "微信登录失败");
                log.error("微信接口返回错误: {} - {}", errcode, errmsg);
                return ApiResponse.fail("微信登录失败: " + errmsg);
            }

            String openid = (String) wxResponse.get("openid");
            if (openid == null || openid.isBlank()) {
                return ApiResponse.fail("微信登录失败：未获取到用户标识");
            }

            return processWechatUser(openid, request.getNickname());

        } catch (Exception e) {
            log.error("微信登录异常", e);
            return ApiResponse.fail("微信登录异常: " + e.getMessage());
        }
    }

    /** 处理微信用户：查找或创建 */
    private ApiResponse<LoginResponse> processWechatUser(String openid, String nickname) {
        // 查找已有用户
        User user = userRepository.findByWechatOpenid(openid).orElse(null);

        if (user != null) {
            // 已绑定用户，直接登录
            String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
            return ApiResponse.ok("微信登录成功", buildLoginResponse(user, token));
        }

        // 首次微信登录，自动创建账号
        String defaultNickname = (nickname != null && !nickname.isBlank())
                ? nickname
                : "微信用户";

        User newUser = User.builder()
                .email(openid + "@wechat.local")
                .password(passwordEncoder.encode(openid)) // 随机密码
                .nickname(defaultNickname)
                .wechatOpenid(openid)
                .emailVerified(true) // 微信登录无需验证邮箱
                .build();

        newUser = userRepository.save(newUser);
        String token = jwtTokenProvider.generateToken(newUser.getId(), newUser.getEmail());

        log.info("新微信用户注册: openid={}, nickname={}", openid, defaultNickname);
        return ApiResponse.ok("微信登录成功（已创建账号）", buildLoginResponse(newUser, token));
    }
}

