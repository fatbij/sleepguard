package com.sleepguard;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

public class CheckInAlarmReceiver extends BroadcastReceiver {

    static final String CHANNEL_ID    = "sleepguard_checkin";
    static final String EXTRA_SESSION = "session_id";
    static final int    NOTIF_ID      = 2;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        long sessionId = intent.getLongExtra(EXTRA_SESSION, 0);

        Intent openCheckIn = new Intent(ctx, MorningCheckInActivity.class);
        openCheckIn.putExtra(EXTRA_SESSION, sessionId);
        openCheckIn.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pi = PendingIntent.getActivity(ctx, 0, openCheckIn,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "Sleep Check-in", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Morning prompt to rate your sleep");
        nm.createNotificationChannel(ch);

        nm.notify(NOTIF_ID, new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("How did you sleep?")
            .setContentText("Tap to log last night and rate your sleep.")
            .setSmallIcon(R.drawable.ic_moon)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build());
    }
}
