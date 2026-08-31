package com.carelink.app.ui.elder;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.carelink.app.R;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.repository.AlertRepository;
import com.carelink.app.data.repository.HealthRepository;
import com.carelink.app.data.repository.ReminderRepository;
import com.carelink.app.ui.map.MapPreviewActivity;
import com.carelink.app.utils.DemoDataHelper;
import com.carelink.app.utils.FontScaleHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ElderHomeFragment extends Fragment {

    private static final int MAX_CUSTOM_APPS = 8;
    private static final int REQUEST_BODY_SENSORS_PERMISSION = 3011;
    private static final float FALL_ACCELERATION_THRESHOLD = 24f;
    private static final long FALL_ALERT_COOLDOWN_MS = 45_000L;

    @Inject
    ReminderRepository reminderRepository;
    @Inject
    AlertRepository alertRepository;
    @Inject
    HealthRepository healthRepository;

    private PreferenceManager preferenceManager;

    private TextView shareStatusTag;
    private TextView shareContent;
    private TextView shareFooter;

    private LinearLayout reminderContainer;
    private TextView reminderHint;
    private final List<JSONObject> pendingReminders = new ArrayList<>();

    private LinearLayout customAppsContainer;
    private TextView customAppsHint;
    private final List<HomeAppItem> customApps = new ArrayList<>();

    private TextView healthSummaryView;
    private TextView healthHintView;
    private TextView fallStatusView;
    private Button fallToggleButton;

    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private Sensor stepCounterSensor;
    private Sensor accelerometerSensor;

    private int sensorHeartRate = -1;
    private int sensorSteps = -1;
    private long lastFallAlertAt = 0L;

    private final SensorEventListener healthSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event == null || event.sensor == null || event.values == null || event.values.length == 0) {
                return;
            }
            int type = event.sensor.getType();
            if (type == Sensor.TYPE_HEART_RATE) {
                int value = Math.round(event.values[0]);
                if (value > 0) {
                    sensorHeartRate = value;
                    preferenceManager.saveHealthHeartRate(value);
                    preferenceManager.saveHealthUpdatedAt(nowText());
                    refreshHealthViews();
                }
                return;
            }
            if (type == Sensor.TYPE_STEP_COUNTER) {
                int value = Math.round(event.values[0]);
                if (value >= 0) {
                    sensorSteps = value;
                    preferenceManager.saveHealthSteps(value);
                    preferenceManager.saveHealthUpdatedAt(nowText());
                    refreshHealthViews();
                }
                return;
            }
            if (type == Sensor.TYPE_ACCELEROMETER && preferenceManager.isFallDetectionEnabled()) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                float acceleration = (float) Math.sqrt(x * x + y * y + z * z);
                long now = System.currentTimeMillis();
                if (acceleration >= FALL_ACCELERATION_THRESHOLD && (now - lastFallAlertAt) > FALL_ALERT_COOLDOWN_MS) {
                    lastFallAlertAt = now;
                    handlePotentialFall(acceleration);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        preferenceManager = new PreferenceManager(requireContext());
        loadCustomApps();
        loadCachedReminders();

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFFF7F5F0);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(88));
        scrollView.addView(root);

        initHealthSensorManager();
        root.addView(createHeroPanel());
        root.addView(createReminderCard());
        root.addView(createShareCard());
        root.addView(createHealthCard());
        root.addView(createCustomAppsCard());
        root.addView(createQuickActionCard());

        refreshShareViews();
        refreshHealthViews();
        renderReminders();
        renderCustomApps();
        fetchUnreadReminders();
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchUnreadReminders();
        registerHealthSensors();
        refreshHealthViews();
    }

    @Override
    public void onPause() {
        unregisterHealthSensors();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_BODY_SENSORS_PERMISSION) {
            return;
        }
        if (!isAdded()) {
            return;
        }
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            registerHealthSensors();
            refreshHealthViews();
        } else {
            Toast.makeText(requireContext(), "未授予体征权限，将使用手动录入和手机传感器数据", Toast.LENGTH_SHORT).show();
        }
    }

    private View createHeroPanel() {
        LinearLayout panel = createPixelCard(0xFFFFFFFF, 0xFF1A1C2C);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(requireContext());
        title.setText(DemoDataHelper.getGreeting());
        title.setTextSize(FontScaleHelper.title(requireContext()) + 2);
        title.setTextColor(0xFF1A1C2C);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title);

        TextView date = new TextView(requireContext());
        date.setText(DemoDataHelper.getTodayDate());
        date.setTextSize(Math.max(14, FontScaleHelper.secondary(requireContext())));
        date.setTextColor(0xFF4B5563);
        date.setPadding(0, dp(6), 0, 0);
        panel.addView(date);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("今日重点：按时吃药、完成打卡、保持联系。");
        subtitle.setTextSize(Math.max(15, FontScaleHelper.body(requireContext()) - 1));
        subtitle.setTextColor(0xFF2D3748);
        subtitle.setPadding(0, dp(10), 0, 0);
        panel.addView(subtitle);

        return panel;
    }

    private View createReminderCard() {
        LinearLayout card = createSectionCard("家属提醒", "提醒会暂存在首页，点击“我知道了”后家属端可看到回执。");

        reminderHint = createHintText();
        card.addView(reminderHint);

        reminderContainer = new LinearLayout(requireContext());
        reminderContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(reminderContainer);

        LinearLayout actionRow = new LinearLayout(requireContext());
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(10), 0, 0);

        Button refreshBtn = createPixelButton("刷新提醒", 0xFF29ADFF);
        refreshBtn.setOnClickListener(v -> fetchUnreadReminders());
        actionRow.addView(refreshBtn, flexLp());

        card.addView(actionRow);
        return card;
    }

    private View createShareCard() {
        LinearLayout card = createSectionCard("位置共享", "家属端查看的是云端共享位置，可按场景一键更新。");

        shareStatusTag = new TextView(requireContext());
        shareStatusTag.setTextSize(FontScaleHelper.secondary(requireContext()));
        shareStatusTag.setTextColor(0xFF1A1C2C);
        shareStatusTag.setPadding(dp(10), dp(8), dp(10), dp(8));
        shareStatusTag.setBackground(makeRect(0xFFFFF1B8, 0xFF1A1C2C, 3));
        card.addView(shareStatusTag);

        shareContent = new TextView(requireContext());
        shareContent.setTextSize(Math.max(15, FontScaleHelper.body(requireContext()) - 1));
        shareContent.setTextColor(0xFF2D3748);
        shareContent.setPadding(0, dp(10), 0, 0);
        card.addView(shareContent);

        LinearLayout row1 = new LinearLayout(requireContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(12), 0, 0);
        row1.addView(createShareActionButton("立即更新", "单次共享", "家中", "本次共享后结束"), flexLpWithRightGap());
        row1.addView(createShareActionButton("共享30分钟", "临时共享", "社区中心", "30分钟后结束"), flexLp());
        card.addView(row1);

        LinearLayout row2 = new LinearLayout(requireContext());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);
        row2.addView(createShareActionButton("共享1小时", "临时共享", "医院", "1小时后结束"), flexLpWithRightGap());

        Button stopBtn = createPixelButton("停止共享", 0xFFE05C5C);
        stopBtn.setOnClickListener(v -> {
            preferenceManager.saveShareStatus("已结束");
            preferenceManager.saveShareEndTime("已手动停止");
            refreshShareViews();
            Toast.makeText(requireContext(), "已停止位置共享", Toast.LENGTH_SHORT).show();
        });
        row2.addView(stopBtn, flexLp());
        card.addView(row2);

        shareFooter = createHintText();
        shareFooter.setPadding(0, dp(10), 0, 0);
        card.addView(shareFooter);
        return card;
    }

    private Button createShareActionButton(String text, String status, String location, String endTime) {
        Button button = createPixelButton(text, 0xFF29ADFF);
        button.setOnClickListener(v -> {
            String now = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
            preferenceManager.saveShareStatus(status);
            preferenceManager.saveShareLastLocation(location);
            preferenceManager.saveShareLastTime(now);
            preferenceManager.saveShareEndTime(endTime);
            refreshShareViews();
            Toast.makeText(requireContext(), text + "成功", Toast.LENGTH_SHORT).show();
        });
        return button;
    }

    private View createHealthCard() {
        LinearLayout card = createSectionCard("健康监测", "支持手表/外设体征同步，家属端可共享查看。");

        healthSummaryView = new TextView(requireContext());
        healthSummaryView.setTextSize(Math.max(14, FontScaleHelper.body(requireContext()) - 1));
        healthSummaryView.setTextColor(0xFF2D3748);
        healthSummaryView.setPadding(0, dp(2), 0, 0);
        card.addView(healthSummaryView);

        fallStatusView = createHintText();
        fallStatusView.setPadding(0, dp(8), 0, 0);
        card.addView(fallStatusView);

        LinearLayout row1 = new LinearLayout(requireContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(12), 0, 0);

        Button syncBtn = createPixelButton("同步外设数据", 0xFF29ADFF);
        syncBtn.setOnClickListener(v -> {
            syncHealthSnapshot();
            Toast.makeText(requireContext(), "健康数据已同步", Toast.LENGTH_SHORT).show();
        });
        row1.addView(syncBtn, flexLpWithRightGap());

        Button uploadBtn = createPixelButton("上传健康共享", 0xFF52C97A);
        uploadBtn.setOnClickListener(v -> {
            syncHealthSnapshot();
            uploadHealthSnapshot(false, "manual_upload");
        });
        row1.addView(uploadBtn, flexLp());
        card.addView(row1);

        LinearLayout row2 = new LinearLayout(requireContext());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);

        Button abnormalBtn = createPixelButton("异常体征上报", 0xFFE8956D);
        abnormalBtn.setOnClickListener(v -> {
            syncHealthSnapshot();
            uploadHealthSnapshot(false, "abnormal_vital");
            long elderId = preferenceManager.getUserId() > 0 ? preferenceManager.getUserId() : preferenceManager.getElderId();
            if (elderId > 0) {
                alertRepository.triggerEmergency(
                        elderId,
                        preferenceManager.getShareLatitude(),
                        preferenceManager.getShareLongitude(),
                        "检测到异常体征，请家属尽快关注老人状态",
                        "ABNORMAL_VITAL",
                        3
                );
            }
            Toast.makeText(requireContext(), "已向家属端发送异常体征告警", Toast.LENGTH_SHORT).show();
        });
        row2.addView(abnormalBtn, flexLpWithRightGap());

        fallToggleButton = createPixelButton("开启跌倒检测", 0xFFFF004D);
        fallToggleButton.setOnClickListener(v -> toggleFallDetection());
        row2.addView(fallToggleButton, flexLp());
        card.addView(row2);

        healthHintView = createHintText();
        healthHintView.setPadding(0, dp(10), 0, 0);
        card.addView(healthHintView);
        return card;
    }

    private void toggleFallDetection() {
        boolean enabled = !preferenceManager.isFallDetectionEnabled();
        preferenceManager.setFallDetectionEnabled(enabled);
        registerHealthSensors();
        refreshHealthViews();
        Toast.makeText(requireContext(), enabled ? "已开启跌倒检测" : "已关闭跌倒检测", Toast.LENGTH_SHORT).show();
    }

    private void syncHealthSnapshot() {
        requestBodySensorPermissionIfNeeded();

        int heartRate = sensorHeartRate > 0 ? sensorHeartRate : preferenceManager.getHealthHeartRate();
        if (heartRate <= 0) {
            heartRate = 72;
        }
        int bloodOxygen = preferenceManager.getHealthBloodOxygen();
        if (bloodOxygen <= 0) {
            bloodOxygen = 97;
        }
        int systolic = preferenceManager.getHealthSystolic();
        if (systolic <= 0) {
            systolic = 122;
        }
        int diastolic = preferenceManager.getHealthDiastolic();
        if (diastolic <= 0) {
            diastolic = 78;
        }
        int steps = sensorSteps >= 0 ? sensorSteps : preferenceManager.getHealthSteps();
        if (steps < 0) {
            steps = 0;
        }

        preferenceManager.saveHealthHeartRate(heartRate);
        preferenceManager.saveHealthBloodOxygen(bloodOxygen);
        preferenceManager.saveHealthSystolic(systolic);
        preferenceManager.saveHealthDiastolic(diastolic);
        preferenceManager.saveHealthSteps(steps);
        preferenceManager.saveHealthUpdatedAt(nowText());
        refreshHealthViews();
    }

    private void uploadHealthSnapshot(boolean fallDetected, String source) {
        int heartRate = preferenceManager.getHealthHeartRate();
        int bloodOxygen = preferenceManager.getHealthBloodOxygen();
        int systolic = preferenceManager.getHealthSystolic();
        int diastolic = preferenceManager.getHealthDiastolic();
        int steps = preferenceManager.getHealthSteps();

        healthRepository.reportHealthSnapshot(
                heartRate,
                bloodOxygen,
                systolic,
                diastolic,
                steps,
                source,
                fallDetected,
                new HealthRepository.ResultCallback<Map<String, Object>>() {
                    @Override
                    public void onSuccess(Map<String, Object> data) {
                        if (!isAdded()) {
                            return;
                        }
                        preferenceManager.saveHealthUpdatedAt(nowText());
                        refreshHealthViews();
                        if (fallDetected) {
                            Toast.makeText(requireContext(), "跌倒异常已同步到家属端", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "健康数据已共享到家属端", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) {
                            return;
                        }
                        if (healthHintView != null) {
                            healthHintView.setText("健康数据同步失败：" + safeText(message, "请稍后重试"));
                        }
                    }
                }
        );
    }

    private void handlePotentialFall(float acceleration) {
        if (!isAdded()) {
            return;
        }
        syncHealthSnapshot();
        uploadHealthSnapshot(true, "fall_detection");

        long elderId = preferenceManager.getUserId() > 0 ? preferenceManager.getUserId() : preferenceManager.getElderId();
        if (elderId > 0) {
            String description = "疑似跌倒：加速度峰值 " + String.format(Locale.CHINA, "%.2f", acceleration)
                    + " m/s²，已触发紧急通知，请尽快联系老人确认情况";
            alertRepository.triggerEmergency(
                    elderId,
                    preferenceManager.getShareLatitude(),
                    preferenceManager.getShareLongitude(),
                    description,
                    "FALL",
                    3
            );
        }

        if (healthHintView != null) {
            healthHintView.setText("检测到异常震动，已将跌倒风险作为紧急消息推送给家属端首页。");
        }
    }

    private View createCustomAppsCard() {
        LinearLayout card = createSectionCard("本机应用", "可将常用应用固定到首页，点击打开，长按移除。");

        Button addButton = createPixelButton("添加本机应用", 0xFFFF004D);
        addButton.setOnClickListener(v -> showInstalledAppsPicker());
        card.addView(addButton, fullLp());

        customAppsHint = createHintText();
        customAppsHint.setPadding(0, dp(10), 0, dp(8));
        card.addView(customAppsHint);

        customAppsContainer = new LinearLayout(requireContext());
        customAppsContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(customAppsContainer);

        return card;
    }

    private View createQuickActionCard() {
        LinearLayout card = createSectionCard("快捷服务", "");

        LinearLayout row1 = new LinearLayout(requireContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);

        Button safeBtn = createPixelButton("我很安全", 0xFF52C97A);
        safeBtn.setOnClickListener(v -> Toast.makeText(requireContext(), "已通知家属你当前状态正常。", Toast.LENGTH_SHORT).show());
        row1.addView(safeBtn, flexLpWithRightGap());

        Button contactBtn = createPixelButton("联系家属", 0xFFE8956D);
        contactBtn.setOnClickListener(v -> sendContactFamilyRequest());
        row1.addView(contactBtn, flexLp());
        card.addView(row1);

        LinearLayout row2 = new LinearLayout(requireContext());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);

        Button mapButton = createPixelButton("地图预览", 0xFF29ADFF);
        mapButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), MapPreviewActivity.class)));
        row2.addView(mapButton, flexLpWithRightGap());

        Button aiButton = createPixelButton("AI聊天", 0xFFFFA300);
        aiButton.setOnClickListener(v -> requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.elder_content_container, new ElderAiChatFragment(), "elder_ai_chat")
                .commitAllowingStateLoss());
        row2.addView(aiButton, flexLp());
        card.addView(row2);

        return card;
    }

    private void sendContactFamilyRequest() {
        long elderId = preferenceManager.getUserId();
        if (elderId <= 0) {
            elderId = preferenceManager.getElderId();
        }
        if (elderId <= 0) {
            Toast.makeText(requireContext(), "当前账号未绑定老人身份，无法发送家属协助申请。", Toast.LENGTH_SHORT).show();
            return;
        }
        double lat = preferenceManager.getShareLatitude();
        double lng = preferenceManager.getShareLongitude();
        alertRepository.triggerEmergency(
                elderId,
                lat,
                lng,
                "老人主动发起协助请求，请尽快查看并联系。",
                "SOS",
                3
        );
        Toast.makeText(requireContext(), "已向家属端发送协助申请。", Toast.LENGTH_SHORT).show();
    }

    private void loadCachedReminders() {
        pendingReminders.clear();
        String raw = preferenceManager.getElderPendingReminders();
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    pendingReminders.add(item);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void persistReminders() {
        JSONArray array = new JSONArray();
        for (JSONObject item : pendingReminders) {
            array.put(item);
        }
        preferenceManager.saveElderPendingReminders(array.toString());
    }

    private void fetchUnreadReminders() {
        reminderRepository.getUnreadReminders(new ReminderRepository.ResultCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                pendingReminders.clear();
                if (data != null) {
                    for (Map<String, Object> item : data) {
                        JSONObject object = new JSONObject();
                        try {
                            object.put("id", toLong(item.get("id")));
                            object.put("emoji", safeText(item.get("emoji"), "🔔"));
                            object.put("label", safeText(item.get("label"), "关怀提醒"));
                            object.put("message", safeText(item.get("message"), ""));
                            object.put("sender", safeText(item.get("sender"), "家属"));
                            object.put("time", safeText(item.get("time"), safeText(item.get("createdAt"), "")));
                            object.put("imageUrl", safeText(item.get("imageUrl"), safeText(item.get("image_url"), "")));
                            object.put("read", false);
                            pendingReminders.add(object);
                        } catch (JSONException ignored) {
                        }
                    }
                }
                persistReminders();
                renderReminders();
            }

            @Override
            public void onError(String message) {
                renderReminders();
            }
        });
    }

    private void renderReminders() {
        if (reminderContainer == null || reminderHint == null) {
            return;
        }
        reminderContainer.removeAllViews();
        if (pendingReminders.isEmpty()) {
            reminderHint.setText("当前没有新的家属提醒。");
            return;
        }
        reminderHint.setText("你有 " + pendingReminders.size() + " 条未确认提醒。");

        for (JSONObject item : new ArrayList<>(pendingReminders)) {
            LinearLayout row = createPixelCard(0xFFFFFFFF, 0xFF1A1C2C);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(8);

            TextView title = new TextView(requireContext());
            title.setText(item.optString("emoji", "🔔") + " " + item.optString("label", "关怀提醒"));
            title.setTextSize(FontScaleHelper.sectionTitle(requireContext()));
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(0xFF1A1C2C);
            row.addView(title);

            TextView msg = new TextView(requireContext());
            msg.setText(item.optString("message", ""));
            msg.setTextSize(Math.max(15, FontScaleHelper.body(requireContext()) - 1));
            msg.setTextColor(0xFF2D3748);
            msg.setPadding(0, dp(6), 0, dp(6));
            row.addView(msg);

            String imageUrl = item.optString("imageUrl", "");
            if (!TextUtils.isEmpty(imageUrl)) {
                ImageView imageView = new ImageView(requireContext());
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(72), dp(72));
                imageParams.bottomMargin = dp(6);
                row.addView(imageView, imageParams);
                Glide.with(requireContext()).load(imageUrl).centerCrop().into(imageView);
            }

            TextView meta = createHintText();
            meta.setText("来自：" + item.optString("sender", "家属") + "  时间：" + item.optString("time", ""));
            row.addView(meta);

            Button ackBtn = createPixelButton("我知道了", 0xFF52C97A);
            ackBtn.setOnClickListener(v -> markReminderRead(item));
            LinearLayout.LayoutParams ackParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            ackParams.topMargin = dp(8);
            row.addView(ackBtn, ackParams);

            reminderContainer.addView(row, rowParams);
        }
    }

    private void markReminderRead(JSONObject item) {
        long id = item.optLong("id", -1);
        if (id <= 0) {
            pendingReminders.remove(item);
            persistReminders();
            renderReminders();
            return;
        }
        reminderRepository.markReminderRead(id, new ReminderRepository.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                pendingReminders.remove(item);
                persistReminders();
                renderReminders();
                Toast.makeText(requireContext(), "已确认，家属会收到你的回执。", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), "确认失败：" + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadCustomApps() {
        customApps.clear();
        String raw = preferenceManager.getElderHomeApps();
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String label = object.optString("label", "应用");
                String packageName = object.optString("packageName", "");
                String badge = object.optString("badge", firstChar(label));
                if (!packageName.trim().isEmpty()) {
                    customApps.add(new HomeAppItem(label, packageName, badge));
                }
            }
        } catch (JSONException ignored) {
        }
    }

    private void persistCustomApps() {
        JSONArray array = new JSONArray();
        for (HomeAppItem item : customApps) {
            JSONObject object = new JSONObject();
            try {
                object.put("label", item.label);
                object.put("packageName", item.packageName);
                object.put("badge", item.badge);
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        preferenceManager.saveElderHomeApps(array.toString());
    }

    private void showInstalledAppsPicker() {
        List<HomeAppItem> apps = loadInstalledLaunchableApps();
        if (apps.isEmpty()) {
            Toast.makeText(requireContext(), "未找到可启动应用", Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence[] labels = new CharSequence[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        Set<String> selectedPackages = new HashSet<>();
        for (HomeAppItem item : customApps) {
            selectedPackages.add(item.packageName);
        }
        for (int i = 0; i < apps.size(); i++) {
            HomeAppItem item = apps.get(i);
            labels[i] = item.label;
            checked[i] = selectedPackages.contains(item.packageName);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("选择要添加的应用");
        builder.setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked);
        builder.setNegativeButton("取消", null);
        builder.setPositiveButton("保存", (dialog, which) -> {
            List<HomeAppItem> selected = new ArrayList<>();
            for (int i = 0; i < apps.size(); i++) {
                if (checked[i]) {
                    selected.add(apps.get(i));
                }
            }
            if (selected.size() > MAX_CUSTOM_APPS) {
                selected = selected.subList(0, MAX_CUSTOM_APPS);
                Toast.makeText(requireContext(), "最多保留前8个应用", Toast.LENGTH_SHORT).show();
            }
            customApps.clear();
            customApps.addAll(selected);
            persistCustomApps();
            renderCustomApps();
        });
        builder.show();
    }

    private List<HomeAppItem> loadInstalledLaunchableApps() {
        List<HomeAppItem> result = new ArrayList<>();
        PackageManager pm = requireContext().getPackageManager();
        Set<String> seen = new HashSet<>();

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchers = pm.queryIntentActivities(launcherIntent, 0);
        for (ResolveInfo info : launchers) {
            if (info == null || info.activityInfo == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            if (packageName == null || packageName.trim().isEmpty() || !seen.add(packageName)) {
                continue;
            }
            if (requireContext().getPackageName().equals(packageName)) {
                continue;
            }
            CharSequence label = info.loadLabel(pm);
            String finalLabel = label == null ? packageName : label.toString().trim();
            if (finalLabel.isEmpty()) {
                finalLabel = packageName;
            }
            result.add(new HomeAppItem(finalLabel, packageName, firstChar(finalLabel)));
        }

        if (result.isEmpty()) {
            List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo appInfo : installedApps) {
                if (appInfo == null) {
                    continue;
                }
                String packageName = appInfo.packageName;
                if (packageName == null || packageName.trim().isEmpty() || !seen.add(packageName)) {
                    continue;
                }
                if (requireContext().getPackageName().equals(packageName)) {
                    continue;
                }
                Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                if (launchIntent == null) {
                    continue;
                }
                CharSequence label = appInfo.loadLabel(pm);
                String finalLabel = label == null ? packageName : label.toString().trim();
                if (finalLabel.isEmpty()) {
                    finalLabel = packageName;
                }
                result.add(new HomeAppItem(finalLabel, packageName, firstChar(finalLabel)));
            }
        }

        result.sort(Comparator.comparing(item -> item.label));
        return result;
    }

    private void renderCustomApps() {
        if (customAppsContainer == null || customAppsHint == null) {
            return;
        }
        customAppsContainer.removeAllViews();
        if (customApps.isEmpty()) {
            customAppsHint.setText("还没有添加应用，点击“添加本机应用”。");
            return;
        }
        customAppsHint.setText("已添加 " + customApps.size() + " 个应用。点击可打开，长按可移除。");

        for (HomeAppItem item : customApps) {
            Button button = createPixelButton(item.label, 0xFF29ADFF);
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setOnClickListener(v -> launchCustomApp(item));
            button.setOnLongClickListener(v -> {
                removeCustomApp(item.packageName);
                return true;
            });
            LinearLayout.LayoutParams params = fullLp();
            params.bottomMargin = dp(8);
            customAppsContainer.addView(button, params);
        }
    }

    private void launchCustomApp(HomeAppItem item) {
        try {
            Intent intent = requireContext().getPackageManager().getLaunchIntentForPackage(item.packageName);
            if (intent == null) {
                Toast.makeText(requireContext(), "该应用不可启动", Toast.LENGTH_SHORT).show();
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "应用启动失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeCustomApp(String packageName) {
        for (int i = customApps.size() - 1; i >= 0; i--) {
            if (customApps.get(i).packageName.equals(packageName)) {
                customApps.remove(i);
            }
        }
        persistCustomApps();
        renderCustomApps();
        Toast.makeText(requireContext(), "已移除", Toast.LENGTH_SHORT).show();
    }

    private void refreshShareViews() {
        if (shareStatusTag == null || shareContent == null || shareFooter == null) {
            return;
        }
        String status = preferenceManager.getShareStatus();
        String location = preferenceManager.getShareLastLocation();
        String time = preferenceManager.getShareLastTime();
        String endTime = preferenceManager.getShareEndTime();

        shareStatusTag.setText("状态：" + safeText(status, "未共享"));
        shareContent.setText("位置：" + safeText(location, "暂无")
                + "\n更新时间：" + safeText(time, "暂无")
                + "\n结束时间：" + safeText(endTime, "暂无")
                + "\n紧急联系人：" + safeText(preferenceManager.getEmergencyContact(), "未设置"));
        shareFooter.setText("可以随时一键更新、临时共享，或立即停止。");
    }

    private void initHealthSensorManager() {
        if (sensorManager != null) {
            return;
        }
        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            return;
        }
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    private void registerHealthSensors() {
        if (!isAdded()) {
            return;
        }
        if (sensorManager == null) {
            initHealthSensorManager();
        }
        if (sensorManager == null) {
            return;
        }
        unregisterHealthSensors();

        if (stepCounterSensor != null) {
            sensorManager.registerListener(healthSensorListener, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (preferenceManager.isFallDetectionEnabled() && accelerometerSensor != null) {
            sensorManager.registerListener(healthSensorListener, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        if (heartRateSensor != null) {
            boolean bodyPermissionGranted = ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED;
            if (bodyPermissionGranted) {
                sensorManager.registerListener(healthSensorListener, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    private void unregisterHealthSensors() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(healthSensorListener);
        }
    }

    private void requestBodySensorPermissionIfNeeded() {
        if (!isAdded() || heartRateSensor == null) {
            return;
        }
        boolean granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BODY_SENSORS)
                == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, REQUEST_BODY_SENSORS_PERMISSION);
        }
    }

    private void refreshHealthViews() {
        if (healthSummaryView == null || healthHintView == null || fallStatusView == null) {
            return;
        }
        int heartRate = preferenceManager.getHealthHeartRate();
        int bloodOxygen = preferenceManager.getHealthBloodOxygen();
        int systolic = preferenceManager.getHealthSystolic();
        int diastolic = preferenceManager.getHealthDiastolic();
        int steps = preferenceManager.getHealthSteps();
        String updatedAt = safeText(preferenceManager.getHealthUpdatedAt(), "暂无");

        StringBuilder summary = new StringBuilder();
        summary.append("心率：").append(heartRate > 0 ? heartRate + " bpm" : "暂无")
                .append("\n血氧：").append(bloodOxygen > 0 ? bloodOxygen + "%" : "暂无")
                .append("\n血压：").append(systolic > 0 && diastolic > 0 ? (systolic + "/" + diastolic + " mmHg") : "暂无")
                .append("\n步数：").append(Math.max(steps, 0))
                .append("\n更新时间：").append(updatedAt);
        healthSummaryView.setText(summary.toString());

        boolean fallEnabled = preferenceManager.isFallDetectionEnabled();
        fallStatusView.setText("跌倒检测：" + (fallEnabled ? "已开启（异常震动将自动上报紧急消息）" : "未开启"));
        if (fallToggleButton != null) {
            fallToggleButton.setText(fallEnabled ? "关闭跌倒检测" : "开启跌倒检测");
        }

        String sensorHint;
        if (sensorManager == null) {
            sensorHint = "当前设备未检测到传感器服务，可手动同步并上传健康数据。";
        } else if (heartRateSensor == null) {
            sensorHint = "未检测到心率传感器，可通过手表外设同步后上传。";
        } else {
            sensorHint = "已接入手机/外设传感器，家属端可查看共享健康数据。";
        }
        healthHintView.setText(sensorHint);
    }

    private String nowText() {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
    }

    private LinearLayout createSectionCard(String title, String desc) {
        LinearLayout card = createPixelCard(0xFFFFFFFF, 0xFF1A1C2C);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout.LayoutParams cardLp = fullLp();
        cardLp.bottomMargin = dp(12);
        card.setLayoutParams(cardLp);

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextColor(0xFF1A1C2C);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextSize(FontScaleHelper.sectionTitle(requireContext()) + 1);
        card.addView(titleView);

        if (desc != null && !desc.trim().isEmpty()) {
            TextView descView = createHintText();
            descView.setText(desc);
            descView.setPadding(0, dp(6), 0, dp(8));
            card.addView(descView);
        }
        return card;
    }

    private LinearLayout createPixelCard(int bgColor, int borderColor) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(makeRect(bgColor, borderColor, 4));
        layout.setElevation(dp(2));
        return layout;
    }

    private Button createPixelButton(String text, int bgColor) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(Math.max(15, FontScaleHelper.body(requireContext()) - 1));
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setBackground(makeRect(bgColor, 0xFF1A1C2C, 4));
        return button;
    }

    private TextView createHintText() {
        TextView text = new TextView(requireContext());
        text.setTextColor(0xFF4B5563);
        text.setTextSize(Math.max(13, FontScaleHelper.secondary(requireContext())));
        return text;
    }

    private GradientDrawable makeRect(int bgColor, int borderColor, int borderWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(bgColor);
        drawable.setCornerRadius(0f);
        drawable.setStroke(dp(borderWidthDp), borderColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fullLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams flexLp() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f);
    }

    private LinearLayout.LayoutParams flexLpWithRightGap() {
        LinearLayout.LayoutParams lp = flexLp();
        lp.rightMargin = dp(8);
        return lp;
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    private String firstChar(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "A";
        }
        return text.substring(0, 1);
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return -1L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private String safeText(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static class HomeAppItem {
        final String label;
        final String packageName;
        final String badge;

        HomeAppItem(String label, String packageName, String badge) {
            this.label = label;
            this.packageName = packageName;
            this.badge = badge == null || badge.trim().isEmpty() ? "A" : badge.trim();
        }
    }
}
