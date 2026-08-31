package com.carelink.app.ui.elder;

import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
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
import com.carelink.app.utils.FontScaleHelper;

/** 老人端乐曲/戏曲播放页 */
public class AudioLibraryFragment extends Fragment {

    private MediaPlayer mediaPlayer;
    private TextView currentPlaying;
    private String currentTrack = "未播放";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        int titleSize = FontScaleHelper.title(requireContext());
        int sectionSize = FontScaleHelper.sectionTitle(requireContext());
        int bodySize = FontScaleHelper.body(requireContext());

        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        scrollView.addView(root);

        TextView title = new TextView(requireContext());
        title.setText("乐曲与戏曲");
        title.setTextSize(titleSize);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        currentPlaying = new TextView(requireContext());
        currentPlaying.setText("当前播放：" + currentTrack);
        currentPlaying.setTextSize(bodySize);
        currentPlaying.setPadding(0, 0, 0, 20);
        root.addView(currentPlaying);

        root.addView(createSection("国风乐曲", new String[]{"高山流水", "春江花月夜", "梅花三弄"}, sectionSize, bodySize));
        root.addView(createSection("经典戏曲", new String[]{"贵妃醉酒", "天仙配", "红灯记"}, sectionSize, bodySize));

        Button stopBtn = new Button(requireContext());
        stopBtn.setText("暂停播放");
        stopBtn.setTextSize(bodySize);
        stopBtn.setOnClickListener(v -> stopPlayback());
        root.addView(stopBtn);
        return scrollView;
    }

    private View createSection(String sectionTitle, String[] tracks, int sectionSize, int bodySize) {
        LinearLayout section = new LinearLayout(requireContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, 0, 0, 24);

        TextView title = new TextView(requireContext());
        title.setText(sectionTitle);
        title.setTextSize(sectionSize);
        title.setPadding(0, 12, 0, 12);
        section.addView(title);

        for (String track : tracks) {
            Button btn = new Button(requireContext());
            btn.setText("播放 · " + track);
            btn.setTextSize(bodySize);
            btn.setAllCaps(false);
            btn.setOnClickListener(v -> playDemoTrack(track));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = 12;
            btn.setLayoutParams(params);
            section.addView(btn);
        }
        return section;
    }

    private void playDemoTrack(String trackName) {
        stopPlayback();
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mediaPlayer = MediaPlayer.create(requireContext(), uri);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
                currentTrack = trackName;
                currentPlaying.setText("当前播放：" + currentTrack);
                Toast.makeText(requireContext(), "开始播放：" + trackName, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "当前设备不支持演示播放", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "播放失败，请稍后再试", Toast.LENGTH_SHORT).show();
        }

    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
        currentTrack = "未播放";
        if (currentPlaying != null) currentPlaying.setText("当前播放：" + currentTrack);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPlayback();
    }
}
