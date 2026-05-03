package com.sleepguard.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "wake_episodes")
public class WakeEpisodeRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "session_id")
    public long sessionId;

    @ColumnInfo(name = "start_ms")
    public long startMs;

    @ColumnInfo(name = "end_ms")
    public long endMs;      // 0 if in progress

    public String outcome;  // "fell_asleep" or "left_bed"
}
