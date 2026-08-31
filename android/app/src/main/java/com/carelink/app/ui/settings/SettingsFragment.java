package com.carelink.app.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * 兼容旧导航定义的设置页壳层。
 * 当前统一跳转到 SettingsActivity，避免 Fragment/Activity 双实现继续分叉。
 */
public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (getContext() != null) {
            startActivity(new Intent(requireContext(), SettingsActivity.class));
        }
        return new View(requireContext());
    }
}
