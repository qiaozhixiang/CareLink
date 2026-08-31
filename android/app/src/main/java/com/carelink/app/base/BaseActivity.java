package com.carelink.app.base;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewbinding.ViewBinding;

/**
 * Activity 基类，统一处理公共逻辑
 */
public abstract class BaseActivity<VB extends ViewBinding> extends AppCompatActivity {

    protected VB binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = inflateBinding();
        setContentView(binding.getRoot());
        initView();
        observeViewModel();
    }

    /** 子类实现 ViewBinding inflate */
    protected abstract VB inflateBinding();

    /** 初始化 View */
    protected abstract void initView();

    /** 观察 ViewModel */
    protected void observeViewModel() {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
