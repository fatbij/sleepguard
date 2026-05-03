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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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

    private BroadcastReceiver screenOnReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        powerManager    = (PowerManager)    getSystemService(POWER_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null) ? intent.getAction() : "";

        if ("STOP".equals(action) || ACTION_STOP.equals(action)) {
            stopSession();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        startForeground(1, buildNotification());

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
        stopForeground(true);
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
        startActivity(i);
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
        super.onDestroy();
    }
}
