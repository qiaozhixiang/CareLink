package com.carelink.app.ui.family;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.repository.AlertRepository;
import com.carelink.app.data.repository.HealthRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class DashboardFragment extends Fragment {

    @Inject
    AlertRepository alertRepository;
    @Inject
    HealthRepository healthRepository;

    private TextView pendingAlertBody;
    private TextView healthSharedBody;
    private Button clearPendingButton;

    private final List<Map<String, Object>> cachedPendingAlerts = new ArrayList<>();
    private boolean clearingPendingAlerts = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        PreferenceManager preferenceManager = new PreferenceManager(requireContext());

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFFF7F5F0);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(88));
        scrollView.addView(root);

        root.addView(createHeroCard(preferenceManager));
        root.addView(createPendingAssistCard());
        root.addView(createHealthSharedCard());

        String status = safe(preferenceManager.getShareStatus(), "未开启");
        String location = safe(preferenceManager.getShareLastLocation(), "暂无");
        String update = safe(preferenceManager.getShareLastTime(), "暂无");
        String invite = safe(preferenceManager.getInviteCode(), "暂无");

        root.addView(createCard("老人状态", "当前位置：" + location + "\n最后更新：" + update + "\n共享状态：" + status));
        root.addView(createCard("今日看板", "1. 查看打卡完成情况\n2. 处理未读提醒\n3. 检查异常告警"));
        root.addView(createCard("远程协助", "已接入协助能力：可发起提醒、跟进老人回执、支持共享协助流程。"));
        root.addView(createCard("家庭信息", "家庭邀请码：" + invite + "\n建议提醒：每天固定时段发送关怀消息。"));

        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPendingAssistAlerts();
        loadFamilyHealthSnapshot();
    }

    private View createHeroCard(PreferenceManager preferenceManager) {
        LinearLayout card = createPixelCard(0xFFFFFFFF);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView title = new TextView(requireContext());
        title.setText("家属监护中心");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF1A1C2C);
        card.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("信息集中展示，提醒和告警可在此快速处理。");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFF4B5563);
        subtitle.setPadding(0, dp(8), 0, 0);
        card.addView(subtitle);

        TextView elder = new TextView(requireContext());
        elder.setText("当前守护对象：" + safe(preferenceManager.getNickname(), "未设置"));
        elder.setTextSize(15);
        elder.setTextColor(0xFF2D3748);
        elder.setPadding(0, dp(8), 0, 0);
        card.addView(elder);

        return card;
    }

    private View createPendingAssistCard() {
        LinearLayout card = createPixelCard(0xFFFFF6EF);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(requireContext());
        title.setText("协助请求（重点）");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF1A1C2C);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, titleParams);

        clearPendingButton = new Button(requireContext());
        clearPendingButton.setAllCaps(false);
        clearPendingButton.setTextSize(13);
        clearPendingButton.setTextColor(0xFFFFFFFF);
        clearPendingButton.setBackgroundColor(0xFFEF4444);
        clearPendingButton.setOnClickListener(v -> clearPendingAssistAlerts());
        header.addView(clearPendingButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(header);

        pendingAlertBody = new TextView(requireContext());
        pendingAlertBody.setText("正在加载协助请求...");
        pendingAlertBody.setTextSize(14);
        pendingAlertBody.setLineSpacing(dp(4), 1f);
        pendingAlertBody.setTextColor(0xFF2D3748);
        pendingAlertBody.setPadding(0, dp(8), 0, 0);
        pendingAlertBody.setTextIsSelectable(true);
        card.addView(pendingAlertBody);

        updateClearPendingButtonState();
        return card;
    }

    private View createHealthSharedCard() {
        LinearLayout card = createPixelCard(0xFFEFFBFF);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView title = new TextView(requireContext());
        title.setText("健康共享（老人/家属）");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF1A1C2C);
        card.addView(title);

        healthSharedBody = new TextView(requireContext());
        healthSharedBody.setText("正在加载健康共享数据...");
        healthSharedBody.setTextSize(14);
        healthSharedBody.setLineSpacing(dp(4), 1f);
        healthSharedBody.setTextColor(0xFF2D3748);
        healthSharedBody.setPadding(0, dp(8), 0, 0);
        card.addView(healthSharedBody);
        return card;
    }

    private void loadPendingAssistAlerts() {
        if (pendingAlertBody == null) {
            return;
        }
        pendingAlertBody.setText("正在加载协助请求...");
        alertRepository.fetchPendingFamilyAlerts(new AlertRepository.ResultCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (!isAdded() || pendingAlertBody == null) {
                    return;
                }
                List<Map<String, Object>> ordered = data == null ? new ArrayList<>() : new ArrayList<>(data);
                if (!ordered.isEmpty()) {
                    Collections.sort(ordered, new Comparator<Map<String, Object>>() {
                        @Override
                        public int compare(Map<String, Object> left, Map<String, Object> right) {
                            int leftFall = isFall(left) ? 1 : 0;
                            int rightFall = isFall(right) ? 1 : 0;
                            if (leftFall != rightFall) {
                                return rightFall - leftFall;
                            }
                            return parseInt(valueOf(right.get("level"), "1"), 1)
                                    - parseInt(valueOf(left.get("level"), "1"), 1);
                        }
                    });
                }

                cachedPendingAlerts.clear();
                cachedPendingAlerts.addAll(ordered);
                updateClearPendingButtonState();

                if (ordered.isEmpty()) {
                    pendingAlertBody.setText("当前没有待处理协助请求。");
                    return;
                }

                List<String> lines = new ArrayList<>();
                for (int i = 0; i < ordered.size(); i++) {
                    Map<String, Object> item = ordered.get(i);
                    String elderName = valueOf(item.get("elderName"), "老人");
                    String desc = valueOf(item.get("description"), "发起了协助请求");
                    String time = valueOf(item.get("createdAt"), "");
                    String type = valueOf(item.get("alertType"), "SOS");
                    String typeLabel = "FALL".equalsIgnoreCase(type) ? "【跌倒风险】" : "【协助】";
                    String line = (i + 1) + ". " + typeLabel + elderName + "：" + desc;
                    if (!time.isEmpty()) {
                        line += "\n   时间：" + time;
                    }
                    lines.add(line);
                }

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < lines.size(); i++) {
                    if (i > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(lines.get(i));
                }
                pendingAlertBody.setText(sb.toString());
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || pendingAlertBody == null) {
                    return;
                }
                cachedPendingAlerts.clear();
                updateClearPendingButtonState();
                pendingAlertBody.setText("加载协助请求失败：" + safe(message, "请稍后重试"));
            }
        });
    }

    private void clearPendingAssistAlerts() {
        if (clearingPendingAlerts) {
            return;
        }
        if (cachedPendingAlerts.isEmpty()) {
            Toast.makeText(requireContext(), "当前没有可清除的老人消息", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Long> alertIds = new ArrayList<>();
        for (Map<String, Object> item : cachedPendingAlerts) {
            long id = parseLong(item.get("id"), -1L);
            if (id > 0) {
                alertIds.add(id);
            }
        }
        if (alertIds.isEmpty()) {
            Toast.makeText(requireContext(), "未找到可清除的消息编号", Toast.LENGTH_SHORT).show();
            return;
        }

        clearingPendingAlerts = true;
        updateClearPendingButtonState();
        alertRepository.batchHandleAlerts(alertIds, "IGNORED", "family_home_clear",
                new AlertRepository.ResultCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer data) {
                        if (!isAdded()) {
                            return;
                        }
                        clearingPendingAlerts = false;
                        updateClearPendingButtonState();
                        int count = data == null ? 0 : Math.max(data, 0);
                        Toast.makeText(requireContext(), "已清除 " + count + " 条老人消息", Toast.LENGTH_SHORT).show();
                        loadPendingAssistAlerts();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) {
                            return;
                        }
                        clearingPendingAlerts = false;
                        updateClearPendingButtonState();
                        Toast.makeText(requireContext(), "清除失败：" + safe(message, "请稍后重试"), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateClearPendingButtonState() {
        if (clearPendingButton == null) {
            return;
        }
        if (clearingPendingAlerts) {
            clearPendingButton.setEnabled(false);
            clearPendingButton.setText("清除中...");
            return;
        }
        if (cachedPendingAlerts.isEmpty()) {
            clearPendingButton.setEnabled(false);
            clearPendingButton.setText("暂无消息");
            return;
        }
        clearPendingButton.setEnabled(true);
        clearPendingButton.setText("清除老人消息(" + cachedPendingAlerts.size() + ")");
    }

    private void loadFamilyHealthSnapshot() {
        if (healthSharedBody == null) {
            return;
        }
        healthSharedBody.setText("正在加载健康共享数据...");
        healthRepository.fetchFamilyLatestHealth(new HealthRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                if (!isAdded() || healthSharedBody == null) {
                    return;
                }
                Object itemsObj = data == null ? null : data.get("items");
                if (!(itemsObj instanceof List)) {
                    healthSharedBody.setText("当前暂无已共享的健康数据。");
                    return;
                }
                List<?> rawList = (List<?>) itemsObj;
                if (rawList.isEmpty()) {
                    healthSharedBody.setText("当前暂无已共享的健康数据。");
                    return;
                }

                StringBuilder sb = new StringBuilder();
                int max = Math.min(3, rawList.size());
                for (int i = 0; i < max; i++) {
                    Object row = rawList.get(i);
                    if (!(row instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> item = (Map<?, ?>) row;
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(i + 1).append(". ")
                            .append(valueOf(item.get("nickname"), "成员"))
                            .append("（").append(valueOf(item.get("role"), "MEMBER")).append("）")
                            .append("\n   心率：").append(valueOf(item.get("heartRate"), "暂无"))
                            .append("  血氧：").append(valueOf(item.get("bloodOxygen"), "暂无"))
                            .append("\n   血压：")
                            .append(valueOf(item.get("systolic"), "-"))
                            .append("/")
                            .append(valueOf(item.get("diastolic"), "-"))
                            .append("  步数：").append(valueOf(item.get("steps"), "0"))
                            .append("\n   更新时间：").append(valueOf(item.get("reportedAt"), "暂无"));
                }
                healthSharedBody.setText(sb.length() == 0 ? "当前暂无已共享的健康数据。" : sb.toString());
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || healthSharedBody == null) {
                    return;
                }
                healthSharedBody.setText("健康数据加载失败：" + safe(message, "请稍后重试"));
            }
        });
    }

    private View createCard(String titleText, String contentText) {
        LinearLayout card = createPixelCard(0xFFFFFFFF);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView title = new TextView(requireContext());
        title.setText(titleText);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF1A1C2C);
        card.addView(title);

        TextView content = new TextView(requireContext());
        content.setText(contentText);
        content.setTextSize(14);
        content.setLineSpacing(dp(4), 1f);
        content.setTextColor(0xFF2D3748);
        content.setPadding(0, dp(8), 0, 0);
        card.addView(content);

        return card;
    }

    private LinearLayout createPixelCard(int bgColor) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(makeRect(bgColor, 0xFF1A1C2C, 4));
        layout.setElevation(dp(2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        layout.setLayoutParams(params);
        return layout;
    }

    private GradientDrawable makeRect(int bgColor, int borderColor, int borderWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(bgColor);
        drawable.setCornerRadius(0f);
        drawable.setStroke(dp(borderWidthDp), borderColor);
        return drawable;
    }

    private boolean isFall(Map<String, Object> item) {
        return item != null && "FALL".equalsIgnoreCase(valueOf(item.get("alertType"), ""));
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long parseLong(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String valueOf(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
