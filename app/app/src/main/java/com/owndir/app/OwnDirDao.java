package com.owndir.app;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.owndir.app.OwnDir;

import java.util.List;


@Dao
public interface OwnDirDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(OwnDir ownDir);

    @Query("SELECT * FROM owndir WHERE dir = :dir")
    OwnDir findByToken(String dir);

    @Query("SELECT * FROM owndir")
    List<OwnDir> getAll();

    @Delete
    void delete(OwnDir ownDir);

    @Query("DELETE FROM owndir")
    void deleteAll();
}
