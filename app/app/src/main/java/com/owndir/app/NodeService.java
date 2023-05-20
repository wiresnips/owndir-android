/*
 * Copyright (c) 2017, Roman Sisik
 * All rights reserved.
 * See LICENSE for more information.
 */

package com.owndir.app;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import android.os.Process;

import android.util.Log;

public class NodeService extends Service {

    public static final String BROADCAST_STARTED = "node.broadcast.started";
    public static final String BROADCAST_FINISHED = "node.broadcast.finished";
    public static final int NOTIFICATION_ID = 6667;
    public static final String notification_channel_id = "owndir_notification_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("OwnDir", "NodeService onCreate");

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        //*
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("OwnDir", "startNode!");

                // this puts an error in the log, which is lovely
                startNode("node", "-e", "console.log(\"BAM\")");
                // startNode("node", "-v");


                // Log.d("OwnDir", "startNode returned: " + result);

                // String jsPath = getCacheDir().getAbsolutePath() + "/main.js";
                // Utils.copyAssetFile(getAssets(), "main.js", jsPath);
                // startNode("node", jsPath, "Hello World", String.valueOf(PORT));
            }
        }).start();
         //*/

        sendBroadcast(new Intent(BROADCAST_STARTED));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("OwnDir", "NodeService onDestroy");

        sendBroadcast(new Intent(BROADCAST_FINISHED));

        // This ugly hack is for now necessary to kill node's process
        Process.killProcess(getPid(this, getString(R.string.node_process_name)));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notification_channel_id, "channel_name",  NotificationManager.IMPORTANCE_DEFAULT);
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
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setTicker(this.getText(R.string.app_name))
                .build();
    }

    static {
        System.loadLibrary("node");
        System.loadLibrary("node-bridge");
    }

    public static boolean isServiceRunning(Context context)
    {
        final ActivityManager am = (ActivityManager)context.getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo i: am.getRunningServices(Integer.MAX_VALUE)) {
            if (i.service.getClassName().equals(NodeService.class.getName())) {
                Log.d("OwnDir", "NodeService isServiceRunning TRUE");
                return true;
            }
        }

        Log.d("OwnDir", "NodeService isServiceRunning FALSE");
        return false;
    }

    public static int getPid(Context context, String processName) {
        ActivityManager am = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);

        for (ActivityManager.RunningAppProcessInfo pi: am.getRunningAppProcesses()) {
            if(pi.processName.equals(processName))
                return pi.pid;
        }

        return -1;
    }


    private native void startNode(String... argv);
}
