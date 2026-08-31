package com.carelink.app.di;

import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/**
 * Firebase 模块占位类。
 * 当前未启用 Firebase 相关能力，保留空模块用于兼容既有结构。
 */
@Module
@InstallIn(SingletonComponent.class)
public class FirebaseModule {
}
