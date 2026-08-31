package com.carelink.app.ui.elder;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.carelink.app.receiver.ReminderReceiver;
import com.carelink.app.ui.calendar.PerpetualCalendarActivity;
import com.carelink.app.utils.DemoDataHelper;
import com.carelink.app.utils.FontScaleHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ScheduleFragment extends Fragment {

    private static final int REMIND_MINUTES_BEFORE = 10;

    private Calendar currentMonth;
    private int selectedDay;
    private TextView monthTitle;
    private TextView daySectionTitle;
    private LinearLayout calendarContainer;
    private LinearLayout dayScheduleContainer;
    private EditText activeVoiceInput;

    private final ActivityResultLauncher<Intent> speechLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty() && activeVoiceInput != null) {
                        activeVoiceInput.setText(matches.get(0));
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        currentMonth = Calendar.getInstance(Locale.CHINA);
        selectedDay = currentMonth.get(Calendar.DAY_OF_MONTH);

        ScrollView rootScroll = new ScrollView(requireContext());
        rootScroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(110));
        root.setBackgroundColor(0xFFFFFBF2);
        rootScroll.addView(root);

        TextView topTip = new TextView(requireContext());
        topTip.setText("像日历一样查看每天安排，也能一键新增今天日程。");
        topTip.setTextSize(FontScaleHelper.secondary(requireContext()));
        topTip.setPadding(dp(16), dp(14), dp(16), dp(14));
        topTip.setBackgroundColor(0xFFFFE8C8);
        root.addView(topTip, fullLp());

        root.addView(createMonthControlPanel());

        monthTitle = new TextView(requireContext());
        monthTitle.setTextSize(FontScaleHelper.title(requireContext()));
        monthTitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        monthTitle.setPadding(0, dp(8), 0, dp(10));
        monthTitle.setBackgroundColor(0xFFFFF1DA);
        root.addView(monthTitle, fullLp());

        root.addView(createWeekHeader());

        calendarContainer = new LinearLayout(requireContext());
        calendarContainer.setOrientation(LinearLayout.VERTICAL);
        calendarContainer.setBackgroundColor(0xFFFFFCF7);
        root.addView(calendarContainer, fullLp());

        daySectionTitle = new TextView(requireContext());
        daySectionTitle.setText("当日日程");
        daySectionTitle.setTextSize(FontScaleHelper.sectionTitle(requireContext()));
        daySectionTitle.setPadding(dp(14), dp(14), dp(14), dp(8));
        daySectionTitle.setBackgroundColor(0xFFFFE0B2);
        LinearLayout.LayoutParams dayTitleLp = fullLp();
        dayTitleLp.topMargin = dp(12);
        root.addView(daySectionTitle, dayTitleLp);

        dayScheduleContainer = new LinearLayout(requireContext());
        dayScheduleContainer.setOrientation(LinearLayout.VERTICAL);
        dayScheduleContainer.setPadding(0, dp(10), 0, 0);
        root.addView(dayScheduleContainer, fullLp());

        root.addView(createBottomActionRow());

        FloatingActionButton fab = new FloatingActionButton(requireContext());
        fab.setImageResource(android.R.drawable.ic_input_add);
        fab.setOnClickListener(v -> showAddDialog(false));
        LinearLayout.LayoutParams fabLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        fabLp.gravity = Gravity.END;
        fabLp.topMargin = dp(10);
        root.addView(fab, fabLp);

        refreshCalendar();
        scheduleAllUpcomingReminders();
        return rootScroll;
    }

    private View createMonthControlPanel() {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(10), 0, dp(8));
        panel.setBackgroundColor(0xFFFFF3E0);

        LinearLayout row1 = new LinearLayout(requireContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(createHeaderButton("去年", v -> {
            currentMonth.add(Calendar.YEAR, -1);
            selectedDay = 1;
            refreshCalendar();
        }), flexLpWithRightGap());
        row1.addView(createHeaderButton("本月", v -> {
            Calendar now = Calendar.getInstance(Locale.CHINA);
            currentMonth.set(Calendar.YEAR, now.get(Calendar.YEAR));
            currentMonth.set(Calendar.MONTH, now.get(Calendar.MONTH));
            selectedDay = now.get(Calendar.DAY_OF_MONTH);
            refreshCalendar();
        }), flexLpWithRightGap());
        row1.addView(createHeaderButton("明年", v -> {
            currentMonth.add(Calendar.YEAR, 1);
            selectedDay = 1;
            refreshCalendar();
        }), flexLp());

        LinearLayout row2 = new LinearLayout(requireContext());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);
        row2.addView(createHeaderButton("上月", v -> {
            currentMonth.add(Calendar.MONTH, -1);
            selectedDay = 1;
            refreshCalendar();
        }), flexLpWithRightGap());
        row2.addView(createHeaderButton("今天", v -> jumpToToday()), flexLpWithRightGap());
        row2.addView(createHeaderButton("下月", v -> {
            currentMonth.add(Calendar.MONTH, 1);
            selectedDay = 1;
            refreshCalendar();
        }), flexLp());

        panel.addView(row1);
        panel.addView(row2);
        return panel;
    }

    private Button createHeaderButton(String text, View.OnClickListener listener) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(FontScaleHelper.secondary(requireContext()));
        button.setOnClickListener(listener);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        return button;
    }

    private View createBottomActionRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, 0);

        Button addTodayBtn = new Button(requireContext());
        addTodayBtn.setText("为今天新增日程");
        addTodayBtn.setAllCaps(false);
        addTodayBtn.setOnClickListener(v -> showAddDialog(true));
        row.addView(addTodayBtn, flexLpWithRightGap());

        Button addSelectedBtn = new Button(requireContext());
        addSelectedBtn.setText("为所选日期新增");
        addSelectedBtn.setAllCaps(false);
        addSelectedBtn.setOnClickListener(v -> showAddDialog(false));
        row.addView(addSelectedBtn, flexLpWithRightGap());

        Button btnPerpetual = new Button(requireContext());
        btnPerpetual.setText("万年历");
        btnPerpetual.setAllCaps(false);
        btnPerpetual.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PerpetualCalendarActivity.class)));
        row.addView(btnPerpetual, flexLp());
        return row;
    }

    private LinearLayout createWeekHeader() {
        String[] weeks = {"日", "一", "二", "三", "四", "五", "六"};
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(6));
        for (String week : weeks) {
            row.addView(createCell(week, true, false, false));
        }
        return row;
    }

    private void jumpToToday() {
        Calendar now = Calendar.getInstance(Locale.CHINA);
        currentMonth.set(Calendar.YEAR, now.get(Calendar.YEAR));
        currentMonth.set(Calendar.MONTH, now.get(Calendar.MONTH));
        selectedDay = now.get(Calendar.DAY_OF_MONTH);
        refreshCalendar();
    }

    private void refreshCalendar() {
        monthTitle.setText(String.format(Locale.CHINA, "%d年%d月", currentMonth.get(Calendar.YEAR), currentMonth.get(Calendar.MONTH) + 1));
        calendarContainer.removeAllViews();

        Calendar temp = (Calendar) currentMonth.clone();
        temp.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = temp.get(Calendar.DAY_OF_WEEK);
        int offset = firstDayOfWeek - Calendar.SUNDAY;
        int daysInMonth = temp.getActualMaximum(Calendar.DAY_OF_MONTH);

        int day = 1;
        for (int rowIndex = 0; rowIndex < 6 && day <= daysInMonth; rowIndex++) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (int col = 0; col < 7; col++) {
                TextView cell;
                if (rowIndex == 0 && col < offset) {
                    cell = createCell("", false, false, false);
                } else if (day <= daysInMonth) {
                    int currentDay = day;
                    boolean hasSchedule = hasScheduleForDay(currentDay);
                    String label = hasSchedule ? currentDay + "\n●" : String.valueOf(currentDay);
                    cell = createCell(label, false, currentDay == selectedDay, hasSchedule);
                    cell.setOnClickListener(v -> {
                        selectedDay = currentDay;
                        refreshCalendar();
                    });
                    day++;
                } else {
                    cell = createCell("", false, false, false);
                }
                row.addView(cell);
            }
            calendarContainer.addView(row);
        }

        refreshDaySchedules();
    }

    private TextView createCell(String text, boolean header, boolean selected, boolean hasSchedule) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tv.setTextSize(header ? FontScaleHelper.secondary(requireContext()) : FontScaleHelper.secondary(requireContext()));
        tv.setPadding(0, dp(10), 0, dp(10));
        tv.setMinLines(header ? 1 : 2);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (selected) {
            tv.setBackgroundColor(0xFFFFC107);
            tv.setTextColor(0xFF5D4037);
        } else if (hasSchedule) {
            tv.setBackgroundColor(0xFFFFE0B2);
            tv.setTextColor(0xFF6D4C41);
        } else if (!header) {
            tv.setBackgroundColor(0xFFFFF8E1);
        }
        return tv;
    }

    private boolean hasScheduleForDay(int day) {
        String dateKey = String.format(Locale.CHINA, "%02d-%02d", currentMonth.get(Calendar.MONTH) + 1, day);
        for (DemoDataHelper.ScheduleItem item : DemoDataHelper.getSchedules()) {
            if (dateKey.equals(item.date)) {
                return true;
            }
        }
        return false;
    }

    private void refreshDaySchedules() {
        dayScheduleContainer.removeAllViews();

        String dateKey = String.format(Locale.CHINA, "%02d-%02d", currentMonth.get(Calendar.MONTH) + 1, selectedDay);
        daySectionTitle.setText(dateKey + " 当日日程");

        List<DemoDataHelper.ScheduleItem> schedules = new ArrayList<>();
        for (DemoDataHelper.ScheduleItem item : DemoDataHelper.getSchedules()) {
            if (dateKey.equals(item.date)) {
                schedules.add(item);
            }
        }

        if (schedules.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(dateKey + " 暂无日程安排\n可点击下方按钮新增");
            empty.setTextSize(FontScaleHelper.body(requireContext()));
            empty.setPadding(dp(16), dp(16), dp(16), dp(16));
            empty.setBackgroundColor(0xFFFFF3E0);
            dayScheduleContainer.addView(empty, fullLp());
            return;
        }

        for (DemoDataHelper.ScheduleItem item : schedules) {
            dayScheduleContainer.addView(createScheduleCard(item));
        }
    }

    private View createScheduleCard(DemoDataHelper.ScheduleItem item) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundColor(item.done ? 0xFFE8F5E9 : 0xFFFFF3E0);
        LinearLayout.LayoutParams cardLp = fullLp();
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);

        LinearLayout top = new LinearLayout(requireContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView timeView = new TextView(requireContext());
        timeView.setText(item.time);
        timeView.setTextSize(FontScaleHelper.sectionTitle(requireContext()));
        timeView.setPadding(dp(10), dp(6), dp(10), dp(6));
        timeView.setBackgroundColor(0xFFFFE0B2);
        top.addView(timeView);

        TextView title = new TextView(requireContext());
        title.setText((item.done ? "✓ " : "○ ") + item.title);
        title.setTextSize(FontScaleHelper.sectionTitle(requireContext()));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(10);
        top.addView(title, titleLp);

        Button doneButton = new Button(requireContext());
        doneButton.setText(item.done ? "已完成" : "标记完成");
        doneButton.setAllCaps(false);
        doneButton.setEnabled(!item.done);
        doneButton.setOnClickListener(v -> {
            DemoDataHelper.markScheduleDone(item.date, item.title);
            refreshDaySchedules();
        });
        top.addView(doneButton);

        card.addView(top);

        TextView info = new TextView(requireContext());
        info.setText("分类：" + item.category + "  优先级：" + item.priority + "  创建者：" + item.creator);
        info.setTextSize(FontScaleHelper.secondary(requireContext()));
        info.setPadding(0, dp(8), 0, dp(6));
        card.addView(info);

        TextView note = new TextView(requireContext());
        note.setText(TextUtils.isEmpty(item.note) ? "无备注" : item.note);
        note.setTextSize(FontScaleHelper.body(requireContext()));
        card.addView(note);

        return card;
    }

    private void showAddDialog(boolean forceToday) {
        if (forceToday) {
            jumpToToday();
        }

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(14), dp(18), dp(8));

        EditText etTitle = new EditText(requireContext());
        etTitle.setHint("请输入日程名称");
        layout.addView(etTitle, fullLp());

        Button btnVoice = new Button(requireContext());
        btnVoice.setText("语音输入日程名称");
        btnVoice.setAllCaps(false);
        btnVoice.setOnClickListener(v -> startSpeechInput(etTitle));
        layout.addView(btnVoice, fullLp());

        EditText etTime = new EditText(requireContext());
        etTime.setHint("请输入时间，如 18:00");
        layout.addView(etTime, fullLp());

        EditText etNote = new EditText(requireContext());
        etNote.setHint("请输入备注（可选）");
        layout.addView(etNote, fullLp());

        Button btnVoiceNote = new Button(requireContext());
        btnVoiceNote.setText("语音输入备注");
        btnVoiceNote.setAllCaps(false);
        btnVoiceNote.setOnClickListener(v -> startSpeechInput(etNote));
        layout.addView(btnVoiceNote, fullLp());

        String dateKey = String.format(Locale.CHINA, "%02d-%02d", currentMonth.get(Calendar.MONTH) + 1, selectedDay);

        new AlertDialog.Builder(requireContext())
                .setTitle("新增日程（" + dateKey + "）")
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String title = etTitle.getText().toString().trim();
                    String time = etTime.getText().toString().trim();
                    String note = etNote.getText().toString().trim();

                    if (title.isEmpty()) {
                        Toast.makeText(requireContext(), "请输入日程名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!isValidTime(time)) {
                        Toast.makeText(requireContext(), "时间格式错误，请使用 HH:mm", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DemoDataHelper.addSchedule(title, dateKey, time, note, "老人");
                    refreshCalendar();
                    scheduleAllUpcomingReminders();
                    Toast.makeText(requireContext(), "日程已保存，临近时间会提醒", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean isValidTime(String time) {
        if (time == null) {
            return false;
        }
        return time.matches("^([01]\\d|2[0-3]):([0-5]\\d)$");
    }

    private void startSpeechInput(EditText target) {
        activeVoiceInput = target;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出日程内容");
        speechLauncher.launch(intent);
    }

    private void scheduleAllUpcomingReminders() {
        for (DemoDataHelper.ScheduleItem item : DemoDataHelper.getSchedules()) {
            scheduleReminderIfNeeded(item);
        }
    }

    private void scheduleReminderIfNeeded(DemoDataHelper.ScheduleItem item) {
        if (item == null || item.done) {
            return;
        }

        String[] dm = item.date == null ? null : item.date.split("-");
        String[] hm = item.time == null ? null : item.time.split(":");
        if (dm == null || hm == null || dm.length != 2 || hm.length != 2) {
            return;
        }

        int month;
        int day;
        int hour;
        int minute;
        try {
            month = Integer.parseInt(dm[0]);
            day = Integer.parseInt(dm[1]);
            hour = Integer.parseInt(hm[0]);
            minute = Integer.parseInt(hm[1]);
        } catch (Exception e) {
            return;
        }

        Calendar scheduleTime = Calendar.getInstance(Locale.CHINA);
        Calendar now = Calendar.getInstance(Locale.CHINA);
        scheduleTime.set(Calendar.YEAR, now.get(Calendar.YEAR));
        scheduleTime.set(Calendar.MONTH, month - 1);
        scheduleTime.set(Calendar.DAY_OF_MONTH, day);
        scheduleTime.set(Calendar.HOUR_OF_DAY, hour);
        scheduleTime.set(Calendar.MINUTE, minute);
        scheduleTime.set(Calendar.SECOND, 0);
        scheduleTime.set(Calendar.MILLISECOND, 0);

        Calendar reminderTime = (Calendar) scheduleTime.clone();
        reminderTime.add(Calendar.MINUTE, -REMIND_MINUTES_BEFORE);

        if (reminderTime.getTimeInMillis() <= System.currentTimeMillis()) {
            return;
        }

        Intent intent = new Intent(requireContext(), ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_REMINDER);
        intent.putExtra(ReminderReceiver.EXTRA_TITLE, "日程提醒");
        intent.putExtra(ReminderReceiver.EXTRA_BODY,
                item.title + " 将在 " + item.time + " 开始，请提前准备。");
        intent.putExtra(ReminderReceiver.EXTRA_TYPE, "routine");

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                buildReminderRequestCode(item),
                intent,
                flags
        );

        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        long triggerAt = reminderTime.getTimeInMillis();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        }
    }

    private int buildReminderRequestCode(DemoDataHelper.ScheduleItem item) {
        String key = (item.date == null ? "" : item.date)
                + "|"
                + (item.time == null ? "" : item.time)
                + "|"
                + (item.title == null ? "" : item.title);
        return key.hashCode();
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
}
