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

    private Handler handler;
    private Runnable ticker;
    private boolean running = false;
    private KeyguardManager keyguardManager;

    private static long sTickCount = 0;
    public static long getTickCount() { return sTickCount; }

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
            startTicker();
        }

        return START_STICKY;
    }

    private void startTicker() {
        ticker = new Runnable() {
            @Override
            public void run() {
                sTickCount++;
                Intent tick = new Intent(ACTION_TICK);
                sendBroadcast(tick);

                if (keyguardManager != null
                        && keyguardManager.isKeyguardLocked()
                        && !LockScreenActivity.isSuppressed()) {
                    launchLockScreen();
                }

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
        PendingIntent openApp = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, TimerService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1,
                stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, "sleepguard")
                .setContentTitle("SleepGuard Active")
                .setContentText("Watching over your sleep")
                .setSmallIcon(R.drawable.ic_moon)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setSilent(true)
                .addAction(0, "Stop", stopPending)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                "sleepguard", "SleepGuard", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Watching over your sleep");
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