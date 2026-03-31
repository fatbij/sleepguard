package com.sleepguard;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;

public class TimerService extends Service {

    public static final String ACTION_TICK = "com.sleepguard.TICK";
    public static final String ACTION_STOP = "com.sleepguard.STOP_FROM_NOTIFICATION";
    public static final long   CYCLE_SECONDS = 30 * 60;

    private static long sSecondsLeft = CYCLE_SECONDS;

    private Handler  handler;
    private Runnable ticker;
    private boolean  running = false;
    private KeyguardManager keyguardManager;

    public static long getSecondsLeft() { return sSecondsLeft; }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null) ? intent.getAction() : "";

        if ("STOP".equals(action) || ACTION_STOP.equals(action)) {
            stopTicker();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        startForeground(1, buildNotification());

        if (!running) {
            running = true;
            sSecondsLeft = CYCLE_SECONDS;
            startTicker();
        }

        return START_STICKY;
    }

    private void startTicker() {
        ticker = new Runnable() {
            @Override
            public void run() {
                sSecondsLeft--;
                if (sSecondsLeft <= 0) {
                    sSecondsLeft = CYCLE_SECONDS;
                }

                // Broadcast tick
                Intent tick = new Intent(ACTION_TICK);
                tick.putExtra("secondsLeft", sSecondsLeft);
                sendBroadcast(tick);

                // Only launch lock screen if phone is locked AND not suppressed
                if (keyguardManager != null
                        && keyguardManager.isKeyguardLocked()
                        && !LockScreenActivity.isSuppressed()) {
                    launchLockScreen();
                }

                NotificationManager nm = getSystemService(NotificationManager.class);
                nm.notify(1, buildNotification());

                handler.postDelayed(this, 1000);
            }
        };
        handler.post(ticker);
    }

    private void launchLockScreen() {
        Intent i = new Intent(this, LockScreenActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                   Intent.FLAG_ACTIVITY_SINGLE_TOP |
                   Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    private void stopTicker() {
        running = false;
        if (ticker != null) handler.removeCallbacks(ticker);
    }

    private Notification buildNotification() {
        long m = sSecondsLeft / 60;
        long s = sSecondsLeft % 60;
        String timeStr = String.format(java.util.Locale.UK, "%02d:%02d remaining", m, s);

        PendingIntent openApp = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, TimerService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1,
            stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, "sleepguard")
            .setContentTitle("SleepGuard Active")
            .setContentText(timeStr)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Stop", stopPending)
            .build();
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
            "sleepguard", "SleepGuard Timer", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Keeps the sleep timer running");
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        stopTicker();
        super.onDestroy();
    }
}
