/*
 * Copyright (c) 2017, Roman Sisik
 * All rights reserved.
 * See LICENSE for more information.
 */

package com.owndir.app;

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
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import android.os.Parcel;
import android.os.Parcelable;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;


public class NodeService extends Service {

    static {
        System.loadLibrary("node");
        System.loadLibrary("node-bridge");
    }
    private native int startNode(String logfile, String... argv);


    public static final String BUILD = "node.broadcast.build";
    public static final String KILL_BUILD = "node.broadcast.kill_build";
    public static final String SERVE = "node.broadcast.serve";
    public static final String KILL_SERVE = "node.broadcast.kill_serve";

    public static final String STATUS = "node.broadcast.status";
    public static final int NOTIFICATION_ID = 6667;
    public static final String notification_channel_id = "owndir_notification_channel";


    public enum Task { BUILD, SERVE };
    private class NodeFuture extends FutureTask<Integer> {
        public final int ownDirId;
        public final Task type;
        public Integer exitCode;

        public NodeFuture (Callable<Integer> callable, Task task, OwnDir ownDir) {
            super(callable);
            this.ownDirId = ownDir.id;
            this.type = task;
        }


        @Override
        protected void done() {
            try {
                exitCode = get();
                broadcastStatusUpdate();
            } catch (InterruptedException | ExecutionException | CancellationException e) {
                // Handle exceptions as needed
                e.printStackTrace();
            }
        }
    }

    private ExecutorService threadPool;
    protected Map<Integer, NodeFuture> nodeThreads = new HashMap<>();



    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("OwnDir", "NodeService onCreate");

        threadPool = Executors.newCachedThreadPool();

        try {
            Os.setenv("TMP", getTmpDir().getAbsolutePath(), true);
        } catch (ErrnoException ex) {
            Log.e("OwnDir", "Error setting TMP", ex);
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("OwnDir", "NodeService onDestroy");
        // deleteRecursive(getTmpDir());

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { return START_STICKY; }

        String action = intent.getAction();

        if (action == null) { return START_STICKY; }
        if (action.equals(STATUS)) {
            broadcastStatusUpdate();
            return START_STICKY;
        }

        OwnDir ownDir = intent.getParcelableExtra("ownDir");

        Log.d("OwnDir", "NodeService " + action + " : " + ownDir.toString());

        if (action.equals(BUILD))      { runBuild(ownDir); }
        if (action.equals(SERVE))      { runServe(ownDir); }
        if (action.equals(KILL_BUILD)) { killBuild(ownDir); }
        if (action.equals(KILL_SERVE)) { killServe(ownDir); }

        return START_STICKY;
    }

    public static class ThreadStatus implements Parcelable {
        public Task type;
        public boolean cancelled;
        public boolean done;
        public Integer exitCode;

        public ThreadStatus (NodeFuture thread) {
            this.type = thread.type;
            this.cancelled = thread.isCancelled();
            this.done = thread.isDone();
            this.exitCode = thread.exitCode;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(type.ordinal());
            dest.writeBoolean(cancelled);
            dest.writeBoolean(done);
            dest.writeInt(exitCode != null ? exitCode : 0);
        }
        private ThreadStatus(Parcel in) {
            type = Task.values()[in.readInt()];
            cancelled = in.readBoolean();
            done = in.readBoolean();
            exitCode = done ? in.readInt() : null;
        }

        public static final Parcelable.Creator<ThreadStatus> CREATOR = new Parcelable.Creator<ThreadStatus>() {
            public ThreadStatus createFromParcel(Parcel in) { return new ThreadStatus(in); }
            public ThreadStatus[] newArray(int size) { return new ThreadStatus[size]; }
        };
    }

    public void broadcastStatusUpdate () {
        Bundle update = new Bundle();
        for (Map.Entry<Integer, NodeFuture> entry : nodeThreads.entrySet()) {
            update.putParcelable("" + entry.getKey(), new ThreadStatus(entry.getValue()));
        }

        Intent intent = new Intent(STATUS);
        intent.putExtra("update", update);
        sendBroadcast(intent);
    }


    private void runBuild (OwnDir ownDir) {
        NodeFuture existingTask = nodeThreads.get(ownDir.id);
        if (existingTask != null && !existingTask.isDone()) {
            if (existingTask.type == Task.BUILD) { return; }
            if (existingTask.type == Task.SERVE) {
                existingTask.cancel(true);
            }
        }

        NodeFuture build = new NodeFuture(() -> {
            Log.d("OwnDir", "build: " + ownDir.dir);
            Log.d("OwnDir", String.join(" ", ownDir.buildCmd()));
            Integer result = startNode(ownDir.getLogFilePath(), ownDir.buildCmd());
            broadcastStatusUpdate();

            if (ownDir.enabled && result == 0) {
                runServe(ownDir);
            }
            return result;
        }, Task.BUILD, ownDir);

        threadPool.submit(build);
        nodeThreads.put(ownDir.id, build);

        broadcastStatusUpdate();
    }

    private void killBuild (OwnDir ownDir) {
        NodeFuture existingTask = nodeThreads.get(ownDir.id);
        if (existingTask != null && !existingTask.isDone()) {
            if (existingTask.type == Task.BUILD) {
                existingTask.cancel(true);
            }
        }
        broadcastStatusUpdate();
    }


    private void runServe (OwnDir ownDir) {
        NodeFuture existingTask = nodeThreads.get(ownDir.id);
        if (existingTask != null && !existingTask.isDone()) {
            if (existingTask.type == Task.BUILD) { return; }
            if (existingTask.type == Task.SERVE) {
                existingTask.cancel(true);
            }
        }

        NodeFuture serve = new NodeFuture(() -> {
            Log.d("OwnDir", "serve: " + ownDir.dir);
            Log.d("OwnDir", String.join(" ", ownDir.runCmd()));
            Integer result = startNode(ownDir.getLogFilePath(), ownDir.runCmd());
            broadcastStatusUpdate();
            return result;
        }, Task.SERVE, ownDir);

        threadPool.submit(serve);
        nodeThreads.put(ownDir.id, serve);
        broadcastStatusUpdate();
    }

    private void killServe (OwnDir ownDir) {
        NodeFuture existingTask = nodeThreads.get(ownDir.id);
        if (existingTask != null && !existingTask.isDone()) {
            if (existingTask.type == Task.SERVE) {
                existingTask.cancel(true);
            }
        }
        broadcastStatusUpdate();
    }
















    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notification_channel_id, "OwnDir NodeJs",  NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("channel_description");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }


    private Notification createNotification() {
        Log.d("OwnDir", "NodeService createNotification");

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        return new Notification.Builder(this, notification_channel_id)
                .setContentTitle(this.getText(R.string.app_name))
                .setContentText(this.getText(R.string.app_name))
                .setSmallIcon(R.drawable.ic_node_service_icon)
                .setContentIntent(pendingIntent)
                .setTicker(this.getText(R.string.app_name))
                .setPriority(Notification.PRIORITY_MIN)
                .build();
    }




    public File getTmpDir () {
        File tmpDir = new File(getFilesDir().getAbsolutePath() + File.separator + "tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        return tmpDir;
    }

    void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }









}
