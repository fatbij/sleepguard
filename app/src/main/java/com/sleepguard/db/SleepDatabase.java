package com.sleepguard.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {SleepSession.class, WakeEpisodeRecord.class}, version = 2)
public abstract class SleepDatabase extends RoomDatabase {

    private static volatile SleepDatabase instance;

    public abstract SleepDao sleepDao();

    public static SleepDatabase getInstance(Context ctx) {
        if (instance == null) {
            synchronized (SleepDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            ctx.getApplicationContext(),
                            SleepDatabase.class,
                            "sleep_db")
                        .fallbackToDestructiveMigration()
                        .build();
                }
            }
        }
        return instance;
    }
}
