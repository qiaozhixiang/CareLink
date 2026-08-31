package com.carelink.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.carelink.app.worker.WorkScheduler;

/** 开机自启：重新注册 WorkManager 周期任务 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            WorkScheduler.scheduleAll(context);
        }
    }
}
