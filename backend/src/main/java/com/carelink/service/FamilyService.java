package com.carelink.service;

import com.carelink.dto.FamilyMemberResponse;
import com.carelink.entity.Family;
import com.carelink.entity.User;
import com.carelink.repository.FamilyRepository;
import com.carelink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyService {

    private static final int MAX_INVITE_CODE_ATTEMPTS = 10;

    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;

    /** 创建家庭组 */
    @Transactional
    public Map<String, Object> createFamily(Long userId, String familyName) {
        long startAt = System.currentTimeMillis();
        log.info("开始创建家庭: userId={}, familyName={}", userId, familyName);

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        String normalizedName = normalizeFamilyName(familyName);

        Family existingFamily = findExistingFamily(user.getFamilyId());
        if (existingFamily != null) {
            log.info("用户已存在家庭，直接返回当前家庭信息: userId={}, familyId={}", userId, existingFamily.getId());
            ensureUserRoleForFamily(user);
            userRepository.saveAndFlush(user);
            return buildFamilyPayload(existingFamily, userId);
        }

        Family family = null;
        for (int attempt = 1; attempt <= MAX_INVITE_CODE_ATTEMPTS; attempt++) {
            String inviteCode = generateInviteCode();
            try {
                family = familyRepository.saveAndFlush(Family.builder()
                        .name(normalizedName)
                        .inviteCode(inviteCode)
                        .creatorId(userId)
                        .build());
                log.info("家庭主记录创建成功: userId={}, familyId={}, inviteCode={}, attempt={}", userId, family.getId(), inviteCode, attempt);
                break;
            } catch (DataIntegrityViolationException ex) {
                log.warn("家庭邀请码冲突，重试生成: userId={}, inviteCode={}, attempt={}", userId, inviteCode, attempt);
                if (attempt == MAX_INVITE_CODE_ATTEMPTS) {
                    throw new RuntimeException("创建家庭失败，请稍后重试");
                }
            }
        }

        if (family == null) {
            throw new RuntimeException("创建家庭失败，请稍后重试");
        }

        try {
            user.setFamilyId(family.getId());
            ensureUserRoleForFamily(user);
            userRepository.saveAndFlush(user);
        } catch (RuntimeException ex) {
            log.error("家庭创建后回写用户家庭信息失败: userId={}, familyId={}, role={}", userId, family.getId(), user.getRole(), ex);
            throw ex;
        }

        Map<String, Object> result = buildCreateFamilyPayload(family, userId);
        log.info("创建家庭完成: userId={}, familyId={}, costMs={}", userId, family.getId(), System.currentTimeMillis() - startAt);
        return result;
    }

    /** 加入家庭 */
    @Transactional
    public Map<String, Object> joinFamily(Long userId, String inviteCode) {
        String normalizedCode = normalizeInviteCode(inviteCode);
        Family family = familyRepository.findByInviteCode(normalizedCode)
                .orElseThrow(() -> new RuntimeException("邀请码无效或已过期"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setFamilyId(family.getId());
        userRepository.save(user);

        return buildFamilyPayload(family, userId);
    }

    /** 校验邀请码 */
    public Map<String, Object> validateInviteCode(String inviteCode) {
        String normalizedCode = normalizeInviteCode(inviteCode);
        Family family = familyRepository.findByInviteCode(normalizedCode)
                .orElseThrow(() -> new RuntimeException("邀请码无效或已过期"));

        Map<String, Object> result = new HashMap<>();
        result.put("familyId", family.getId());
        result.put("familyName", family.getName());
        result.put("inviteCode", family.getInviteCode());
        result.put("memberCount", userRepository.findByFamilyId(family.getId()).size());
        return result;
    }

    /** 获取家庭信息 */
    public Map<String, Object> getFamilyInfo(Long familyId, Long currentUserId) {
        if (familyId == null) {
            throw new RuntimeException("尚未加入任何家庭");
        }
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new RuntimeException("家庭不存在"));
        return buildFamilyPayload(family, currentUserId);
    }

    /** 获取家庭成员列表 */
    public List<FamilyMemberResponse> getFamilyMembers(Long familyId, Long currentUserId) {
        if (familyId == null) {
            throw new RuntimeException("尚未加入任何家庭");
        }
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new RuntimeException("家庭不存在"));
        List<User> members = userRepository.findByFamilyId(familyId);
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }

        return members.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing((User member) -> !Objects.equals(member.getId(), family.getCreatorId()))
                        .thenComparing(member -> normalizeDisplayName(member.getNickname(), member.getEmail()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(User::getId, Comparator.nullsLast(Long::compareTo)))
                .map(user -> buildFamilyMemberResponse(user, family, currentUserId))
                .collect(Collectors.toList());
    }


    /** 获取家庭中的老人列表 */
    public List<Map<String, Object>> getFamilyElders(Long familyId) {
        List<User> members = userRepository.findByFamilyId(familyId);
        return members.stream()
                .filter(u -> "ELDER".equals(u.getRole()))
                .map(user -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("userId", user.getId());
                    map.put("nickname", user.getNickname());
                    map.put("avatarUrl", user.getAvatarUrl());
                    return map;
                }).collect(Collectors.toList());
    }

    /** 移除家庭成员 */
    @Transactional
    public void removeMember(Long operatorUserId, Long targetUserId) {
        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("当前用户不存在"));
        if (operator.getFamilyId() == null) {
            throw new RuntimeException("尚未加入任何家庭");
        }

        Family family = familyRepository.findById(operator.getFamilyId())
                .orElseThrow(() -> new RuntimeException("家庭不存在"));
        if (!Objects.equals(family.getCreatorId(), operatorUserId)) {
            throw new RuntimeException("仅家庭创建者可移除成员");
        }
        if (Objects.equals(operatorUserId, targetUserId)) {
            throw new RuntimeException("创建者不能移除自己，请先转移创建者或解散家庭");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!Objects.equals(operator.getFamilyId(), target.getFamilyId())) {
            throw new RuntimeException("目标成员不在当前家庭中");
        }

        target.setFamilyId(null);
        userRepository.save(target);
    }

    /** 转移家庭创建者 */
    @Transactional
    public Map<String, Object> transferCreator(Long operatorUserId, Long targetUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new RuntimeException("请选择要转移的成员");
        }

        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("当前用户不存在"));
        Long familyId = operator.getFamilyId();
        if (familyId == null) {
            throw new RuntimeException("尚未加入任何家庭");
        }

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new RuntimeException("家庭不存在"));
        if (!Objects.equals(family.getCreatorId(), operatorUserId)) {
            throw new RuntimeException("仅家庭创建者可转移创建者身份");
        }
        if (Objects.equals(operatorUserId, targetUserId)) {
            throw new RuntimeException("当前成员已经是创建者");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("目标成员不存在"));
        if (!Objects.equals(familyId, target.getFamilyId())) {
            throw new RuntimeException("目标成员不在当前家庭中");
        }

        Long previousCreatorId = family.getCreatorId();
        family.setCreatorId(targetUserId);
        Family savedFamily = familyRepository.saveAndFlush(family);
        userRepository.flush();

        log.info("家庭创建者转移已落库: familyId={}, previousCreatorId={}, newCreatorId={}, operatorFamilyId={}, targetFamilyId={}",
                savedFamily.getId(),
                previousCreatorId,
                savedFamily.getCreatorId(),
                operator.getFamilyId(),
                target.getFamilyId());

        return buildFamilyGovernancePayload(savedFamily, operatorUserId, previousCreatorId, targetUserId, null);
    }


    /** 解散家庭 */
    @Transactional
    public Map<String, Object> dissolveFamily(Long operatorUserId) {
        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("当前用户不存在"));
        Long familyId = operator.getFamilyId();
        if (familyId == null) {
            throw new RuntimeException("尚未加入任何家庭");
        }

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new RuntimeException("家庭不存在"));
        if (!Objects.equals(family.getCreatorId(), operatorUserId)) {
            throw new RuntimeException("仅家庭创建者可解散家庭");
        }

        Long previousCreatorId = family.getCreatorId();
        List<User> members = userRepository.findByFamilyId(familyId);
        int releasedMemberCount = members.size();
        for (User member : members) {
            member.setFamilyId(null);
        }
        userRepository.saveAll(members);
        userRepository.flush();
        familyRepository.delete(family);
        familyRepository.flush();
        log.info("家庭已解散并落库: familyId={}, operatorUserId={}, releasedMemberCount={}", familyId, operatorUserId, releasedMemberCount);

        Map<String, Object> result = new HashMap<>();
        result.put("familyId", familyId);
        result.put("previousCreatorId", previousCreatorId);
        result.put("releasedMemberCount", releasedMemberCount);
        result.put("dissolved", true);
        result.put("currentUserJoined", false);
        return result;
    }


    /** 当前用户主动退出家庭 */
    @Transactional
    public Map<String, Object> leaveFamily(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Long familyId = user.getFamilyId();
        if (familyId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("userId", user.getId());
            result.put("previousFamilyId", null);
            result.put("currentFamilyId", null);
            result.put("creatorId", null);
            result.put("leftFamily", false);
            result.put("currentUserJoined", false);
            return result;
        }

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new RuntimeException("家庭不存在"));
        if (Objects.equals(family.getCreatorId(), userId)) {
            throw new RuntimeException("请先转移创建者或解散家庭后再退出");
        }

        Long previousFamilyId = user.getFamilyId();
        user.setFamilyId(null);
        User savedUser = userRepository.saveAndFlush(user);

        log.info("成员退出家庭已落库: userId={}, previousFamilyId={}, currentFamilyId={}",
                savedUser.getId(), previousFamilyId, savedUser.getFamilyId());

        return buildFamilyGovernancePayload(family, userId, family.getCreatorId(), family.getCreatorId(), savedUser);
    }


    private Map<String, Object> buildCreateFamilyPayload(Family family, Long currentUserId) {
        Map<String, Object> result = new HashMap<>();
        result.put("familyId", family.getId());
        result.put("familyName", family.getName());
        result.put("inviteCode", family.getInviteCode());
        result.put("creatorId", family.getCreatorId());
        result.put("createdAt", family.getCreatedAt());
        result.put("memberCount", 1);
        result.put("currentUserJoined", currentUserId != null);
        result.put("membersSummary", Collections.emptyList());
        return result;
    }

    private Family findExistingFamily(Long familyId) {
        if (familyId == null || familyId <= 0) {
            return null;
        }
        return familyRepository.findById(familyId).orElse(null);
    }

    private void ensureUserRoleForFamily(User user) {
        String currentRole = user.getRole();
        if (currentRole == null || currentRole.trim().isEmpty() || "UNKNOWN".equalsIgnoreCase(currentRole)) {
            user.setRole("FAMILY");
        }
    }

    private Map<String, Object> buildFamilyPayload(Family family, Long currentUserId) {
        List<User> members = userRepository.findByFamilyId(family.getId());
        List<FamilyMemberResponse> memberResponses = buildFamilyMemberResponses(family, members, currentUserId);
        Map<String, Object> result = new HashMap<>();
        result.put("familyId", family.getId());
        result.put("familyName", family.getName());
        result.put("inviteCode", family.getInviteCode());
        result.put("creatorId", family.getCreatorId());
        result.put("createdAt", family.getCreatedAt());
        result.put("memberCount", memberResponses.size());
        result.put("currentUserJoined", currentUserId != null && memberResponses.stream().anyMatch(member -> Boolean.TRUE.equals(member.getJoined())));
        result.put("membersSummary", memberResponses.stream()
                .map(this::toMemberSummary)
                .collect(Collectors.toList()));
        return result;
    }

    private Map<String, Object> buildFamilyGovernancePayload(Family family,
                                                            Long currentUserId,
                                                            Long previousCreatorId,
                                                            Long newCreatorId,
                                                            User savedUser) {
        Map<String, Object> result = buildFamilyPayload(family, currentUserId);
        result.put("previousCreatorId", previousCreatorId);
        result.put("newCreatorId", newCreatorId);
        result.put("leftFamily", savedUser != null);
        if (savedUser != null) {
            result.put("userId", savedUser.getId());
            result.put("previousFamilyId", family == null ? null : family.getId());
            result.put("currentFamilyId", savedUser.getFamilyId());
        } else {
            result.put("currentFamilyId", family == null ? null : family.getId());
        }
        return result;
    }

    private List<FamilyMemberResponse> buildFamilyMemberResponses(Family family, List<User> members, Long currentUserId) {
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        return members.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing((User member) -> !Objects.equals(member.getId(), family.getCreatorId()))
                        .thenComparing(member -> normalizeDisplayName(member.getNickname(), member.getEmail()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(User::getId, Comparator.nullsLast(Long::compareTo)))
                .map(user -> buildFamilyMemberResponse(user, family, currentUserId))
                .collect(Collectors.toList());
    }

    private FamilyMemberResponse buildFamilyMemberResponse(User user, Family family, Long currentUserId) {
        boolean isCreator = Objects.equals(user.getId(), family.getCreatorId());
        boolean isCurrentUser = currentUserId != null && Objects.equals(user.getId(), currentUserId);
        String displayName = normalizeDisplayName(user.getNickname(), user.getEmail());
        String avatarUrl = sanitizeAvatarUrl(user.getAvatarUrl());

        return FamilyMemberResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .displayName(displayName)
                .role(user.getRole())
                .roleLabel(resolveRoleLabel(user.getRole()))
                .avatarUrl(avatarUrl)
                .avatarShared(avatarUrl != null && !avatarUrl.isBlank())
                .emergencyContactName(user.getEmergencyContactName())
                .emergencyContactPhone(user.getEmergencyContactPhone())
                .familyId(family.getId())
                .familyName(family.getName())
                .creatorId(family.getCreatorId())
                .creator(isCreator)
                .currentUser(isCurrentUser)
                .joined(true)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private Map<String, Object> toMemberSummary(FamilyMemberResponse member) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", member.getUserId());
        map.put("nickname", member.getNickname());
        map.put("displayName", member.getDisplayName());
        map.put("avatarUrl", member.getAvatarUrl());
        map.put("role", member.getRole());
        map.put("roleLabel", member.getRoleLabel());
        map.put("familyId", member.getFamilyId());
        map.put("creatorId", member.getCreatorId());
        map.put("isCreator", member.getCreator());
        map.put("isSelf", member.getCurrentUser());
        map.put("joined", member.getJoined());
        return map;
    }

    private String normalizeDisplayName(String nickname, String email) {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname.trim();
        }
        if (email != null && !email.trim().isEmpty()) {
            String normalizedEmail = email.trim();
            int atIndex = normalizedEmail.indexOf('@');
            return atIndex > 0 ? normalizedEmail.substring(0, atIndex) : normalizedEmail;
        }
        return "未设置昵称";
    }

    private String sanitizeAvatarUrl(String avatarUrl) {
        if (avatarUrl == null) {
            return null;
        }
        String normalized = avatarUrl.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("content://") || lower.startsWith("file://") || lower.matches("^[a-z]:\\\\.*")) {
            return null;
        }
        return normalized;
    }

    private String resolveRoleLabel(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "角色未设置";
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if ("ELDER".equals(normalized)) {
            return "老人端成员";
        }
        if ("FAMILY".equals(normalized)) {
            return "家属端成员";
        }
        return normalized;
    }

    private String normalizeFamilyName(String familyName) {


        if (familyName == null) {
            throw new RuntimeException("请输入家庭名称");
        }
        String normalized = familyName.trim();
        if (normalized.isEmpty()) {
            throw new RuntimeException("请输入家庭名称");
        }
        if (normalized.length() > 20) {
            throw new RuntimeException("家庭名称最多 20 个字");
        }
        return normalized;
    }

    private String normalizeInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new RuntimeException("请输入邀请码");
        }
        String normalized = inviteCode.trim();
        if (!normalized.matches("\\d{6}")) {
            throw new RuntimeException("邀请码为6位数字");
        }
        return normalized;
    }

    /** 生成6位不重复邀请码 */
    private String generateInviteCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }
}



