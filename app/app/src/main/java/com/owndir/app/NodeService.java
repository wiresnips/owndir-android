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
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.File;


public class NodeService extends Service {

    static {
        System.loadLibrary("node");
        System.loadLibrary("node-bridge");
    }
    private native int startNode(String logfile, String... argv);


    public static final int NOTIFICATION_ID = 6667;
    public static final String notification_channel_id = "owndir_notification_channel";


    public static final String RUN = "com.owndir.app.nodejs.run";
    public static final String KILL = "com.owndir.app.nodejs.kill";

    public static final String BUSY = "com.owndir.app.nodejs.busy";
    public static final String DESTROYED = "com.owndir.app.nodejs.onDestroy";
    public static final String STATUS = "com.owndir.app.nodejs.status";


    public Thread nodejs;
    public String[] command = new String[]{};

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("OwnDir", "NodeService onCreate");

        try {
            Os.setenv("TMP", getTmpDir().getAbsolutePath(), true);
        } catch (ErrnoException ex) {
            Log.e("OwnDir", "Error setting TMP", ex);
        }

        createNotificationChannel();
        createNotification("", "");
    }

    public void run(String logfilePath, String[] command) {
        boolean alreadyRunning = nodejs != null;

        Log.d("OwnDir",
                "NodeService run: " +
                        "\n\t" + "command: " + String.join(" ", command) +
                        "\n\t" + "logFilePath: " + logfilePath +
                        "\n\t" + "alreadyRunning: " + (alreadyRunning ? String.join(" ", this.command) : "no")
        );

        this.command = command;

        nodejs = new Thread(new Runnable() {
            @Override
            public void run() {
                startNode(logfilePath, command);
                kill();
            }
        });
        nodejs.start();
    }

    public void kill () {
        nodejs = null;
        command = new String[]{};
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("OwnDir", "NodeService onDestroy");
        sendBroadcast(new Intent(DESTROYED));

        // this is the only way I've found to actually _stop_ Node, once it's started
        Process.killProcess(Process.myPid());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    public void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notification_channel_id, "OwnDir NodeJs",  NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("channel_description");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }


    public void createNotification(String title, String text) {
        Log.d("OwnDir", "NodeService createNotification");

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Intent killIntent = new Intent(this, this.getClass());
        killIntent.setAction(KILL);
        PendingIntent killPendingIntent = PendingIntent.getService(this, 0, killIntent, 0);
        NotificationCompat.Action killAction = new NotificationCompat.Action.Builder(
                R.drawable.baseline_cancel_24, "Stop", killPendingIntent).build();

        Notification notification = new NotificationCompat.Builder(this, notification_channel_id)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_node_service_icon)
                .setContentIntent(pendingIntent)
                .setTicker(this.getText(R.string.app_name))
                .setPriority(Notification.PRIORITY_MIN)
                .addAction(killAction)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    public File getTmpDir () {
        File tmpDir = new File(getFilesDir().getAbsolutePath() + File.separator + "tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        return tmpDir;
    }

    /*
    void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }
    //*/

}
