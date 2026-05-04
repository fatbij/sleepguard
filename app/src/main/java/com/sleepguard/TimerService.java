package com.sleepguard;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.os.PowerManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import com.sleepguard.db.SleepDatabase;
import com.sleepguard.db.SleepSession;
import com.sleepguard.db.WakeEpisodeRecord;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TimerService extends Service {

    public static final String ACTION_TICK = "com.sleepguard.TICK";
    public static final String ACTION_STOP = "com.sleepguard.STOP_FROM_NOTIFICATION";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private Handler handler;
    private Runnable ticker;
    private boolean running = false;
    private KeyguardManager keyguardManager;
    private PowerManager powerManager;

    private static long sTickCount = 0;
    public static long getTickCount() { return sTickCount; }

    private WindowManager windowManager;
    private View overlayTokenView;

    private BroadcastReceiver screenOnReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        powerManager    = (PowerManager)    getSystemService(POWER_SERVICE);
        addOverlayToken();
    }

    // Android 14 (API 34) blocks startActivity() from a background process unless the
    // process has a registered window (isUidPresent check in ActivityStarter). Adding a
    // 1×1 TYPE_APPLICATION_OVERLAY view gives the process a window entry in the WMS so
    // the background-activity-launch exemption for SYSTEM_ALERT_WINDOW applies.
    private void addOverlayToken() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Settings.canDrawOverlays(this)) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            overlayTokenView = new View(this);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSPARENT);
            try { windowManager.addView(overlayTokenView, p); } catch (Exception ignored) {}
        }
    }

    private void removeOverlayToken() {
        if (overlayTokenView != null && windowManager != null) {
            try { windowManager.removeViewImmediate(overlayTokenView); } catch (Exception ignored) {}
            overlayTokenView = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null) ? intent.getAction() : "";

        if ("STOP".equals(action) || ACTION_STOP.equals(action)) {
            stopSession();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, buildNotification());
        }

        if (!running) {
            running = true;
            startSession();
            startTicker();
        }

        return START_STICKY;
    }

    private void startSession() {
        executor.execute(() -> {
            SleepDatabase db = SleepDatabase.getInstance(this);
            if (db.sleepDao().getActiveSession() != null) return;
            SleepSession s = new SleepSession();
            s.id           = System.currentTimeMillis();
            s.date         = new SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(new Date(s.id));
            s.sleepTime    = new SimpleDateFormat("HH:mm",      Locale.UK).format(new Date(s.id));
            s.wakeTime     = "";
            s.sessionStart = s.id;
            s.sessionEnd   = 0;
            s.notes        = "";
            db.sleepDao().insertSession(s);
        });
    }

    private void stopSession() {
        stopTicker();
        executor.execute(() -> {
            SleepDatabase db = SleepDatabase.getInstance(this);
            SleepSession s = db.sleepDao().getActiveSession();
            if (s != null) {
                s.sessionEnd = System.currentTimeMillis();
                s.wakeTime   = new SimpleDateFormat("HH:mm", Locale.UK).format(new Date(s.sessionEnd));
                db.sleepDao().updateSession(s);
                List<WakeEpisodeRecord> eps = db.sleepDao().getEpisodesForSession(s.id);
                for (WakeEpisodeRecord ep : eps) {
                    if (ep.endMs == 0) {
                        ep.endMs   = s.sessionEnd;
                        ep.outcome = "fell_asleep";
                        db.sleepDao().updateEpisode(ep);
                    }
                }
                scheduleDiaryReminder(s.id);
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private void startTicker() {
        // Fire immediately when screen turns on while locked, without waiting for next tick.
        screenOnReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                if (keyguardManager != null && keyguardManager.isKeyguardLocked()
                        && !LockScreenActivity.isSuppressed()
                        && !LockScreenActivity.isShowing
                        && !LockScreenActivity.wakeEpisodeActive) {
                    launchLockScreen();
                }
            }
        };
        registerReceiver(screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));

        ticker = new Runnable() {
            @Override
            public void run() {
                sTickCount++;
                Intent tick = new Intent(ACTION_TICK);
                tick.setPackage(getPackageName());
                sendBroadcast(tick);

                // Only push the lock screen when the display is already on.
                // If we launch while the screen is off the FLAG_TURN_SCREEN_ON
                // window flag would wake the device every tick — instead we rely
                // on showWhenLocked="true" to surface the activity once the user
                // presses the power button themselves.
                if (keyguardManager != null
                        && keyguardManager.isKeyguardLocked()
                        && powerManager != null && powerManager.isInteractive()
                        && !LockScreenActivity.isSuppressed()
                        && !LockScreenActivity.isShowing
                        && !LockScreenActivity.wakeEpisodeActive) {
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
        try {
            startActivity(i);
        } catch (Exception ignored) {}
    }

    private void scheduleDiaryReminder(long sessionId) {
        if (sessionId == 0) return;
        Intent i = new Intent(this, CheckInAlarmReceiver.class);
        i.putExtra(CheckInAlarmReceiver.EXTRA_SESSION, sessionId);
        PendingIntent pi = PendingIntent.getBroadcast(this, CheckInAlarmReceiver.NOTIF_ID,
            i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        long triggerAt  = System.currentTimeMillis() + 30 * 60 * 1000L;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    private void stopTicker() {
        running = false;
        if (ticker != null) handler.removeCallbacks(ticker);
        if (screenOnReceiver != null) {
            try { unregisterReceiver(screenOnReceiver); } catch (Exception ignored) {}
            screenOnReceiver = null;
        }
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
        removeOverlayToken();
        super.onDestroy();
    }
}
