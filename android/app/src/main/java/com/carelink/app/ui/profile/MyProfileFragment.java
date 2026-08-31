package com.carelink.app.ui.profile;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.carelink.app.R;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.remote.dto.LoginResponse;
import com.carelink.app.data.repository.AuthRepository;
import com.carelink.app.data.repository.FamilyRepository;
import com.carelink.app.ui.auth.LoginActivity;
import com.carelink.app.ui.auth.RoleSelectActivity;
import com.carelink.app.ui.elder.ElderFamilyMembersActivity;
import com.carelink.app.ui.family.FamilyMainActivity;
import com.carelink.app.ui.family.JoinFamilyActivity;
import com.carelink.app.ui.settings.SettingsActivity;
import com.carelink.app.utils.FontScaleHelper;

import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MyProfileFragment extends Fragment {

    @Inject
    AuthRepository authRepository;

    @Inject
    FamilyRepository familyRepository;

    private PreferenceManager preferenceManager;
    private ImageView avatarView;
    private TextView tvNickname;
    private TextView tvFamilyInfo;
    private TextView tvProfileSummary;

    private final ActivityResultLauncher<String> avatarPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }
                authRepository.uploadAvatar(uri, new AuthRepository.ResultCallback<String>() {
                    @Override
                    public void onSuccess(String avatarUrl) {
                        if (!isAdded()) {
                            return;
                        }
                        String fallbackNickname = preferenceManager == null ? "" : preferenceManager.getNickname();
                        applyProfileResult(null, fallbackNickname, avatarUrl);
                        bindProfileSummary();
                        Toast.makeText(requireContext(), "头像已上传到服务器并更新", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(
                                requireContext(),
                                message == null || message.trim().isEmpty() ? "头像上传失败，请稍后重试" : message.trim(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        preferenceManager = new PreferenceManager(requireContext());
        int titleSize = FontScaleHelper.title(requireContext());
        int bodySize = FontScaleHelper.body(requireContext());

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getColor(R.color.surface_page));

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 40);
        scrollView.addView(root);

        root.addView(createHeroCard(titleSize, bodySize));
        root.addView(createProfileCard(bodySize));
        root.addView(createQuickActionSection(bodySize));
        root.addView(createSettingEntryCard(bodySize));
        root.addView(createDangerZone(bodySize));

        bindProfileSummary();
        syncFamilyInfo(false);
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        bindProfileSummary();
        syncFamilyInfo(true);
    }

    private View createHeroCard(int titleSize, int bodySize) {
        LinearLayout hero = new LinearLayout(requireContext());
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(30, 30, 30, 30);
        hero.setBackground(createRoundedBg(getColor(R.color.brand_blue), 34));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 18;
        hero.setLayoutParams(params);

        TextView eyebrow = new TextView(requireContext());
        eyebrow.setText("账户中心");
        eyebrow.setTextSize(FontScaleHelper.secondary(requireContext()));
        eyebrow.setTextColor(getColor(R.color.white_80));
        hero.addView(eyebrow);

        TextView title = new TextView(requireContext());
        title.setText("我的");
        title.setTextSize(titleSize + 2);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getColor(R.color.white));
        title.setPadding(0, 8, 0, 8);
        hero.addView(title);

        tvProfileSummary = new TextView(requireContext());
        tvProfileSummary.setTextSize(bodySize);
        tvProfileSummary.setTextColor(getColor(R.color.white_80));
        hero.addView(tvProfileSummary);
        return hero;
    }

    private View createProfileCard(int bodySize) {
        LinearLayout card = verticalCard();

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header);

        avatarView = new ImageView(requireContext());
        avatarView.setImageResource(R.drawable.ic_my);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(160, 160);
        avatarParams.rightMargin = 24;
        avatarView.setLayoutParams(avatarParams);
        header.addView(avatarView);
        safeSetAvatar(preferenceManager.getAvatarUrl());

        LinearLayout infoColumn = new LinearLayout(requireContext());
        infoColumn.setOrientation(LinearLayout.VERTICAL);
        infoColumn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(infoColumn);

        TextView roleBadge = new TextView(requireContext());
        roleBadge.setText(getRoleLabel() + "账户");
        roleBadge.setTextSize(FontScaleHelper.secondary(requireContext()));
        roleBadge.setTextColor(getColor(R.color.brand_blue));
        roleBadge.setPadding(20, 10, 20, 10);
        roleBadge.setBackground(createRoundedBg(getColor(R.color.surface_soft_blue), 20));
        infoColumn.addView(roleBadge);

        tvNickname = new TextView(requireContext());
        tvNickname.setTextSize(bodySize + 4);
        tvNickname.setTypeface(Typeface.DEFAULT_BOLD);
        tvNickname.setTextColor(getColor(R.color.text_primary));
        tvNickname.setPadding(0, 14, 0, 8);
        infoColumn.addView(tvNickname);

        TextView tvEmail = new TextView(requireContext());
        tvEmail.setText("邮箱 · " + preferenceManager.getEmail());
        tvEmail.setTextSize(bodySize);
        tvEmail.setTextColor(getColor(R.color.text_secondary));
        infoColumn.addView(tvEmail);

        tvFamilyInfo = new TextView(requireContext());
        tvFamilyInfo.setTextSize(bodySize);
        tvFamilyInfo.setTextColor(getColor(R.color.text_secondary));
        tvFamilyInfo.setPadding(0, 12, 0, 0);
        infoColumn.addView(tvFamilyInfo);

        card.addView(createSectionTitle("资料管理", bodySize + 2));
        card.addView(createActionButton("上传头像", bodySize, false, v -> avatarPickerLauncher.launch("image/*")));
        card.addView(createActionButton("修改昵称", bodySize, false, v -> showEditNicknameDialog()));
        return card;
    }

    private View createQuickActionSection(int bodySize) {
        LinearLayout card = verticalCard();
        card.addView(createSectionTitle("快捷操作", bodySize + 2));
        card.addView(createActionButton("家庭管理", bodySize, false, v -> openFamilyManagePage()));
        card.addView(createActionButton("切换身份", bodySize, false, v -> handleSwitchRole()));
        return card;
    }

    private View createSettingEntryCard(int bodySize) {
        LinearLayout card = verticalCard();
        card.addView(createSectionTitle("偏好与外观", bodySize + 2));

        TextView desc = new TextView(requireContext());
        desc.setText("统一管理字体、语音、提醒、家庭与安全等设置。");
        desc.setTextSize(bodySize);
        desc.setTextColor(getColor(R.color.text_secondary));
        desc.setPadding(0, 6, 0, 16);
        card.addView(desc);

        card.addView(createPrimaryButton("进入设置", bodySize, v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class))));
        return card;
    }

    private View createDangerZone(int bodySize) {
        LinearLayout card = verticalCard();
        card.addView(createSectionTitle("账号安全", bodySize + 2));

        TextView desc = new TextView(requireContext());
        desc.setText("涉及身份切换与退出登录，请在确认信息已保存后操作。");
        desc.setTextSize(bodySize);
        desc.setTextColor(getColor(R.color.text_secondary));
        desc.setPadding(0, 6, 0, 16);
        card.addView(desc);

        card.addView(createActionButton("退出登录", bodySize, true, v -> handleLogout()));
        return card;
    }

    private LinearLayout verticalCard() {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        card.setBackground(createRoundedBg(getColor(R.color.surface_card), 28));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 18;
        card.setLayoutParams(params);
        return card;
    }

    private TextView createSectionTitle(String text, int size) {
        TextView title = new TextView(requireContext());
        title.setText(text);
        title.setTextSize(size);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getColor(R.color.text_primary));
        title.setPadding(0, 0, 0, 14);
        return title;
    }

    private Button createPrimaryButton(String text, int fontSize, View.OnClickListener listener) {
        Button button = createBaseButton(text, fontSize, listener);
        button.setTextColor(getColor(R.color.white));
        button.setBackground(createRoundedBg(getColor(R.color.brand_blue), 22));
        return button;
    }

    private Button createActionButton(String text, int fontSize, boolean danger, View.OnClickListener listener) {
        Button button = createBaseButton(text, fontSize, listener);
        button.setTextColor(getColor(danger ? R.color.emergency_red : R.color.text_primary));
        button.setBackground(createRoundedBg(getColor(danger ? R.color.surface_soft_gray : R.color.action_neutral), 22));
        return button;
    }

    private Button createBaseButton(String text, int fontSize, View.OnClickListener listener) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setTextSize(fontSize);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 12;
        button.setLayoutParams(params);
        return button;
    }

    private void bindProfileSummary() {
        if (tvProfileSummary != null) {
            tvProfileSummary.setText(getNickname() + " · " + getRoleLabel() + "\n" + getFamilySummaryLineSafe());
        }
        if (tvNickname != null) {
            tvNickname.setText(getNickname());
        }
        if (tvFamilyInfo != null) {
            tvFamilyInfo.setText(getFamilyTextSafe());
        }
    }

    private void safeSetAvatar(String avatarUrl) {
        if (avatarView == null) {
            return;
        }
        try {
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Glide.with(this)
                        .load(Uri.parse(avatarUrl))
                        .placeholder(R.drawable.ic_my)
                        .error(R.drawable.ic_my)
                        .circleCrop()
                        .into(avatarView);
            } else {
                avatarView.setImageResource(R.drawable.ic_my);
            }
        } catch (Exception e) {
            avatarView.setImageResource(R.drawable.ic_my);
        }
    }

    private String getNickname() {
        String nick = preferenceManager.getNickname();
        return nick == null || nick.trim().isEmpty() ? "未设置昵称" : nick;
    }

    private String getRoleLabel() {
        return "ELDER".equals(preferenceManager.getRole()) ? "老人端" : "家属端";
    }

    private String getFamilySummaryLine() {
        return preferenceManager.getFamilyId() > 0 ? "已加入家庭 · 可查看邀请码" : "暂未加入家庭";
    }

    private String getFamilyText() {
        long familyId = preferenceManager.getFamilyId();
        if (familyId > 0) {
            return "家庭名称 · " + preferenceManager.getFamilyName() + "\n邀请码 · " + preferenceManager.getInviteCode();
        }
        return "家庭信息 · 未加入家庭";
    }

    private String getFamilySummaryLineSafe() {
        long familyId = preferenceManager.getFamilyId();
        if (familyId <= 0) {
            return "家庭状态 · 未加入家庭";
        }
        String familyName = safeText(preferenceManager.getFamilyName());
        String inviteCode = safeText(preferenceManager.getInviteCode());
        if (!familyName.isEmpty() && !inviteCode.isEmpty()) {
            return "家庭状态 · 已加入家庭";
        }
        return "家庭状态 · 已加入（信息同步中）";
    }

    private String getFamilyTextSafe() {
        long familyId = preferenceManager.getFamilyId();
        if (familyId > 0) {
            String familyName = displayOrFallback(preferenceManager.getFamilyName(), "未同步");
            String inviteCode = displayOrFallback(preferenceManager.getInviteCode(), "未同步");
            return "家庭名称 · " + familyName + "\n邀请码 · " + inviteCode;
        }
        return "家庭信息 · 未加入家庭";
    }

    private void syncFamilyInfo(boolean force) {
        if (preferenceManager == null) {
            return;
        }
        long familyId = preferenceManager.getFamilyId();
        if (familyId <= 0) {
            return;
        }
        if (!force
                && !safeText(preferenceManager.getFamilyName()).isEmpty()
                && !safeText(preferenceManager.getInviteCode()).isEmpty()) {
            return;
        }
        familyRepository.getFamilyInfo(familyId, new FamilyRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                if (!isAdded()) {
                    return;
                }
                bindProfileSummary();
            }

            @Override
            public void onError(String message) {
                // Keep local fallback display when server sync fails.
            }
        });
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String displayOrFallback(String value, String fallback) {
        String normalized = safeText(value);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private void showEditNicknameDialog() {
        EditText editText = new EditText(requireContext());
        editText.setHint("请输入新昵称");
        editText.setText(preferenceManager.getNickname());
        new AlertDialog.Builder(requireContext())
                .setTitle("修改昵称")
                .setView(editText)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newNickname = editText.getText().toString().trim();
                    if (!newNickname.isEmpty()) {
                        updateProfile(newNickname, null, "昵称已更新");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void handleSwitchRole() {
        new AlertDialog.Builder(requireContext())
                .setTitle("切换身份")
                .setMessage("切换身份后会自动退出当前家庭，是否继续？")
                .setPositiveButton("确定", (dialog, which) -> leaveFamilyAndSwitchRole())
                .setNegativeButton("取消", null)
                .show();
    }

    private void leaveFamilyAndSwitchRole() {
        if (preferenceManager.getFamilyId() <= 0) {
            navigateToRoleSelectAfterSwitch();
            return;
        }
        familyRepository.leaveFamily(new FamilyRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "已退出当前家庭，请重新选择身份", Toast.LENGTH_SHORT).show();
                navigateToRoleSelectAfterSwitch();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                if (shouldContinueSwitchRoleWhenLeaveFailed(message)) {
                    Toast.makeText(requireContext(), "家庭状态已失效，已继续切换身份", Toast.LENGTH_SHORT).show();
                    navigateToRoleSelectAfterSwitch();
                    return;
                }
                Toast.makeText(
                        requireContext(),
                        message == null || message.trim().isEmpty() ? "退出家庭失败，请稍后重试" : message.trim(),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void navigateToRoleSelectAfterSwitch() {
        preferenceManager.clearFamilyState();
        preferenceManager.clearRoleState();
        Intent intent = new Intent(requireContext(), RoleSelectActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void openFamilyManagePage() {
        if (preferenceManager.getFamilyId() > 0) {
            if ("ELDER".equalsIgnoreCase(preferenceManager.getRole())) {
                startActivity(new Intent(requireContext(), ElderFamilyMembersActivity.class));
            } else {
                startActivity(new Intent(requireContext(), FamilyMainActivity.class).putExtra("open_page", "members"));
            }
            return;
        }
        startActivity(new Intent(requireContext(), JoinFamilyActivity.class));
    }

    private boolean shouldContinueSwitchRoleWhenLeaveFailed(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.contains("网络")
                || normalized.contains("超时")
                || normalized.contains("timeout")
                || normalized.contains("network")
                || normalized.contains("连接")) {
            return false;
        }
        return normalized.contains("未加入家庭")
                || normalized.contains("不在家庭")
                || normalized.contains("已退出家庭")
                || normalized.contains("家庭不存在")
                || normalized.contains("成员不存在")
                || normalized.contains("already")
                || normalized.contains("not in family")
                || normalized.contains("no family")
                || normalized.contains("not found");
    }

    private void updateProfile(String nickname, String avatarUrl, String successMessage) {
        String resolvedNickname = nickname != null ? nickname.trim() : preferenceManager.getNickname();
        if (resolvedNickname == null || resolvedNickname.trim().isEmpty()) {
            String email = preferenceManager.getEmail();
            if (email != null && email.contains("@")) {
                resolvedNickname = email.substring(0, email.indexOf('@'));
            } else {
                resolvedNickname = "用户" + preferenceManager.getUserId();
            }
        }
        String resolvedAvatarUrl = avatarUrl != null ? avatarUrl : preferenceManager.getAvatarUrl();

        final String finalNickname = resolvedNickname;
        final String finalAvatarUrl = resolvedAvatarUrl;
        authRepository.updateProfile(finalNickname, finalAvatarUrl, new AuthRepository.ResultCallback<LoginResponse>() {
            @Override
            public void onSuccess(LoginResponse data) {
                if (!isAdded()) {
                    return;
                }
                applyProfileResult(data, finalNickname, finalAvatarUrl);
                bindProfileSummary();
                Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(
                        requireContext(),
                        message == null || message.trim().isEmpty() ? "资料更新失败，请稍后重试" : message.trim(),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void applyProfileResult(LoginResponse data, String fallbackNickname, String fallbackAvatarUrl) {
        String nickname = data != null && data.getNickname() != null && !data.getNickname().trim().isEmpty()
                ? data.getNickname().trim() : fallbackNickname;
        String avatarUrl = data != null && data.getAvatarUrl() != null && !data.getAvatarUrl().trim().isEmpty()
                ? data.getAvatarUrl().trim() : fallbackAvatarUrl;

        preferenceManager.saveNickname(nickname == null ? "" : nickname);
        preferenceManager.saveAvatarUrl(avatarUrl == null ? "" : avatarUrl);
        safeSetAvatar(avatarUrl);
    }

    private void handleLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    preferenceManager.clear();
                    Intent intent = new Intent(requireContext(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private GradientDrawable createRoundedBg(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int getColor(int colorRes) {
        return ContextCompat.getColor(requireContext(), colorRes);
    }
}
