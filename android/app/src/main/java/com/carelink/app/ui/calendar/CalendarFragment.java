package com.carelink.app.ui.calendar;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.carelink.app.utils.DemoDataHelper;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CalendarFragment extends Fragment {
    private Calendar currentMonth;
    private int selectedDay;
    private TextView monthTitle;
    private LinearLayout calendarContainer;
    private LinearLayout dayScheduleContainer;
    private EditText activeVoiceInput;

    private final ActivityResultLauncher<Intent> speechLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty() && activeVoiceInput != null) activeVoiceInput.setText(matches.get(0));
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        currentMonth = Calendar.getInstance(Locale.CHINA);
        selectedDay = currentMonth.get(Calendar.DAY_OF_MONTH);

        ScrollView rootScroll = new ScrollView(requireContext());
        rootScroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 120);
        root.setBackgroundColor(0xFFF5F8FF);
        rootScroll.addView(root);

        TextView title = new TextView(requireContext());
        title.setText("日历与日程");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(24, 24, 24, 16);
        title.setBackground(createRoundedBg(0xFFDCEBFF, 26));
        root.addView(title);

        LinearLayout line1 = new LinearLayout(requireContext());
        line1.setOrientation(LinearLayout.HORIZONTAL);
        line1.setPadding(12, 16, 12, 8);
        line1.setBackground(createRoundedBg(0xFFCFE3FF, 24));
        TextView prevYear = createHeaderButton("<< 上年");
        TextView prevMonth = createHeaderButton("< 上月");
        TextView nextMonth = createHeaderButton("下月 >");
        TextView nextYear = createHeaderButton("下年 >>");
        line1.addView(prevYear); line1.addView(prevMonth); line1.addView(nextMonth); line1.addView(nextYear);
        root.addView(line1);

        monthTitle = new TextView(requireContext());
        monthTitle.setTextSize(24);
        monthTitle.setTypeface(Typeface.DEFAULT_BOLD);
        monthTitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        monthTitle.setPadding(0, 16, 0, 16);
        monthTitle.setBackground(createRoundedBg(0xFFE4F0FF, 20));
        root.addView(monthTitle);

        prevYear.setOnClickListener(v -> { currentMonth.add(Calendar.YEAR, -1); selectedDay = 1; refreshCalendar(); });
        prevMonth.setOnClickListener(v -> { currentMonth.add(Calendar.MONTH, -1); selectedDay = 1; refreshCalendar(); });
        nextMonth.setOnClickListener(v -> { currentMonth.add(Calendar.MONTH, 1); selectedDay = 1; refreshCalendar(); });
        nextYear.setOnClickListener(v -> { currentMonth.add(Calendar.YEAR, 1); selectedDay = 1; refreshCalendar(); });

        root.addView(createWeekHeader());

        calendarContainer = new LinearLayout(requireContext());
        calendarContainer.setOrientation(LinearLayout.VERTICAL);
        calendarContainer.setBackground(createRoundedBg(0xFFFFFFFF, 24));
        LinearLayout.LayoutParams calParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        calParams.topMargin = 12;
        root.addView(calendarContainer, calParams);

        TextView sectionTitle = new TextView(requireContext());
        sectionTitle.setText("当日日程");
        sectionTitle.setTextSize(20);
        sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sectionTitle.setPadding(20, 20, 20, 12);
        sectionTitle.setBackground(createRoundedBg(0xFFD7E8FF, 24));
        LinearLayout.LayoutParams secParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        secParams.topMargin = 18;
        root.addView(sectionTitle, secParams);

        dayScheduleContainer = new LinearLayout(requireContext());
        dayScheduleContainer.setOrientation(LinearLayout.VERTICAL);
        dayScheduleContainer.setPadding(0, 12, 0, 0);
        root.addView(dayScheduleContainer);

        Button btnPerpetual = new Button(requireContext());
        btnPerpetual.setText("查看万年历");
        btnPerpetual.setBackground(createRoundedBg(0xFFFFFFFF, 22));
        btnPerpetual.setOnClickListener(v -> startActivity(new Intent(requireContext(), PerpetualCalendarActivity.class)));
        root.addView(btnPerpetual);

        com.google.android.material.floatingactionbutton.FloatingActionButton fab = new com.google.android.material.floatingactionbutton.FloatingActionButton(requireContext());
        fab.setImageResource(android.R.drawable.ic_input_add);
        fab.setOnClickListener(v -> showAddDialog());
        root.addView(fab);

        refreshCalendar();
        return rootScroll;
    }

    private TextView createHeaderButton(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(15);
        tv.setPadding(16, 12, 16, 12);
        tv.setBackground(createRoundedBg(0xFFFFFFFF, 18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(6, 0, 6, 0);
        tv.setLayoutParams(params);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        return tv;
    }

    private LinearLayout createWeekHeader() {
        String[] weeks = {"日", "一", "二", "三", "四", "五", "六"};
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 12, 0, 8);
        for (String week : weeks) row.addView(createCell(week, true, false, false));
        return row;
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
                    boolean hasSchedule = hasScheduleForDay(day);
                    cell = createCell(String.valueOf(day), false, day == selectedDay, hasSchedule);
                    final int currentDay = day;
                    cell.setOnClickListener(v -> { selectedDay = currentDay; refreshCalendar(); });
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
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tv.setTextSize(header ? 17 : 16);
        tv.setPadding(0, 20, 0, 20);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (selected) {
            tv.setBackground(createRoundedBg(0xFF8EC5FF, 16));
            tv.setTextColor(0xFF0D3B66);
        } else if (hasSchedule) {
            tv.setBackground(createRoundedBg(0xFFD7E8FF, 16));
            tv.setTextColor(0xFF184A7A);
        } else if (!header) {
            tv.setBackground(createRoundedBg(0xFFF1F6FF, 16));
        }
        return tv;
    }

    private boolean hasScheduleForDay(int day) {
        String dateKey = String.format(Locale.CHINA, "%02d-%02d", currentMonth.get(Calendar.MONTH) + 1, day);
        for (DemoDataHelper.ScheduleItem item : DemoDataHelper.getSchedules()) if (dateKey.equals(item.date)) return true;
        return false;
    }

    private void refreshDaySchedules() {
        dayScheduleContainer.removeAllViews();
        String dateKey = String.format(Locale.CHINA, "%02d-%02d", currentMonth.get(Calendar.MONTH) + 1, selectedDay);
        List<DemoDataHelper.ScheduleItem> schedules = new ArrayList<>();
        for (DemoDataHelper.ScheduleItem item : DemoDataHelper.getSchedules()) if (dateKey.equals(item.date)) schedules.add(item);
        if (schedules.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(dateKey + " 暂无日程安排");
            empty.setTextSize(16);
            empty.setPadding(18, 18, 18, 18);
            empty.setBackground(createRoundedBg(0xFFDCEBFF, 20));
            dayScheduleContainer.addView(empty);
            return;
        }
        for (DemoDataHelper.ScheduleItem item : schedules) {
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(24, 20, 24, 20);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = 16;
            card.setLayoutParams(params);
            card.setBackground(createRoundedBg(item.done ? 0xFFE8F5E9 : 0xFFFFF8E1, 22));
            TextView title = new TextView(requireContext());
            title.setText((item.done ? "✓ " : "○ ") + item.title);
            title.setTextSize(19);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(title);
            TextView info = new TextView(requireContext());
            info.setText("时间：" + item.time + "   分类：" + item.category + "   优先级：" + item.priority);
            info.setTextSize(15);
            info.setPadding(0, 8, 0, 8);
            card.addView(info);
            TextView note = new TextView(requireContext());
            note.setText(item.note + "\n添加者：" + item.creator);
            note.setTextSize(15);
            card.addView(note);
            card.setOnClickListener(v -> { DemoDataHelper.markScheduleDone(item.date, item.title); refreshDaySchedules(); });
            dayScheduleContainer.addView(card);
        }
    }

    private void showAddDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        EditText etTitle = new EditText(requireContext());
        etTitle.setHint("请输入日程名称");
        layout.addView(etTitle);
        Button btnVoice = new Button(requireContext());
        btnVoice.setText("语音输入日程名称");
        btnVoice.setOnClickListener(v -> startSpeechInput(etTitle));
        layout.addView(btnVoice);
        EditText etTime = new EditText(requireContext());
        etTime.setHint("请输入时间，如：09:00");
        layout.addView(etTime);
        EditText etNote = new EditText(requireContext());
        etNote.setHint("请输入说明");
        layout.addView(etNote);
        Button btnVoiceNote = new Button(requireContext());
        btnVoiceNote.setText("语音输入备注");
        btnVoiceNote.setOnClickListener(v -> startSpeechInput(etNote));
        layout.addView(btnVoiceNote);

        new AlertDialog.Builder(requireContext())
                .setTitle("家属新增日程")
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String title = etTitle.getText().toString().trim();
                    String time = etTime.getText().toString().trim();
                    String note = etNote.getText().toString().trim();
                    if (!title.isEmpty() && !time.isEmpty()) {
                        String dateKey = String.format(Locale.CHINA, "%02d-%02d", currentMonth.get(Calendar.MONTH) + 1, selectedDay);
                        DemoDataHelper.addSchedule(title, dateKey, time, note, "家属");
                        refreshCalendar();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startSpeechInput(EditText target) {
        activeVoiceInput = target;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出日程内容");
        speechLauncher.launch(intent);
    }

    private GradientDrawable createRoundedBg(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }
}
