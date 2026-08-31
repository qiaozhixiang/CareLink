package com.carelink.app.ui.calendar;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.carelink.app.utils.FontScaleHelper;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class PerpetualCalendarActivity extends AppCompatActivity {
    private Calendar currentMonth;
    private int selectedDay;
    private TextView titleView;
    private TextView selectedInfoView;
    private TextView lunarInfoView;
    private TextView festivalInfoView;
    private TextView yiJiInfoView;
    private LinearLayout calendarContainer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentMonth = Calendar.getInstance(Locale.CHINA);
        selectedDay = currentMonth.get(Calendar.DAY_OF_MONTH);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(0xFFFFFBF5);
        scrollView.addView(root);

        titleView = new TextView(this);
        titleView.setTextSize(FontScaleHelper.title(this));
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(0, 0, 0, 16);
        root.addView(titleView);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setBackground(createRoundedBg(0xFFFFEFD5, 24));
        controls.setPadding(12, 12, 12, 12);
        controls.addView(createBtn("<< 上月", v -> { currentMonth.add(Calendar.MONTH, -1); selectedDay = 1; renderCalendar(); }));
        controls.addView(createBtn("今天", v -> {
            currentMonth = Calendar.getInstance(Locale.CHINA);
            selectedDay = currentMonth.get(Calendar.DAY_OF_MONTH);
            renderCalendar();
        }));
        controls.addView(createBtn("下月 >>", v -> { currentMonth.add(Calendar.MONTH, 1); selectedDay = 1; renderCalendar(); }));
        root.addView(controls);

        selectedInfoView = createInfoCard(root, 0xFFFFF3E0);
        lunarInfoView = createInfoCard(root, 0xFFFFF8E1);
        festivalInfoView = createInfoCard(root, 0xFFE8F5E9);
        yiJiInfoView = createInfoCard(root, 0xFFE3F2FD);

        root.addView(createWeekHeader());

        calendarContainer = new LinearLayout(this);
        calendarContainer.setOrientation(LinearLayout.VERTICAL);
        calendarContainer.setBackground(createRoundedBg(0xFFFFFFFF, 24));
        LinearLayout.LayoutParams calParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        calParams.topMargin = 12;
        root.addView(calendarContainer, calParams);

        setContentView(scrollView);
        renderCalendar();
    }

    private TextView createBtn(String text, android.view.View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(FontScaleHelper.body(this));
        tv.setPadding(18, 10, 18, 10);
        tv.setBackground(createRoundedBg(0xFFFFFFFF, 18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(6, 0, 6, 0);
        tv.setLayoutParams(params);
        tv.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);
        tv.setOnClickListener(listener);
        return tv;
    }

    private TextView createInfoCard(LinearLayout root, int bgColor) {
        TextView tv = new TextView(this);
        tv.setTextSize(FontScaleHelper.body(this));
        tv.setPadding(20, 20, 20, 20);
        tv.setBackground(createRoundedBg(bgColor, 22));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 12;
        tv.setLayoutParams(params);
        root.addView(tv);
        return tv;
    }

    private LinearLayout createWeekHeader() {
        String[] weeks = {"日", "一", "二", "三", "四", "五", "六"};
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 12, 0, 8);
        row.setBackground(createRoundedBg(0xFFFFF8E1, 18));
        for (String week : weeks) row.addView(createCell(week, true, false, false));
        return row;
    }

    private void renderCalendar() {
        titleView.setText(String.format(Locale.CHINA, "%d年%d月 万年历", currentMonth.get(Calendar.YEAR), currentMonth.get(Calendar.MONTH) + 1));
        calendarContainer.removeAllViews();

        Calendar temp = (Calendar) currentMonth.clone();
        temp.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = temp.get(Calendar.DAY_OF_WEEK);
        int offset = firstDayOfWeek - Calendar.SUNDAY;
        int daysInMonth = temp.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar today = Calendar.getInstance(Locale.CHINA);

        int day = 1;
        for (int rowIndex = 0; rowIndex < 6 && day <= daysInMonth; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 7; col++) {
                TextView cell;
                if (rowIndex == 0 && col < offset) {
                    cell = createCell("", false, false, false);
                } else if (day <= daysInMonth) {
                    boolean isToday = today.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR)
                            && today.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)
                            && today.get(Calendar.DAY_OF_MONTH) == day;
                    boolean isSelected = day == selectedDay;
                    cell = createCell(String.valueOf(day), false, isSelected, isToday);
                    final int currentDay = day;
                    cell.setOnClickListener(v -> {
                        selectedDay = currentDay;
                        renderCalendar();
                    });
                    day++;
                } else {
                    cell = createCell("", false, false, false);
                }
                row.addView(cell);
            }
            calendarContainer.addView(row);
        }
        updateSelectedInfo();
    }

    private TextView createCell(String text, boolean header, boolean selected, boolean today) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);
        tv.setTextSize(header ? FontScaleHelper.secondary(this) : FontScaleHelper.body(this));
        tv.setPadding(0, 24, 0, 24);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (selected) {
            tv.setBackground(createRoundedBg(0xFFFFC107, 16));
            tv.setTextColor(0xFF5D4037);
        } else if (today) {
            tv.setBackground(createRoundedBg(0xFFC8E6C9, 16));
            tv.setTextColor(0xFF1B5E20);
        } else if (!header) {
            tv.setBackground(createRoundedBg(0xFFFFFDF7, 16));
        }
        return tv;
    }

    private void updateSelectedInfo() {
        int year = currentMonth.get(Calendar.YEAR);
        int month = currentMonth.get(Calendar.MONTH) + 1;
        int day = selectedDay;
        String fullDate = String.format(Locale.CHINA, "%d年%d月%d日", year, month, day);
        Calendar selected = (Calendar) currentMonth.clone();
        selected.set(Calendar.DAY_OF_MONTH, selectedDay);
        String week = new SimpleDateFormat("EEEE", Locale.CHINA).format(selected.getTime());
        String todayText = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA).format(new Date());

        Solar solar = Solar.fromYmd(year, month, day);
        Lunar lunar = solar.getLunar();

        selectedInfoView.setText("当前选中：" + fullDate + "  " + week + "\n今日日期：" + todayText);
        lunarInfoView.setText("农历：" + lunar.getMonthInChinese() + "月" + lunar.getDayInChinese() +
                "\n天干地支：" + lunar.getYearInGanZhi() + "年 " + lunar.getMonthInGanZhi() + "月 " + lunar.getDayInGanZhi() + "日" +
                "\n生肖：" + lunar.getYearShengXiao());

        String festivals = "节日：" + (lunar.getFestivals().isEmpty() ? "无" : lunar.getFestivals().toString()) +
                "\n公历节日：" + (solar.getFestivals().isEmpty() ? "无" : solar.getFestivals().toString()) +
                "\n节气：" + (lunar.getJieQi() == null ? "无" : lunar.getJieQi());
        festivalInfoView.setText(festivals);

        yiJiInfoView.setText("宜：" + String.join("、", lunar.getDayYi()) +
                "\n忌：" + String.join("、", lunar.getDayJi()));
    }

    private GradientDrawable createRoundedBg(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }
}
