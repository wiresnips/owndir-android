package com.owndir.app;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {OwnDir.class}, version = 1, exportSchema = false)
public abstract class Db extends RoomDatabase {
    public abstract OwnDirDao ownDirDao();

    private static volatile Db INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;

    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static Db getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (Db.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), Db.class, "app_database").build();
                }
            }
        }
        return INSTANCE;
    }
}
