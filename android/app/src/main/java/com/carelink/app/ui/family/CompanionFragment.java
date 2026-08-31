package com.carelink.app.ui.family;

import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.carelink.app.data.repository.FamilyRepository;
import com.carelink.app.data.repository.ReminderRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CompanionFragment extends Fragment {

    @Inject
    FamilyRepository familyRepository;
    @Inject
    ReminderRepository reminderRepository;

    private static final String[][] PRESET_REMINDERS = {
            {"💧", "喝水提醒", "该喝水了，记得多喝温水。"},
            {"🚶", "运动提醒", "起来活动活动，走走路舒展一下。"},
            {"💊", "吃药提醒", "到吃药时间了，别忘记按时服药。"},
            {"😴", "休息提醒", "该休息了，早点睡觉身体好。"},
            {"🍚", "吃饭提醒", "到饭点了，记得按时吃饭。"},
            {"🧥", "增减衣物", "天气有变化，记得及时增减衣服。"},
            {"☀️", "晒太阳", "天气不错，可以出门晒晒太阳。"},
            {"📵", "少看屏幕", "看屏幕太久了，休息一下眼睛。"}
    };

    private PreferenceManager preferenceManager;
    private LinearLayout historyContainer;
    private TextView elderTargetView;
    private TextView imageHintView;
    private TextInputLayout customInputLayout;
    private TextInputEditText customEditText;
    private MaterialButton refreshHistoryBtn;
    private MaterialButton clearHistoryBtn;

    private final List<Map<String, Object>> elderList = new ArrayList<>();
    private JSONArray currentHistory = new JSONArray();
    private long selectedElderUserId = -1L;
    private String selectedElderName = "未选择";
    private Uri selectedImageUri;
    private boolean clearingHistory;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                selectedImageUri = uri;
                if (imageHintView != null) {
                    imageHintView.setText(uri == null ? "未选择图片" : "已选择图片");
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        preferenceManager = new PreferenceManager(requireContext());

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_page));

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));

        TextView title = new TextView(requireContext());
        title.setText("关怀提醒");
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        root.addView(title);

        root.addView(createTargetElderCard());
        root.addView(createPresetCard());
        root.addView(createCustomCard());
        root.addView(createHistoryCard());

        scrollView.addView(root);
        loadElders();
        loadHistory();
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadHistory();
    }

    private View createTargetElderCard() {
        MaterialCardView card = createCard();
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView title = new TextView(requireContext());
        title.setText("提醒目标");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        layout.addView(title);

        elderTargetView = new TextView(requireContext());
        elderTargetView.setText("当前目标：未选择");
        elderTargetView.setTextSize(14);
        elderTargetView.setPadding(0, dp(8), 0, dp(8));
        elderTargetView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        layout.addView(elderTargetView);

        MaterialButton chooseBtn = new MaterialButton(requireContext());
        chooseBtn.setText("选择老人");
        chooseBtn.setAllCaps(false);
        chooseBtn.setOnClickListener(v -> showElderPicker());
        layout.addView(chooseBtn);

        card.addView(layout);
        return card;
    }

    private View createPresetCard() {
        MaterialCardView card = createCard();
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView title = new TextView(requireContext());
        title.setText("快捷提醒");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        title.setPadding(0, 0, 0, dp(10));
        layout.addView(title);

        LinearLayout currentRow = null;
        for (int i = 0; i < PRESET_REMINDERS.length; i++) {
            if (i % 2 == 0) {
                currentRow = new LinearLayout(requireContext());
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setWeightSum(2f);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = dp(8);
                layout.addView(currentRow, rowParams);
            }

            String emoji = PRESET_REMINDERS[i][0];
            String label = PRESET_REMINDERS[i][1];
            String message = PRESET_REMINDERS[i][2];

            MaterialButton btn = new MaterialButton(requireContext(),
                    null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(emoji + " " + label);
            btn.setTextSize(15);
            btn.setAllCaps(false);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            btnParams.setMarginStart(i % 2 == 0 ? 0 : dp(4));
            btnParams.setMarginEnd(i % 2 == 0 ? dp(4) : 0);
            btn.setLayoutParams(btnParams);
            btn.setOnClickListener(v -> sendReminder(emoji, label, message, null));

            if (currentRow != null) {
                currentRow.addView(btn);
            }
        }

        card.addView(layout);
        return card;
    }

    private View createCustomCard() {
        MaterialCardView card = createCard();
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView title = new TextView(requireContext());
        title.setText("自定义提醒");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        layout.addView(title);

        customInputLayout = new TextInputLayout(requireContext());
        customInputLayout.setHint("输入提醒内容...");
        customInputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        customEditText = new TextInputEditText(customInputLayout.getContext());
        customEditText.setMinLines(2);
        customEditText.setMaxLines(6);
        customInputLayout.addView(customEditText);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(8);
        layout.addView(customInputLayout, inputParams);

        LinearLayout imageRow = new LinearLayout(requireContext());
        imageRow.setOrientation(LinearLayout.HORIZONTAL);
        imageRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams imageRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        imageRowParams.topMargin = dp(8);
        layout.addView(imageRow, imageRowParams);

        MaterialButton pickImageBtn = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        pickImageBtn.setText("选择图片");
        pickImageBtn.setAllCaps(false);
        pickImageBtn.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        imageRow.addView(pickImageBtn);

        MaterialButton clearImageBtn = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        clearImageBtn.setText("清除图片");
        clearImageBtn.setAllCaps(false);
        clearImageBtn.setOnClickListener(v -> {
            selectedImageUri = null;
            if (imageHintView != null) {
                imageHintView.setText("未选择图片");
            }
        });
        LinearLayout.LayoutParams clearBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clearBtnParams.setMarginStart(dp(8));
        imageRow.addView(clearImageBtn, clearBtnParams);

        imageHintView = new TextView(requireContext());
        imageHintView.setText("未选择图片");
        imageHintView.setTextSize(12);
        imageHintView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        imageHintView.setPadding(0, dp(6), 0, 0);
        layout.addView(imageHintView);

        MaterialButton sendBtn = new MaterialButton(requireContext());
        sendBtn.setText("发送提醒");
        sendBtn.setAllCaps(false);
        sendBtn.setOnClickListener(v -> {
            String text = customEditText.getText() == null
                    ? ""
                    : customEditText.getText().toString().trim();
            if (text.isEmpty() && selectedImageUri == null) {
                customInputLayout.setError("请输入提醒内容或选择图片");
                return;
            }
            customInputLayout.setError(null);
            sendReminder("🔔", "自定义提醒", text, selectedImageUri);
        });
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sendParams.topMargin = dp(8);
        layout.addView(sendBtn, sendParams);

        card.addView(layout);
        return card;
    }

    private View createHistoryCard() {
        MaterialCardView card = createCard();
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(requireContext());
        title.setText("最近提醒记录");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        refreshHistoryBtn = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        refreshHistoryBtn.setAllCaps(false);
        refreshHistoryBtn.setText("刷新");
        refreshHistoryBtn.setOnClickListener(v -> loadHistory());
        header.addView(refreshHistoryBtn);

        clearHistoryBtn = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        clearHistoryBtn.setAllCaps(false);
        clearHistoryBtn.setText("一键清空");
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clearParams.setMarginStart(dp(8));
        clearHistoryBtn.setLayoutParams(clearParams);
        clearHistoryBtn.setOnClickListener(v -> confirmClearHistory());
        header.addView(clearHistoryBtn);

        layout.addView(header);

        historyContainer = new LinearLayout(requireContext());
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        historyContainer.setPadding(0, dp(8), 0, 0);
        layout.addView(historyContainer);

        card.addView(layout);
        return card;
    }

    private MaterialCardView createCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardElevation(dp(2));
        card.setRadius(dp(16));
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private void loadElders() {
        familyRepository.getElders(new FamilyRepository.ResultCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (!isAdded()) {
                    return;
                }
                elderList.clear();
                if (data != null) {
                    elderList.addAll(data);
                }
                if (!elderList.isEmpty() && selectedElderUserId <= 0) {
                    Map<String, Object> first = elderList.get(0);
                    selectedElderUserId = getLong(first.get("userId"));
                    selectedElderName = getName(first);
                }
                updateTargetText();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                updateTargetText();
                Toast.makeText(requireContext(), "老人列表加载失败：" + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showElderPicker() {
        if (elderList.isEmpty()) {
            Toast.makeText(requireContext(), "当前家庭没有可选老人", Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence[] names = new CharSequence[elderList.size()];
        int checked = 0;
        for (int i = 0; i < elderList.size(); i++) {
            Map<String, Object> elder = elderList.get(i);
            long userId = getLong(elder.get("userId"));
            names[i] = getName(elder);
            if (userId == selectedElderUserId) {
                checked = i;
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("选择提醒目标老人")
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    Map<String, Object> elder = elderList.get(which);
                    selectedElderUserId = getLong(elder.get("userId"));
                    selectedElderName = getName(elder);
                    updateTargetText();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateTargetText() {
        if (elderTargetView == null) {
            return;
        }
        if (selectedElderUserId > 0) {
            elderTargetView.setText("当前目标：" + selectedElderName + "（ID: " + selectedElderUserId + "）");
        } else {
            elderTargetView.setText("当前目标：未选择（请先选择老人）");
        }
    }

    private void sendReminder(String emoji, String label, String message, @Nullable Uri imageUri) {
        if (selectedElderUserId <= 0) {
            Toast.makeText(requireContext(), "请先选择目标老人", Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageUri != null) {
            Toast.makeText(requireContext(), "正在上传图片...", Toast.LENGTH_SHORT).show();
            reminderRepository.uploadReminderImage(imageUri, new ReminderRepository.ResultCallback<String>() {
                @Override
                public void onSuccess(String imageUrl) {
                    if (!isAdded()) {
                        return;
                    }
                    doSendReminder(emoji, label, message, imageUrl);
                }

                @Override
                public void onError(String err) {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(), "图片上传失败：" + err, Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        doSendReminder(emoji, label, message, null);
    }

    private void doSendReminder(String emoji, String label, String message, @Nullable String imageUrl) {
        String now = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
        String senderName = preferenceManager.getNickname();
        if (senderName == null || senderName.trim().isEmpty()) {
            senderName = "家属";
        }

        cacheLocalReminder(-1L, emoji, label, message, senderName, now, imageUrl, false, "");

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("elderUserId", selectedElderUserId);
        body.put("emoji", emoji);
        body.put("label", label);
        body.put("message", message == null ? "" : message);
        if (!TextUtils.isEmpty(imageUrl)) {
            body.put("imageUrl", imageUrl);
        }

        reminderRepository.sendReminder(body, new ReminderRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "已发送给 " + selectedElderName, Toast.LENGTH_SHORT).show();
                clearCustomInput();
                loadHistory();
            }

            @Override
            public void onError(String err) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "发送失败：" + err, Toast.LENGTH_SHORT).show();
                loadHistory();
            }
        });
    }

    private void clearCustomInput() {
        if (customEditText != null) {
            customEditText.setText("");
        }
        selectedImageUri = null;
        if (imageHintView != null) {
            imageHintView.setText("未选择图片");
        }
        if (customInputLayout != null) {
            customInputLayout.setError(null);
        }
    }

    private void cacheLocalReminder(long id, String emoji, String label, String message, String sender, String time,
                                    @Nullable String imageUrl, boolean read, String readTime) {
        try {
            JSONObject local = new JSONObject();
            local.put("id", id);
            local.put("emoji", emoji);
            local.put("label", label);
            local.put("message", message);
            local.put("sender", sender);
            local.put("time", time);
            local.put("timestamp", System.currentTimeMillis());
            local.put("read", read);
            local.put("readTime", readTime == null ? "" : readTime);
            local.put("imageUrl", imageUrl == null ? "" : imageUrl);

            String existingJson = preferenceManager.getCompanionReminders();
            JSONArray reminders;
            try {
                reminders = new JSONArray(existingJson);
            } catch (Exception e) {
                reminders = new JSONArray();
            }
            reminders.put(local);
            while (reminders.length() > 50) {
                reminders.remove(0);
            }
            preferenceManager.saveCompanionReminders(reminders.toString());
        } catch (Exception ignored) {
        }
    }

    private void loadHistory() {
        if (historyContainer == null) {
            return;
        }
        historyContainer.removeAllViews();
        setHistoryActionEnabled(false);

        reminderRepository.getSentReminders(new ReminderRepository.ResultCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (!isAdded()) {
                    return;
                }
                JSONArray array = new JSONArray();
                if (data != null) {
                    for (Map<String, Object> item : data) {
                        JSONObject obj = new JSONObject();
                        try {
                            obj.put("id", toLong(item.get("id")));
                            obj.put("emoji", valueOf(item.get("emoji"), "🔔"));
                            obj.put("label", valueOf(item.get("label"), "关怀提醒"));
                            obj.put("message", valueOf(item.get("message"), ""));
                            obj.put("sender", valueOf(item.get("sender"), "家属"));
                            obj.put("time", valueOf(item.get("time"), ""));
                            obj.put("read", Boolean.parseBoolean(String.valueOf(item.get("read"))));
                            obj.put("readTime", valueOf(item.get("readTime"), valueOf(item.get("read_time"), "")));
                            obj.put("imageUrl", valueOf(item.get("imageUrl"), valueOf(item.get("image_url"), "")));
                            array.put(obj);
                        } catch (Exception ignored) {
                        }
                    }
                }
                currentHistory = array;
                preferenceManager.saveCompanionReminders(array.toString());
                renderHistory(array);
                setHistoryActionEnabled(true);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                try {
                    currentHistory = new JSONArray(preferenceManager.getCompanionReminders());
                    renderHistory(currentHistory);
                } catch (Exception e) {
                    currentHistory = new JSONArray();
                    TextView err = new TextView(requireContext());
                    err.setText("加载提醒记录失败");
                    err.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                    historyContainer.addView(err);
                }
                setHistoryActionEnabled(true);
            }
        });
    }

    private void renderHistory(JSONArray reminders) {
        historyContainer.removeAllViews();
        if (reminders.length() == 0) {
            TextView empty = new TextView(requireContext());
            empty.setText("暂无提醒记录");
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            empty.setPadding(0, dp(10), 0, dp(10));
            historyContainer.addView(empty);
            return;
        }

        for (int i = reminders.length() - 1; i >= 0; i--) {
            try {
                historyContainer.addView(createHistoryItem(reminders.getJSONObject(i)));
            } catch (Exception ignored) {
            }
        }
    }

    private View createHistoryItem(JSONObject item) {
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(8), 0, dp(8));

        LinearLayout titleRow = new LinearLayout(requireContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        wrapper.addView(titleRow);

        TextView emojiView = new TextView(requireContext());
        emojiView.setText(item.optString("emoji", "🔔"));
        emojiView.setTextSize(20);
        emojiView.setPadding(0, 0, dp(8), 0);
        titleRow.addView(emojiView);

        TextView labelView = new TextView(requireContext());
        labelView.setText(item.optString("label", "提醒"));
        labelView.setTextSize(14);
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        titleRow.addView(labelView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        boolean read = item.optBoolean("read", false);
        String readTime = item.optString("readTime", "");
        TextView statusView = new TextView(requireContext());
        statusView.setText(read
                ? ("已确认" + (readTime.isEmpty() ? "" : " " + readTime))
                : "待确认");
        statusView.setTextSize(12);
        statusView.setTextColor(read ? Color.parseColor("#2E7D32") : Color.parseColor("#B26A00"));
        titleRow.addView(statusView);

        long id = item.optLong("id", -1L);
        if (id > 0) {
            MaterialButton deleteBtn = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            deleteBtn.setText("删除");
            deleteBtn.setAllCaps(false);
            deleteBtn.setInsetTop(0);
            deleteBtn.setInsetBottom(0);
            deleteBtn.setMinHeight(dp(30));
            deleteBtn.setMinimumHeight(dp(30));
            deleteBtn.setOnClickListener(v -> deleteReminder(id));
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            deleteParams.leftMargin = dp(8);
            titleRow.addView(deleteBtn, deleteParams);
        }

        TextView timeView = new TextView(requireContext());
        timeView.setText("发送时间：" + item.optString("time", ""));
        timeView.setTextSize(12);
        timeView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        timeView.setPadding(dp(28), dp(2), 0, 0);
        wrapper.addView(timeView);

        String message = item.optString("message", "");
        if (!TextUtils.isEmpty(message)) {
            TextView msgView = new TextView(requireContext());
            msgView.setText("内容：" + message);
            msgView.setTextSize(13);
            msgView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            msgView.setPadding(dp(28), dp(6), 0, 0);
            msgView.setSingleLine(false);
            msgView.setMaxLines(Integer.MAX_VALUE);
            wrapper.addView(msgView);
        }

        String imageUrl = item.optString("imageUrl", "");
        if (!TextUtils.isEmpty(imageUrl)) {
            TextView imageTitle = new TextView(requireContext());
            imageTitle.setText("图片：");
            imageTitle.setTextSize(12);
            imageTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            imageTitle.setPadding(dp(28), dp(6), 0, 0);
            wrapper.addView(imageTitle);

            ImageView iv = new ImageView(requireContext());
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ivParams.leftMargin = dp(28);
            ivParams.topMargin = dp(4);
            ivParams.rightMargin = dp(8);
            iv.setLayoutParams(ivParams);
            wrapper.addView(iv);
            Glide.with(requireContext()).load(imageUrl).into(iv);
        }

        View divider = new View(requireContext());
        divider.setBackgroundColor(Color.parseColor("#F0F0F0"));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(8);
        wrapper.addView(divider, dividerParams);
        return wrapper;
    }

    private void confirmClearHistory() {
        if (clearingHistory) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("清空最近提醒")
                .setMessage("将删除家属端最近提醒记录，操作不可撤销。是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> clearAllHistoryOneTap())
                .show();
    }

    private void clearAllHistoryOneTap() {
        JSONArray source = currentHistory == null ? new JSONArray() : currentHistory;
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            long id = item.optLong("id", -1L);
            if (id > 0) {
                ids.add(id);
            }
        }

        if (ids.isEmpty()) {
            currentHistory = new JSONArray();
            preferenceManager.saveCompanionReminders("[]");
            renderHistory(currentHistory);
            Toast.makeText(requireContext(), "最近提醒已清空", Toast.LENGTH_SHORT).show();
            return;
        }

        clearingHistory = true;
        setHistoryActionEnabled(false);
        deleteHistoryByIds(ids, 0, 0);
    }

    private void deleteHistoryByIds(List<Long> ids, int index, int failedCount) {
        if (index >= ids.size()) {
            clearingHistory = false;
            setHistoryActionEnabled(true);
            if (failedCount == 0) {
                currentHistory = new JSONArray();
                preferenceManager.saveCompanionReminders("[]");
                renderHistory(currentHistory);
                Toast.makeText(requireContext(), "最近提醒已一键清空", Toast.LENGTH_SHORT).show();
            } else {
                loadHistory();
                Toast.makeText(requireContext(), "已清空大部分记录，仍有 " + failedCount + " 条删除失败", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        long id = ids.get(index);
        reminderRepository.deleteReminder(id, new ReminderRepository.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                if (!isAdded()) {
                    return;
                }
                deleteHistoryByIds(ids, index + 1, failedCount);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                deleteHistoryByIds(ids, index + 1, failedCount + 1);
            }
        });
    }

    private void setHistoryActionEnabled(boolean enabled) {
        if (refreshHistoryBtn != null) {
            refreshHistoryBtn.setEnabled(enabled);
        }
        if (clearHistoryBtn != null) {
            clearHistoryBtn.setEnabled(enabled);
        }
    }

    private void deleteReminder(long reminderId) {
        reminderRepository.deleteReminder(reminderId, new ReminderRepository.ResultCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "提醒记录已删除", Toast.LENGTH_SHORT).show();
                loadHistory();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "删除失败：" + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String valueOf(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String getName(Map<String, Object> elder) {
        Object nick = elder.get("nickname");
        String name = nick == null ? "" : String.valueOf(nick).trim();
        return name.isEmpty() ? "老人" : name;
    }

    private long getLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return -1L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return -1L;
        }
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

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
