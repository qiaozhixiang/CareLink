package com.carelink.app.ui.settings;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.carelink.app.R;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.ui.family.JoinFamilyActivity;
import com.carelink.app.utils.ApiConfig;
import com.carelink.app.utils.FontScaleHelper;

public class SettingsActivity extends AppCompatActivity {
    private PreferenceManager preferenceManager;
    private LinearLayout content;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferenceManager = new PreferenceManager(this);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(getColorValue(R.color.surface_page));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(28, 28, 28, 40);
        scrollView.addView(content);
        setContentView(scrollView);
        render();
    }

    private void render() {
        content.removeAllViews();
        content.addView(heroTitle());
        content.addView(sectionCard("显示与字体",
                info("当前字体大小", preferenceManager.getFontSize() + " 号"),
                action("设置字体大小", false, v -> showFontSizeDialog())));

        content.addView(sectionCard("实用功能",
                info("紧急联系人", preferenceManager.getEmergencyContact()),
                action("设置紧急联系人", false, v -> showEmergencyContactDialog()),
                info("实时定位", preferenceManager.isRealtimeLocationEnabled() ? "已开启" : "已关闭"),
                action("切换实时定位", false, v -> toggleRealtimeLocation()),
                action("家庭创建/加入", false, v -> startActivity(new Intent(this, JoinFamilyActivity.class)))));

        content.addView(sectionCard("系统与清理",
                info("服务器地址", ApiConfig.HTTP_BASE_URL),
                action("打开通知权限设置", false, v -> openNotificationSettings()),
                action("清空家庭缓存", false, v -> clearFamilyCache()),
                action("清空本地聊天记录", false, v -> clearAiCache())));

        content.addView(sectionCard("关于应用",
                info("当前版本", "产品化可落地版框架"),
                info("结构状态", "本页已作为双端统一设置实现，后续建议逐步移除旧 SettingsFragment 引用")));
    }

    private View heroTitle() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(30, 30, 30, 30);
        card.setBackground(createRoundedBg(getColorValue(R.color.brand_blue), 34));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 18;
        card.setLayoutParams(params);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("账户偏好 · 双端统一");
        eyebrow.setTextSize(FontScaleHelper.secondary(this));
        eyebrow.setTextColor(getColorValue(R.color.white_80));
        card.addView(eyebrow);

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(FontScaleHelper.title(this) + 2);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getColorValue(R.color.white));
        title.setPadding(0, 8, 0, 8);
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText("统一管理显示、语音、提醒、家庭与安全等设置。当前页面已和新的“我的”界面采用同一套卡片式布局语言。");
        desc.setTextSize(FontScaleHelper.body(this));
        desc.setTextColor(getColorValue(R.color.white_80));
        card.addView(desc);
        return card;
    }

    private View sectionCard(String titleText, View... children) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        card.setBackground(createRoundedBg(getColorValue(R.color.surface_card), 28));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 16;
        card.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(FontScaleHelper.sectionTitle(this));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getColorValue(R.color.text_primary));
        title.setPadding(0, 0, 0, 14);
        card.addView(title);

        for (View child : children) card.addView(child);
        return card;
    }

    private View info(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 0, 0, 12);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(FontScaleHelper.secondary(this));
        labelView.setTextColor(getColorValue(R.color.text_secondary));
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(FontScaleHelper.body(this));
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setTextColor(getColorValue(R.color.text_primary));
        valueView.setPadding(0, 6, 0, 0);
        row.addView(valueView);
        return row;
    }

    private Button action(String text, boolean primary, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(FontScaleHelper.body(this));
        btn.setAllCaps(false);
        btn.setOnClickListener(listener);
        btn.setTextColor(getColorValue(primary ? R.color.white : R.color.text_primary));
        btn.setBackground(createRoundedBg(getColorValue(primary ? R.color.brand_blue : R.color.action_neutral), 22));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 10;
        btn.setLayoutParams(params);
        return btn;
    }

    private void toggleRealtimeLocation() {
        boolean value = !preferenceManager.isRealtimeLocationEnabled();
        preferenceManager.setRealtimeLocationEnabled(value);
        Toast.makeText(this, value ? "实时定位已开启，重新进入老人端主页会自动定位" : "实时定位已关闭", Toast.LENGTH_SHORT).show();
        render();
    }

    private void showEmergencyContactDialog() {

        EditText editText = new EditText(this);
        editText.setHint("请输入紧急联系人电话");
        editText.setText(preferenceManager.getEmergencyContact());
        new AlertDialog.Builder(this)
                .setTitle("设置紧急联系人")
                .setView(editText)
                .setPositiveButton("保存", (dialog, which) -> {
                    String phone = editText.getText().toString().trim();
                    if (!phone.isEmpty()) {
                        preferenceManager.saveEmergencyContact(phone);
                        Toast.makeText(this, "紧急联系人已更新", Toast.LENGTH_SHORT).show();
                        render();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearFamilyCache() {
        preferenceManager.clearFamilyState();
        Toast.makeText(this, "家庭缓存已清空", Toast.LENGTH_SHORT).show();
        render();
    }

    private void clearAiCache() {
        preferenceManager.clearAiChatHistory("ELDER");
        preferenceManager.clearAiChatHistory("FAMILY");
        Toast.makeText(this, "本地聊天记录已清空", Toast.LENGTH_SHORT).show();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(fallback);
        }
    }

    private void showFontSizeDialog() {
        String[] sizes = {"标准", "偏大", "超大"};
        int current = preferenceManager.getFontSize();
        int checked = current >= 24 ? 2 : current >= 20 ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("设置字体大小")
                .setSingleChoiceItems(sizes, checked, null)
                .setPositiveButton("应用", (dialog, which) -> {
                    AlertDialog ad = (AlertDialog) dialog;
                    int index = ad.getListView().getCheckedItemPosition();
                    int size = index == 2 ? 24 : index == 1 ? 20 : 18;
                    preferenceManager.saveFontSize(size);
                    Toast.makeText(this, "字体大小已更新", Toast.LENGTH_SHORT).show();
                    render();
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

    private int getColorValue(int colorRes) {
        return ContextCompat.getColor(this, colorRes);
    }
}
