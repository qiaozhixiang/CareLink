package com.carelink.app.utils;

/**
 * 方言识别扩展预留结构。
 * 当前版本先使用系统语音识别，后续若接入专业语音服务/SDK，
 * 可在这里统一管理普通话、粤语、四川话等扩展配置。
 */
public class DialectSpeechHelper {

    public static final String DEFAULT_LANGUAGE = "zh-CN";

    public static String getRecognizerLanguage(String dialectCode) {
        if (dialectCode == null || dialectCode.trim().isEmpty()) {
            return DEFAULT_LANGUAGE;
        }
        // 当前先统一回落到系统普通话识别；后续可替换为真实方言引擎映射。
        return DEFAULT_LANGUAGE;
    }
}
