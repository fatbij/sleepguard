package com.sleepguard.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface SleepDao {

    @Insert
    long insertSession(SleepSession session);

    @Update
    void updateSession(SleepSession session);

    @Query("SELECT * FROM sleep_sessions ORDER BY session_start ASC")
    List<SleepSession> getAllSessions();

    @Query("SELECT * FROM sleep_sessions WHERE id = :id")
    SleepSession getSessionById(long id);

    @Query("SELECT * FROM sleep_sessions WHERE session_end = 0 LIMIT 1")
    SleepSession getActiveSession();

    @Query("SELECT * FROM sleep_sessions ORDER BY session_start DESC LIMIT 1")
    SleepSession getLatestSession();

    @Insert
    long insertEpisode(WakeEpisodeRecord episode);

    @Update
    void updateEpisode(WakeEpisodeRecord episode);

    @Query("SELECT * FROM wake_episodes WHERE session_id = :sessionId ORDER BY start_ms ASC")
    List<WakeEpisodeRecord> getEpisodesForSession(long sessionId);

    @Query("SELECT * FROM wake_episodes WHERE session_id = :sessionId AND end_ms = 0 LIMIT 1")
    WakeEpisodeRecord getActiveEpisode(long sessionId);

    @Query("SELECT COUNT(*) FROM wake_episodes WHERE session_id = :sessionId")
    int getEpisodeCount(long sessionId);

    @Query("DELETE FROM wake_episodes WHERE session_id = :sessionId AND id = (SELECT id FROM wake_episodes WHERE session_id = :sessionId ORDER BY start_ms DESC LIMIT 1)")
    void deleteLatestEpisode(long sessionId);

    @Delete
    void deleteSession(SleepSession session);

    @Query("DELETE FROM wake_episodes WHERE session_id = :sessionId")
    void deleteEpisodesForSession(long sessionId);

    @Query("SELECT COUNT(*) FROM wake_episodes")
    int getTotalEpisodeCount();
}
