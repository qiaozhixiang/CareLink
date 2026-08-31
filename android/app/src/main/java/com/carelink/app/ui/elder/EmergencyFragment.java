package com.carelink.app.ui.elder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.utils.FontScaleHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmergencyFragment extends Fragment {

    private static final long HOLD_TO_UNLOCK_MS = 3000L;
    private static final long TRIGGER_COOLDOWN_MS = 5000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> requestSmsPermissionLauncher;

    private Button helpButton;
    private TextView unlockHint;
    private Runnable unlockRunnable;

    private boolean sosUnlocked = false;
    private long lastTriggerTs = 0L;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestSmsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        doSendEmergencySmsThenDial();
                    } else {
                        Toast.makeText(requireContext(), "短信权限被拒绝，已切换为打开120拨号页面", Toast.LENGTH_SHORT).show();
                        dialEmergency();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFFFFF8F8);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(96));
        scrollView.addView(root);

        root.addView(createHeroCard());

        Button sosButton = createPixelButton("SOS", 0xFFE05C5C);
        sosButton.setTextSize(40f);
        sosButton.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams sosParams = new LinearLayout.LayoutParams(dp(220), dp(220));
        sosParams.gravity = Gravity.CENTER_HORIZONTAL;
        sosParams.bottomMargin = dp(10);
        root.addView(sosButton, sosParams);

        unlockHint = createHintText();
        unlockHint.setText("长按 SOS 3 秒解锁");
        unlockHint.setGravity(Gravity.CENTER_HORIZONTAL);
        unlockHint.setPadding(0, 0, 0, dp(10));
        root.addView(unlockHint, fullLp());

        helpButton = createPixelButton("紧急求助", 0xFFFF004D);
        helpButton.setVisibility(View.GONE);
        helpButton.setEnabled(false);
        helpButton.setOnClickListener(v -> triggerEmergencyWithPermissionCheck());
        root.addView(helpButton, fullLp());

        Button dial120Button = createPixelButton("仅打开120拨号", 0xFF29ADFF);
        dial120Button.setOnClickListener(v -> dialEmergency());
        LinearLayout.LayoutParams dialLp = fullLp();
        dialLp.topMargin = dp(8);
        root.addView(dial120Button, dialLp);

        root.addView(createTipCard("触发流程", "1. 长按 SOS 3 秒解锁\n2. 点击“紧急求助”\n3. 自动向紧急联系人发送位置短信\n4. 自动跳转120拨号界面"));
        root.addView(createTipCard("说明", "短信发送失败时，系统仍会继续拉起120拨号页面，确保最快求助。"));

        sosButton.setOnTouchListener(this::handleSosTouch);

        return scrollView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelUnlockCountdown();
        helpButton = null;
        unlockHint = null;
    }

    private boolean handleSosTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (sosUnlocked) {
                    return true;
                }
                unlockHint.setText("正在解锁，请持续按住...");
                cancelUnlockCountdown();
                unlockRunnable = () -> {
                    sosUnlocked = true;
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    if (helpButton != null) {
                        helpButton.setVisibility(View.VISIBLE);
                        helpButton.setEnabled(true);
                    }
                    if (unlockHint != null) {
                        unlockHint.setText("已解锁，请点击下方“紧急求助”");
                    }
                    Toast.makeText(requireContext(), "SOS 已解锁", Toast.LENGTH_SHORT).show();
                };
                mainHandler.postDelayed(unlockRunnable, HOLD_TO_UNLOCK_MS);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!sosUnlocked) {
                    cancelUnlockCountdown();
                    if (unlockHint != null) {
                        unlockHint.setText("长按 SOS 3 秒解锁");
                    }
                }
                return true;
            default:
                return true;
        }
    }

    private void cancelUnlockCountdown() {
        if (unlockRunnable != null) {
            mainHandler.removeCallbacks(unlockRunnable);
            unlockRunnable = null;
        }
    }

    private void relockSos() {
        sosUnlocked = false;
        cancelUnlockCountdown();
        if (helpButton != null) {
            helpButton.setVisibility(View.GONE);
            helpButton.setEnabled(false);
        }
        if (unlockHint != null) {
            unlockHint.setText("长按 SOS 3 秒解锁");
        }
    }

    private void triggerEmergencyWithPermissionCheck() {
        long now = System.currentTimeMillis();
        if (now - lastTriggerTs < TRIGGER_COOLDOWN_MS) {
            Toast.makeText(requireContext(), "刚刚已触发，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        lastTriggerTs = now;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            doSendEmergencySmsThenDial();
        } else {
            requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
        }
    }

    private void doSendEmergencySmsThenDial() {
        PreferenceManager prefs = new PreferenceManager(requireContext());
        String nickname = prefs.getNickname();
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = "老人";
        }

        double lat = prefs.getShareLatitude();
        double lng = prefs.getShareLongitude();
        boolean hasLocation = (lat != 0.0d || lng != 0.0d);
        String locationText = hasLocation
                ? "https://uri.amap.com/marker?position=" + lng + "," + lat
                : "暂未获取到定位";
        String timeText = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String smsContent = "【紧急求助】" + nickname
                + " 在 " + timeText
                + " 需要紧急帮助，位置：" + locationText;

        SmsManager smsManager;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            smsManager = requireContext().getSystemService(SmsManager.class);
        } else {
            smsManager = SmsManager.getDefault();
        }

        int sent = 0;
        List<String> targets = parseEmergencyContacts(prefs.getEmergencyContact());
        if (smsManager != null) {
            for (String phone : targets) {
                try {
                    ArrayList<String> parts = smsManager.divideMessage(smsContent);
                    if (parts.size() <= 1) {
                        smsManager.sendTextMessage(phone, null, smsContent, null, null);
                    } else {
                        smsManager.sendMultipartTextMessage(phone, null, parts, null, null);
                    }
                    sent++;
                } catch (Exception ignored) {
                }
            }
        }

        if (sent > 0) {
            Toast.makeText(requireContext(), "已发送求助短信，正在打开120拨号页面", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(requireContext(), "未发送成功，正在直接打开120拨号页面", Toast.LENGTH_LONG).show();
        }

        relockSos();
        dialEmergency();
    }

    private List<String> parseEmergencyContacts(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        String[] parts = raw.split("[,，;；\\s]+");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String phone = part.trim();
            if (phone.isEmpty() || "120".equals(phone)) {
                continue;
            }
            result.add(phone);
        }
        return result;
    }

    private void dialEmergency() {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:120"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "无法打开拨号页面，请手动拨打120", Toast.LENGTH_LONG).show();
        }
    }

    private View createHeroCard() {
        LinearLayout card = createPixelCard(0xFFFFFFFF);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView titleTv = new TextView(requireContext());
        titleTv.setText("紧急求助");
        titleTv.setTextSize(FontScaleHelper.title(requireContext()) + 2);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setTextColor(0xFF1A1C2C);
        card.addView(titleTv);

        TextView desc = new TextView(requireContext());
        desc.setText("请长按 SOS 3 秒解锁。解锁后点击“紧急求助”，将自动发送短信并跳转120拨号。\n如遇危险请优先拨打120。\n");
        desc.setTextSize(Math.max(15, FontScaleHelper.body(requireContext()) - 1));
        desc.setTextColor(0xFF2D3748);
        desc.setPadding(0, dp(8), 0, 0);
        card.addView(desc);
        return card;
    }

    private View createTipCard(String titleText, String contentText) {
        LinearLayout card = createPixelCard(0xFFFFFFFF);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams params = fullLp();
        params.topMargin = dp(10);
        card.setLayoutParams(params);

        TextView title = new TextView(requireContext());
        title.setText(titleText);
        title.setTextSize(FontScaleHelper.sectionTitle(requireContext()));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF1A1C2C);
        card.addView(title);

        TextView content = new TextView(requireContext());
        content.setText(contentText);
        content.setTextSize(Math.max(15, FontScaleHelper.body(requireContext()) - 1));
        content.setTextColor(0xFF2D3748);
        content.setPadding(0, dp(6), 0, 0);
        card.addView(content);
        return card;
    }

    private TextView createHintText() {
        TextView text = new TextView(requireContext());
        text.setTextColor(0xFF4B5563);
        text.setTextSize(Math.max(13, FontScaleHelper.secondary(requireContext())));
        return text;
    }

    private LinearLayout createPixelCard(int bgColor) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(makeRect(bgColor, 0xFF1A1C2C, 4));
        layout.setElevation(dp(2));
        LinearLayout.LayoutParams lp = fullLp();
        lp.bottomMargin = dp(12);
        layout.setLayoutParams(lp);
        return layout;
    }

    private Button createPixelButton(String text, int bgColor) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(Math.max(17, FontScaleHelper.body(requireContext())));
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setBackground(makeRect(bgColor, 0xFF1A1C2C, 4));
        return button;
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

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
