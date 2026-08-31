package com.carelink.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.carelink.app.notification.NotificationHelper;

/** 提醒广播接收器 */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_REMINDER = "com.carelink.app.REMINDER";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_TYPE = "type";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_REMINDER.equals(intent.getAction())) {
            String title = intent.getStringExtra(EXTRA_TITLE);
            String body = intent.getStringExtra(EXTRA_BODY);
            String type = intent.getStringExtra(EXTRA_TYPE);
            NotificationHelper.showNotification(context, type, title, body);
        }
    }
}
