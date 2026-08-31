package com.carelink.app.ui.family;

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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.carelink.app.R;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.repository.FamilyRepository;
import com.carelink.app.utils.FontScaleHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MemberFragment extends Fragment {

    private static final String ARG_READ_ONLY = "arg_read_only";

    @Inject
    FamilyRepository familyRepository;

    private PreferenceManager preferenceManager;
    private boolean readOnlyMode;
    private LinearLayout memberListContainer;
    private ProgressBar progressBar;
    private TextView tvFamilySummary;
    private TextView tvEmpty;
    private TextView tvCreatorTip;
    private Button refreshButton;

    public static MemberFragment newInstance(boolean readOnlyMode) {
        MemberFragment fragment = new MemberFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_READ_ONLY, readOnlyMode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        readOnlyMode = args != null && args.getBoolean(ARG_READ_ONLY, false);
    }

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
        root.addView(createActionCard(bodySize));
        root.addView(createMemberListCard(bodySize));

        loadMembers();
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (memberListContainer != null) {
            loadMembers();
        }
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

        TextView title = new TextView(requireContext());
        title.setText("家庭成员");
        title.setTextSize(titleSize + 1);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getColor(R.color.white));
        hero.addView(title);

        tvFamilySummary = new TextView(requireContext());
        tvFamilySummary.setTextSize(bodySize);
        tvFamilySummary.setTextColor(getColor(R.color.white_80));
        tvFamilySummary.setPadding(0, 10, 0, 0);
        hero.addView(tvFamilySummary);

        tvCreatorTip = new TextView(requireContext());
        tvCreatorTip.setTextSize(FontScaleHelper.secondary(requireContext()));
        tvCreatorTip.setTextColor(getColor(R.color.white_80));
        tvCreatorTip.setPadding(0, 10, 0, 0);
        hero.addView(tvCreatorTip);
        return hero;
    }

    private View createActionCard(int bodySize) {
        LinearLayout card = verticalCard();
        card.addView(createSectionTitle(readOnlyMode ? "家庭信息" : "家庭操作", bodySize + 2));

        TextView desc = new TextView(requireContext());
        desc.setText(readOnlyMode
                ? "仅支持查看家庭成员信息，不可删除或修改。"
                : "可在这里查看成员信息、刷新家庭状态，创建者可以移除成员。 ");
        desc.setTextSize(bodySize);
        desc.setTextColor(getColor(R.color.text_secondary));
        desc.setPadding(0, 6, 0, 16);
        card.addView(desc);

        refreshButton = createPrimaryButton("刷新成员列表", bodySize, v -> loadMembers());
        card.addView(refreshButton);

        Button leaveButton = createActionButton("退出当前家庭", bodySize, true,
                v -> confirmLeaveFamily());
        card.addView(leaveButton);

        if (!readOnlyMode) {
            Button bindButton = createActionButton("家庭绑定与邀请码", bodySize, false,
                    v -> startActivity(new Intent(requireContext(), JoinFamilyActivity.class)));
            card.addView(bindButton);
        }
        return card;
    }

    private View createMemberListCard(int bodySize) {
        LinearLayout card = verticalCard();
        card.addView(createSectionTitle("成员列表", bodySize + 2));

        progressBar = new ProgressBar(requireContext());
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = 16;
        progressParams.bottomMargin = 16;
        progressBar.setLayoutParams(progressParams);
        card.addView(progressBar);

        tvEmpty = new TextView(requireContext());
        tvEmpty.setText("暂无成员数据");
        tvEmpty.setTextSize(bodySize);
        tvEmpty.setTextColor(getColor(R.color.text_secondary));
        tvEmpty.setVisibility(View.GONE);
        card.addView(tvEmpty);

        memberListContainer = new LinearLayout(requireContext());
        memberListContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(memberListContainer);
        return card;
    }

    private void loadMembers() {
        setLoading(true);
        familyRepository.getMembers(new FamilyRepository.ResultCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (!isAdded()) {
                    return;
                }
                renderMembers(data);
                setLoading(false);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                renderError(message);
                setLoading(false);
            }
        });
    }

    private void renderMembers(List<Map<String, Object>> data) {
        List<Map<String, Object>> members = data == null ? new ArrayList<>() : data;
        memberListContainer.removeAllViews();

        long currentUserId = preferenceManager.getUserId();
        long creatorId = extractCreatorId(members);
        boolean isCreator = !readOnlyMode && isCurrentUserCreator(members, currentUserId, creatorId);

        String familyName = safeText(preferenceManager.getFamilyName(), "当前家庭");
        String inviteCode = safeText(preferenceManager.getInviteCode(), "暂无邀请码");
        tvFamilySummary.setText(familyName + " · 邀请码 " + inviteCode + " · 共 " + members.size() + " 人");
        if (readOnlyMode) {
            tvCreatorTip.setText("当前为只读模式，仅可查看成员信息。");
        } else {
            tvCreatorTip.setText(isCreator ? "你是创建者，可移除成员、转移创建者或解散家庭" : "仅创建者可管理家庭成员");
        }

        if (members.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("当前家庭还没有可展示的成员");
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        if (isCreator) {
            memberListContainer.addView(createCreatorActionCard(members, currentUserId, creatorId));
        }

        for (Map<String, Object> member : members) {
            memberListContainer.addView(createMemberCard(member, currentUserId, creatorId, isCreator));
        }
    }


    private void renderError(String message) {
        memberListContainer.removeAllViews();
        tvFamilySummary.setText("家庭成员");
        tvCreatorTip.setText("");
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText(message == null || message.trim().isEmpty() ? "成员信息加载失败，请稍后再试" : message.trim());
    }

    private View createCreatorActionCard(List<Map<String, Object>> members, long currentUserId, long creatorId) {
        LinearLayout card = verticalCard();
        card.addView(createSectionTitle("创建者操作", FontScaleHelper.body(requireContext()) + 2));

        TextView desc = new TextView(requireContext());
        desc.setText("你可以把创建者身份转交给其他家庭成员，或在确认后直接解散当前家庭。 ");
        desc.setTextSize(FontScaleHelper.body(requireContext()));
        desc.setTextColor(getColor(R.color.text_secondary));
        desc.setPadding(0, 6, 0, 16);
        card.addView(desc);

        Button transferButton = createActionButton("转移创建者", FontScaleHelper.body(requireContext()), false,
                v -> showTransferCreatorDialog(members, currentUserId, creatorId));
        card.addView(transferButton);

        Button dissolveButton = createActionButton("解散家庭", FontScaleHelper.body(requireContext()), true,
                v -> confirmDissolveFamily());
        card.addView(dissolveButton);
        return card;
    }

    private void showTransferCreatorDialog(List<Map<String, Object>> members, long currentUserId, long creatorId) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Map<String, Object> member : members) {
            long memberUserId = extractLong(member.get("userId"));
            if (memberUserId <= 0 || memberUserId == currentUserId || memberUserId == creatorId) {
                continue;
            }
            candidates.add(member);
            labels.add(safeText(member.get("nickname"), "未设置昵称") + " · " + buildRoleLabel(member.get("role")));
        }

        if (candidates.isEmpty()) {
            Toast.makeText(requireContext(), "当前没有可转移的家庭成员", Toast.LENGTH_LONG).show();
            return;
        }

        String[] items = labels.toArray(new String[0]);
        new AlertDialog.Builder(requireContext())
                .setTitle("选择新的创建者")
                .setItems(items, (dialog, which) -> {
                    Map<String, Object> target = candidates.get(which);
                    long targetUserId = extractLong(target.get("userId"));
                    String nickname = safeText(target.get("nickname"), "该成员");
                    confirmTransferCreator(targetUserId, nickname);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmTransferCreator(long targetUserId, String nickname) {
        new AlertDialog.Builder(requireContext())
                .setTitle("转移创建者")
                .setMessage("确定将家庭创建者转移给“" + nickname + "”吗？")
                .setPositiveButton("确定", (dialog, which) -> transferCreator(targetUserId))
                .setNegativeButton("取消", null)
                .show();
    }

    private void transferCreator(long targetUserId) {
        setLoading(true);
        familyRepository.transferCreator(targetUserId, new FamilyRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                Toast.makeText(requireContext(), "创建者已转移，你现在已是普通成员，可前往“我的”页退出家庭并切换身份", Toast.LENGTH_LONG).show();
                loadMembers();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                Toast.makeText(requireContext(), message == null || message.trim().isEmpty() ? "转移失败，请稍后重试" : message.trim(), Toast.LENGTH_LONG).show();
            }
        });
    }


    private void confirmDissolveFamily() {
        new AlertDialog.Builder(requireContext())
                .setTitle("解散家庭")
                .setMessage("解散后当前家庭成员都会被移出家庭，此操作不可恢复，是否继续？")
                .setPositiveButton("确定解散", (dialog, which) -> dissolveFamily())
                .setNegativeButton("取消", null)
                .show();
    }

    private void dissolveFamily() {
        setLoading(true);
        familyRepository.dissolveFamily(new FamilyRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                // 清理本地缓存的家庭信息，避免解散后仍显示旧的邀请码
                preferenceManager.clearFamilyState();
                Toast.makeText(requireContext(), "家庭已解散，成员状态已刷新", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(requireContext(), JoinFamilyActivity.class));
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                String errorText = message == null ? "" : message.trim();
                if (errorText.isEmpty() || "网络异常，请稍后重试".equals(errorText) || "操作失败，请稍后重试".equals(errorText)) {
                    errorText = "解散家庭失败，请稍后重试";
                }
                Toast.makeText(requireContext(), errorText, Toast.LENGTH_LONG).show();
            }
        });
    }



    private void confirmLeaveFamily() {
        if (preferenceManager == null || preferenceManager.getFamilyId() <= 0) {
            Toast.makeText(requireContext(), "当前未加入家庭", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(requireContext(), JoinFamilyActivity.class));
            if (getActivity() != null) {
                getActivity().finish();
            }
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("退出当前家庭")
                .setMessage("退出后需要重新加入家庭才能继续使用家庭协作功能。若你是创建者，请先转移创建者或解散家庭。是否继续？")
                .setPositiveButton("确定退出", (dialog, which) -> leaveFamily())
                .setNegativeButton("取消", null)
                .show();
    }

    private void leaveFamily() {
        setLoading(true);
        familyRepository.leaveFamily(new FamilyRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                preferenceManager.clearFamilyState();
                Toast.makeText(requireContext(), "已退出当前家庭", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(requireContext(), JoinFamilyActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                String errorText = message == null ? "" : message.trim();
                if (errorText.isEmpty()) {
                    errorText = "退出家庭失败，请稍后重试";
                }
                Toast.makeText(requireContext(), errorText, Toast.LENGTH_LONG).show();
            }
        });
    }

    private View createMemberCard(Map<String, Object> member, long currentUserId, long creatorId, boolean isCreator) {
        LinearLayout card = verticalCard();

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header);

        ImageView avatar = new ImageView(requireContext());
        avatar.setImageResource(R.drawable.ic_my);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(132, 132);
        avatarParams.rightMargin = 20;
        avatar.setLayoutParams(avatarParams);
        header.addView(avatar);
        loadAvatar(avatar, member.get("avatarUrl"));

        LinearLayout infoColumn = new LinearLayout(requireContext());
        infoColumn.setOrientation(LinearLayout.VERTICAL);
        infoColumn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(infoColumn);

        TextView nickname = new TextView(requireContext());
        nickname.setText(resolveMemberDisplayName(member));
        nickname.setTextSize(FontScaleHelper.body(requireContext()) + 2);
        nickname.setTypeface(Typeface.DEFAULT_BOLD);
        nickname.setTextColor(getColor(R.color.text_primary));
        infoColumn.addView(nickname);

        TextView roleView = new TextView(requireContext());
        roleView.setText(buildRoleLabel(member.get("role")));
        roleView.setTextSize(FontScaleHelper.secondary(requireContext()));
        roleView.setTextColor(getColor(R.color.brand_blue));
        roleView.setPadding(0, 8, 0, 0);
        infoColumn.addView(roleView);

        TextView contactView = new TextView(requireContext());
        contactView.setText(buildContactText(member));
        contactView.setTextSize(FontScaleHelper.secondary(requireContext()));
        contactView.setTextColor(getColor(R.color.text_secondary));
        contactView.setPadding(0, 8, 0, 0);
        infoColumn.addView(contactView);

        long memberUserId = extractLong(member.get("userId"));
        boolean memberIsCreator = isMemberCreator(member, creatorId);
        boolean memberIsSelf = isMemberSelf(member, currentUserId);
        if (memberIsCreator) {
            TextView creatorBadge = buildInlineBadge("创建者");
            header.addView(creatorBadge);
        } else if (memberIsSelf) {
            TextView selfBadge = buildInlineBadge("我");
            header.addView(selfBadge);
        }

        if (isCreator && memberUserId > 0 && !memberIsCreator) {
            Button removeButton = createActionButton("移除成员", FontScaleHelper.body(requireContext()), true,
                    v -> confirmRemoveMember(memberUserId, resolveMemberDisplayName(member)));
            card.addView(removeButton);
        }
        return card;
    }

    private TextView buildInlineBadge(String text) {
        TextView badge = new TextView(requireContext());
        badge.setText(text);
        badge.setTextSize(FontScaleHelper.secondary(requireContext()));
        badge.setTextColor(getColor(R.color.white));
        badge.setGravity(Gravity.CENTER);
        int paddingH = dip2px(12);
        int paddingV = dip2px(4);
        badge.setPadding(paddingH, paddingV, paddingH, paddingV);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.brand_blue));
        bg.setCornerRadius(dip2px(12));
        badge.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dip2px(8);
        badge.setLayoutParams(params);
        return badge;
    }

    private int dip2px(float dp) {
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }


    private void confirmRemoveMember(long userId, String nickname) {
        new AlertDialog.Builder(requireContext())
                .setTitle("移除成员")
                .setMessage("确定将“" + nickname + "”移出当前家庭吗？")
                .setPositiveButton("确定", (dialog, which) -> removeMember(userId))
                .setNegativeButton("取消", null)
                .show();
    }

    private void removeMember(long userId) {
        setLoading(true);
        familyRepository.removeMember(userId, new FamilyRepository.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "成员已移除", Toast.LENGTH_SHORT).show();
                loadMembers();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                Toast.makeText(requireContext(), message == null || message.trim().isEmpty() ? "移除失败，请稍后重试" : message.trim(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (refreshButton != null) {
            refreshButton.setEnabled(!loading);
        }
    }

    private void loadAvatar(ImageView view, Object avatarUrlValue) {
        String avatarUrl = safeText(avatarUrlValue, "");
        if (avatarUrl.isEmpty()) {
            view.setImageResource(R.drawable.ic_my);
            return;
        }
        try {
            Glide.with(this)
                    .load(Uri.parse(avatarUrl))
                    .placeholder(R.drawable.ic_my)
                    .error(R.drawable.ic_my)
                    .circleCrop()
                    .into(view);
        } catch (Exception ignored) {
            view.setImageResource(R.drawable.ic_my);
        }
    }

    private String buildRoleLabel(Object roleValue) {
        String role = safeText(roleValue, "").toUpperCase(Locale.ROOT);
        if (Objects.equals(role, "ELDER")) {
            return "老人端成员";
        }
        if (Objects.equals(role, "FAMILY")) {
            return "家属端成员";
        }
        return "角色未设置";
    }

    private String buildContactText(Map<String, Object> member) {
        String contactName = safeText(member.get("emergencyContactName"), "未设置紧急联系人");
        String contactPhone = safeText(member.get("emergencyContactPhone"), "");
        return contactPhone.isEmpty() ? contactName : contactName + " · " + contactPhone;
    }

    private boolean isCurrentUserCreator(List<Map<String, Object>> members, long currentUserId, long creatorId) {
        if (members == null || members.isEmpty() || currentUserId <= 0) {
            return false;
        }
        for (Map<String, Object> member : members) {
            if (member == null) {
                continue;
            }
            if (isMemberSelf(member, currentUserId)) {
                return isMemberCreator(member, creatorId);
            }
        }
        return creatorId > 0 && currentUserId == creatorId;
    }

    private boolean isMemberCreator(Map<String, Object> member, long fallbackCreatorId) {
        if (member == null) {
            return false;
        }
        Object creatorValue = member.get("creator");
        if (creatorValue instanceof Boolean) {
            return (Boolean) creatorValue;
        }
        creatorValue = member.get("isCreator");
        if (creatorValue instanceof Boolean) {
            return (Boolean) creatorValue;
        }
        long memberUserId = extractLong(member.get("userId"));
        long creatorId = extractLong(member.get("creatorId"));
        if (creatorId <= 0) {
            creatorId = fallbackCreatorId;
        }
        return memberUserId > 0 && creatorId > 0 && memberUserId == creatorId;
    }

    private boolean isMemberSelf(Map<String, Object> member, long currentUserId) {
        if (member == null || currentUserId <= 0) {
            return false;
        }
        Object selfValue = member.get("currentUser");
        if (selfValue instanceof Boolean) {
            return (Boolean) selfValue;
        }
        selfValue = member.get("isSelf");
        if (selfValue instanceof Boolean) {
            return (Boolean) selfValue;
        }
        return extractLong(member.get("userId")) == currentUserId;
    }

    private String resolveMemberDisplayName(Map<String, Object> member) {
        if (member == null) {
            return "未设置昵称";
        }
        String displayName = safeText(member.get("displayName"), "");
        if (!displayName.isEmpty()) {
            return displayName;
        }
        return safeText(member.get("nickname"), "未设置昵称");
    }


    private long extractCreatorId(List<Map<String, Object>> members) {
        for (Map<String, Object> member : members) {
            if (member == null) {
                continue;
            }
            long creatorId = extractLong(member.get("creatorId"));
            if (creatorId > 0) {
                return creatorId;
            }
        }
        return -1L;
    }



    private String safeText(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private long extractLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return -1L;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (Exception ignored) {
            return -1L;
        }
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
