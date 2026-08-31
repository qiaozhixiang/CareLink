package com.carelink.service;

import com.carelink.entity.CompanionReminder;
import com.carelink.entity.User;
import com.carelink.repository.CompanionReminderRepository;
import com.carelink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanionReminderService {

    private final CompanionReminderRepository reminderRepository;
    private final UserRepository userRepository;

    @Value("${carelink.upload.base-dir:C:/carelink/uploads}")
    private String uploadBaseDir;

    @Value("${carelink.upload.url-prefix:http://localhost:8080/files}")
    private String uploadUrlPrefix;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    @Transactional
    public Map<String, Object> sendToElder(Long senderUserId,
                                           Long elderUserId,
                                           String emoji,
                                           String label,
                                           String message,
                                           String imageUrl) {
        User sender = userRepository.findById(senderUserId)
                .orElseThrow(() -> new RuntimeException("发送人不存在"));
        User elder = userRepository.findById(elderUserId)
                .orElseThrow(() -> new RuntimeException("目标老人不存在"));

        if (sender.getFamilyId() == null || !Objects.equals(sender.getFamilyId(), elder.getFamilyId())) {
            throw new RuntimeException("目标老人不在当前家庭中");
        }
        if ("ELDER".equalsIgnoreCase(sender.getRole())) {
            throw new RuntimeException("老人账号不能发送关怀提醒");
        }

        String safeMessage = message == null ? "" : message.trim();
        if (safeMessage.isEmpty()) {
            throw new RuntimeException("提醒内容不能为空");
        }
        if (safeMessage.length() > 500) {
            throw new RuntimeException("提醒内容过长");
        }

        CompanionReminder reminder = CompanionReminder.builder()
                .familyId(sender.getFamilyId())
                .senderUserId(sender.getId())
                .elderUserId(elder.getId())
                .emoji(optionalTrim(emoji))
                .label(optionalTrim(label))
                .message(safeMessage)
                .imageUrl(optionalTrim(imageUrl))
                .senderName(resolveSenderName(sender))
                .isRead(false)
                .build();

        CompanionReminder saved = reminderRepository.save(reminder);
        return toMap(saved);
    }

    public List<Map<String, Object>> getUnreadForElder(Long elderUserId) {
        User elder = userRepository.findById(elderUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!"ELDER".equalsIgnoreCase(elder.getRole())) {
            return Collections.emptyList();
        }

        List<CompanionReminder> reminders =
                reminderRepository.findTop20ByElderUserIdAndIsReadFalseOrderByCreatedAtDesc(elderUserId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CompanionReminder reminder : reminders) {
            result.add(toMap(reminder));
        }
        return result;
    }

    public List<Map<String, Object>> getSentBySender(Long senderUserId) {
        User sender = userRepository.findById(senderUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if ("ELDER".equalsIgnoreCase(sender.getRole())) {
            return Collections.emptyList();
        }

        List<CompanionReminder> reminders =
                reminderRepository.findTop50BySenderUserIdOrderByCreatedAtDesc(senderUserId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CompanionReminder reminder : reminders) {
            result.add(toMap(reminder));
        }
        return result;
    }

    @Transactional
    public void markRead(Long currentUserId, Long reminderId) {
        CompanionReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new RuntimeException("提醒不存在"));
        if (!Objects.equals(reminder.getElderUserId(), currentUserId)) {
            throw new RuntimeException("无权操作该提醒");
        }
        reminder.setIsRead(true);
        reminderRepository.save(reminder);
    }

    @Transactional
    public void deleteBySender(Long senderUserId, Long reminderId) {
        CompanionReminder reminder = reminderRepository.findByIdAndSenderUserId(reminderId, senderUserId)
                .orElseThrow(() -> new RuntimeException("提醒不存在或无权删除"));
        reminderRepository.delete(reminder);
    }

    public String uploadReminderImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请选择图片文件");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new RuntimeException("仅支持图片文件");
        }

        String extension = ".jpg";
        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < originalName.length() - 1) {
                String ext = originalName.substring(dotIndex).toLowerCase();
                if (ext.length() <= 10) {
                    extension = ext;
                }
            }
        }

        String fileName = "reminder_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().replace("-", "") + extension;

        Path reminderDir = Paths.get(uploadBaseDir, "reminders");
        Path targetFile = reminderDir.resolve(fileName);
        try {
            Files.createDirectories(reminderDir);
            file.transferTo(targetFile.toFile());
        } catch (IOException e) {
            throw new RuntimeException("上传图片失败，请稍后重试");
        }

        return uploadUrlPrefix.replaceAll("/+$", "") + "/reminders/" + fileName;
    }

    private Map<String, Object> toMap(CompanionReminder reminder) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", reminder.getId());
        map.put("emoji", defaultValue(reminder.getEmoji(), "🔔"));
        map.put("label", defaultValue(reminder.getLabel(), "关怀提醒"));
        map.put("message", reminder.getMessage());
        map.put("imageUrl", defaultValue(reminder.getImageUrl(), ""));
        map.put("image_url", defaultValue(reminder.getImageUrl(), ""));
        map.put("sender", defaultValue(reminder.getSenderName(), "家属"));
        map.put("time", reminder.getCreatedAt() == null ? "" : reminder.getCreatedAt().format(TIME_FMT));
        map.put("timestamp", reminder.getCreatedAt() == null
                ? System.currentTimeMillis()
                : reminder.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        map.put("read", Boolean.TRUE.equals(reminder.getIsRead()));
        map.put("readTime", reminder.getUpdatedAt() == null ? "" : reminder.getUpdatedAt().format(TIME_FMT));
        map.put("senderUserId", reminder.getSenderUserId());
        map.put("elderUserId", reminder.getElderUserId());
        return map;
    }

    private String optionalTrim(String text) {
        if (text == null) {
            return null;
        }
        String v = text.trim();
        return v.isEmpty() ? null : v;
    }

    private String resolveSenderName(User user) {
        String nick = optionalTrim(user.getNickname());
        if (nick != null) {
            return nick;
        }
        String email = optionalTrim(user.getEmail());
        if (email == null) {
            return "家属";
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
