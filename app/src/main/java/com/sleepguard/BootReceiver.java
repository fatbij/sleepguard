package com.sleepguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Only restart if the user had it active
            SharedPreferences prefs = context.getSharedPreferences("sleepguard", Context.MODE_PRIVATE);
            if (prefs.getBoolean("active", false)) {
                Intent service = new Intent(context, TimerService.class);
                service.setAction("START");
                context.startForegroundService(service);
            }
        }
    }
}
