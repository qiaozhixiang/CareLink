package com.carelink.app.ui.shift;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/** 家属责任轮值页面 */
public class ShiftFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        scrollView.addView(root);

        TextView title = new TextView(requireContext());
        title.setText("责任轮值");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 20);
        root.addView(title);

        root.addView(createShiftCard("周一", "大儿子", "负责早晚问候与服药确认"));
        root.addView(createShiftCard("周二", "二女儿", "负责饮食与运动提醒"));
        root.addView(createShiftCard("周三", "大儿子", "负责血压记录核对"));
        root.addView(createShiftCard("周四", "二女儿", "负责复诊与出行安排"));
        root.addView(createShiftCard("周五", "共同负责", "视频联系与周总结"));

        return scrollView;
    }

    private View createShiftCard(String day, String person, String duty) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 20, 24, 20);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 16;
        card.setLayoutParams(params);
        card.setBackgroundColor(0xFFFFFFFF);

        TextView tvDay = new TextView(requireContext());
        tvDay.setText(day + " · " + person);
        tvDay.setTextSize(20);
        card.addView(tvDay);

        TextView tvDuty = new TextView(requireContext());
        tvDuty.setText(duty);
        tvDuty.setTextSize(15);
        tvDuty.setPadding(0, 8, 0, 0);
        card.addView(tvDuty);

        return card;
    }
}
