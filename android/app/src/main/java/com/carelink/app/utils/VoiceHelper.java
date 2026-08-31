package com.carelink.app.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

/** 语音播报工具（老人端大字模式辅助） */
public class VoiceHelper {

    private TextToSpeech tts;
    private boolean ready = false;

    public VoiceHelper(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINESE);
                ready = true;
            }
        });
    }

    public void speak(String text) {
        if (ready) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    public void destroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
