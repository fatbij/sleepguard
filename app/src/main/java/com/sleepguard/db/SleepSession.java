package com.sleepguard.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sleep_sessions")
public class SleepSession {
    @PrimaryKey
    public long id;                          // epoch-ms timestamp, used as unique key

    public String date;                      // "yyyy-MM-dd"

    @ColumnInfo(name = "sleep_time")
    public String sleepTime;                 // "HH:mm"

    @ColumnInfo(name = "wake_time")
    public String wakeTime;                  // "HH:mm", empty if in progress

    @ColumnInfo(name = "session_start")
    public long sessionStart;

    @ColumnInfo(name = "session_end")
    public long sessionEnd;                  // 0 if in progress

    public int rating;                       // sleep quality 1-5, 0 = unrated
    @ColumnInfo(name = "rested_rating")
    public int restedRating;                 // restedness 1-5, 0 = unrated
    @ColumnInfo(name = "onset_mins")
    public int onsetMins;                    // estimated mins to fall asleep

    public String notes;
}
