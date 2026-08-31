package com.carelink.app.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.carelink.app.R;
import com.carelink.app.ui.alert.AlertCenterFragment;
import com.carelink.app.ui.family.FamilyMainActivity;

/**
 * 通知工具类
 * 渠道：routine_channel / alert_channel / emergency_channel
 */
public class NotificationHelper {

    public static final String CHANNEL_ROUTINE = "routine_channel";
    public static final String CHANNEL_ALERT = "alert_channel";
    public static final String CHANNEL_EMERGENCY = "emergency_channel";

    private static int notificationId = 1000;

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);

            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ROUTINE, "日常提醒", NotificationManager.IMPORTANCE_DEFAULT));

            NotificationChannel alertChannel = new NotificationChannel(
                    CHANNEL_ALERT, "异常告警", NotificationManager.IMPORTANCE_HIGH);
            alertChannel.enableVibration(true);
            nm.createNotificationChannel(alertChannel);

            NotificationChannel emergencyChannel = new NotificationChannel(
                    CHANNEL_EMERGENCY, "紧急求助", NotificationManager.IMPORTANCE_HIGH);

            emergencyChannel.enableVibration(true);
            emergencyChannel.setBypassDnd(true);
            nm.createNotificationChannel(emergencyChannel);
        }
    }

    public static void showNotification(Context context, String type, String title, String body) {
        String channel = CHANNEL_ROUTINE;
        if ("alert".equalsIgnoreCase(type)) channel = CHANNEL_ALERT;
        else if ("emergency".equalsIgnoreCase(type)) channel = CHANNEL_EMERGENCY;

        Intent intent = new Intent(context, FamilyMainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi);

        if (CHANNEL_EMERGENCY.equals(channel)) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX);
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(notificationId++, builder.build());
    }

    public static void showAlertNotification(Context context, String alertType,
                                              String description, int level) {
        String type = level >= 3 ? "emergency" : "alert";
        showNotification(context, type, "照护异常提醒", description);
    }
}
