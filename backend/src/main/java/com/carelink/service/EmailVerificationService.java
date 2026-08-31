package com.carelink.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RESEND_INTERVAL_SECONDS = 60;

    private final JavaMailSender mailSender;

    @Value("${spring.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${carelink.mail.mock-mode:true}")
    private boolean mockMode;

    @Value("${carelink.mail.mock-code:123456}")
    private String mockCode;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${carelink.mail.from:}")
    private String mailFrom;

    @Value("${carelink.mail.verify-code-expire:10}")
    private int expireMinutes;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.port:0}")
    private int mailPort;

    private final Map<String, VerificationCodeRecord> codeStore = new ConcurrentHashMap<>();

    public EmailVerificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PostConstruct
    public void logMailConfigOnStartup() {
        log.info("Mail config loaded: enabled={}, mockMode={}, host={}, port={}, username={}, from={}",
                mailEnabled, mockMode, mailHost, mailPort, safe(mailUsername), safe(mailFrom));
    }

    public Result sendRegisterCode(String email) {
        String normalizedEmail = normalize(email);
        if (mockMode) {
            return issueMockCode(normalizedEmail, "register");
        }

        if (!mailEnabled || mailUsername == null || mailUsername.isBlank()) {
            log.warn("邮箱验证码发送未启用，email={}", normalizedEmail);
            return Result.fail("邮箱验证码服务尚未配置完成");
        }

        VerificationCodeRecord existing = codeStore.get(normalizedEmail);
        if (existing != null && !existing.isExpired() && existing.secondsUntilResend() > 0) {
            return Result.fail("请求过于频繁，请在 " + existing.secondsUntilResend() + " 秒后重试");
        }

        String code = generateCode();
        LocalDateTime now = LocalDateTime.now();
        VerificationCodeRecord record = new VerificationCodeRecord(
                code,
                now.plusMinutes(Math.max(expireMinutes, 1)),
                now.plusSeconds(RESEND_INTERVAL_SECONDS)
        );
        codeStore.put(normalizedEmail, record);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(normalizedEmail);
            message.setSubject("【CareLink】邮箱验证码");
            message.setFrom(resolveFromAddress());
            message.setText(buildMailContent(code));
            mailSender.send(message);
            log.info("邮箱验证码已发送: email={}", normalizedEmail);
            return Result.ok("验证码已发送，请查收邮箱");
        } catch (Exception e) {
            codeStore.remove(normalizedEmail);
            log.error("发送邮箱验证码失败: email={}", normalizedEmail, e);
            return Result.fail(resolveMailSendErrorMessage(e));
        }
    }

    public Result verifyRegisterCode(String email, String code) {
        return verifyCode(normalize(email), code);
    }

    public Result sendResetPasswordCode(String email) {
        String normalizedEmail = normalize(email);
        String key = resetKey(normalizedEmail);

        if (mockMode) {
            return issueMockCode(key, "reset");
        }

        if (!mailEnabled || mailUsername == null || mailUsername.isBlank()) {
            log.warn("邮箱验证码发送未启用，email={}", normalizedEmail);
            return Result.fail("邮箱验证码服务尚未配置完成");
        }

        VerificationCodeRecord existing = codeStore.get(key);
        if (existing != null && !existing.isExpired() && existing.secondsUntilResend() > 0) {
            return Result.fail("请求过于频繁，请在 " + existing.secondsUntilResend() + " 秒后重试");
        }

        String code = generateCode();
        LocalDateTime now = LocalDateTime.now();
        VerificationCodeRecord record = new VerificationCodeRecord(
                code,
                now.plusMinutes(Math.max(expireMinutes, 1)),
                now.plusSeconds(RESEND_INTERVAL_SECONDS)
        );
        codeStore.put(key, record);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(normalizedEmail);
            message.setSubject("【CareLink】密码重置验证码");
            message.setFrom(resolveFromAddress());
            message.setText(buildResetMailContent(code));
            mailSender.send(message);
            log.info("密码重置验证码已发送: email={}", normalizedEmail);
            return Result.ok("验证码已发送，请查收邮箱");
        } catch (Exception e) {
            codeStore.remove(key);
            log.error("发送密码重置验证码失败: email={}", normalizedEmail, e);
            return Result.fail(resolveMailSendErrorMessage(e));
        }
    }

    public Result verifyResetPasswordCode(String email, String code) {
        return verifyCode(resetKey(normalize(email)), code);
    }

    private Result verifyCode(String storeKey, String code) {
        VerificationCodeRecord record = codeStore.get(storeKey);
        if (record == null) {
            return Result.fail("请先获取邮箱验证码");
        }
        if (record.isExpired()) {
            codeStore.remove(storeKey);
            return Result.fail("验证码已过期，请重新获取");
        }
        if (!record.code().equals(code)) {
            return Result.fail("验证码错误");
        }
        codeStore.remove(storeKey);
        return Result.ok("验证通过");
    }

    private String resetKey(String normalizedEmail) {
        return "reset:" + normalizedEmail;
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String generateCode() {
        int value = RANDOM.nextInt(900000) + 100000;
        return String.valueOf(value);
    }

    private String buildMailContent(String code) {
        return "您正在使用 CareLink 注册账号。\n\n"
                + "本次验证码：" + code + "\n"
                + "有效期：" + Math.max(expireMinutes, 1) + " 分钟\n\n"
                + "如果这不是您的操作，请忽略本邮件。";
    }

    private String buildResetMailContent(String code) {
        return "您正在重置 CareLink 账号密码。\n\n"
                + "本次验证码：" + code + "\n"
                + "有效期：" + Math.max(expireMinutes, 1) + " 分钟\n\n"
                + "如果这不是您的操作，请忽略本邮件，您的密码不会被修改。";
    }

    private String resolveFromAddress() {
        if (mailFrom != null && !mailFrom.isBlank()) {
            return mailFrom;
        }
        return mailUsername;
    }

    private Result issueMockCode(String storeKey, String scene) {
        VerificationCodeRecord existing = codeStore.get(storeKey);
        if (existing != null && !existing.isExpired() && existing.secondsUntilResend() > 0) {
            return Result.fail("请求过于频繁，请在 " + existing.secondsUntilResend() + " 秒后重试");
        }

        String code = (mockCode == null || mockCode.isBlank()) ? "123456" : mockCode.trim();
        LocalDateTime now = LocalDateTime.now();
        VerificationCodeRecord record = new VerificationCodeRecord(
                code,
                now.plusMinutes(Math.max(expireMinutes, 1)),
                now.plusSeconds(RESEND_INTERVAL_SECONDS)
        );
        codeStore.put(storeKey, record);

        log.warn("Mock email verification enabled, scene={}, key={}, code={}", scene, storeKey, code);
        return Result.ok("验证码已发送（测试验证码：" + code + "）");
    }

    private String resolveMailSendErrorMessage(Exception e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof MailAuthenticationException) {
                return "QQ邮箱服务认证失败，请检查邮箱账号和 SMTP 授权码";
            }
            if (current instanceof MailSendException) {
                return "邮件发送失败，请检查发件人配置与邮箱服务状态";
            }
            if (current instanceof java.net.ConnectException) {
                return "无法连接邮件服务器，请检查服务器是否放行 465/587 出站端口";
            }
            if (current instanceof java.net.SocketTimeoutException) {
                return "邮件服务器连接超时，请检查服务器网络与 SMTP 端口放行";
            }
            if (current instanceof java.net.UnknownHostException) {
                return "邮件服务器域名解析失败，请检查服务器 DNS 配置";
            }
            current = current.getCause();
        }
        return "验证码发送失败，请稍后重试";
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        int at = value.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return value.charAt(0) + "***" + value.substring(at);
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    private record VerificationCodeRecord(String code, LocalDateTime expireAt, LocalDateTime resendAvailableAt) {
        private boolean isExpired() {
            return LocalDateTime.now().isAfter(expireAt);
        }

        private long secondsUntilResend() {
            return Math.max(0, Duration.between(LocalDateTime.now(), resendAvailableAt).getSeconds());
        }
    }
}
