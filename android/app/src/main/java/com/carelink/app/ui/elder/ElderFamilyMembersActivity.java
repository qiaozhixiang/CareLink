package com.carelink.app.ui.elder;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.carelink.app.R;
import com.carelink.app.ui.family.MemberFragment;
import com.carelink.app.utils.FontScaleHelper;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ElderFamilyMembersActivity extends AppCompatActivity {

    private int memberContainerId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_page));

        root.addView(createTopBar());

        FrameLayout container = new FrameLayout(this);
        memberContainerId = View.generateViewId();
        container.setId(memberContainerId);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        container.setLayoutParams(containerParams);
        root.addView(container);

        setContentView(root);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(memberContainerId, MemberFragment.newInstance(true))
                    .commit();
        }
    }

    private View createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = dp(16);
        int vertical = dp(12);
        bar.setPadding(horizontal, vertical, horizontal, vertical);
        bar.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_card));

        Button backButton = new Button(this);
        backButton.setAllCaps(false);
        backButton.setText("返回");
        backButton.setTextSize(FontScaleHelper.body(this));
        backButton.setOnClickListener(v -> finish());
        bar.addView(backButton);

        TextView title = new TextView(this);
        title.setText("家庭成员");
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(FontScaleHelper.title(this));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        titleParams.leftMargin = dp(12);
        title.setLayoutParams(titleParams);
        bar.addView(title);

        return bar;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
}
