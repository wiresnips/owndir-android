
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
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class OwnDirService extends NodeService {

    public static final String BUILD_TAG = "build";
    public static final String SERVE_TAG = "serve";


    // static methods to allow OwnDirService to mediate comms with ITSELF
    // hypothetically, this could manage a stable of "identical" foreground services, but I think one is enough for now

    public static boolean isRunning (Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (OwnDirService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private static void run (Context context, OwnDir ownDir, String[] command, String tag) {
        Log.d("OwnDir", "OwnDirService run: " + String.join(" ", command) + "\n\t" + ownDir.toString());
        Intent intent = new Intent(context, OwnDirService.class);
        intent.setAction(RUN);
        intent.putExtra("command", command);
        intent.putExtra("ownDir", ownDir);
        intent.putExtra("tag", tag);
        context.startForegroundService(intent);
    }
    public static void serve (Context context, OwnDir ownDir) {
        run(context, ownDir, ownDir.runCmd(), SERVE_TAG);
    }

    public static void build (Context context, OwnDir ownDir) {
        run(context, ownDir, ownDir.buildCmd(), BUILD_TAG);
    }

    public static void kill (Context context, OwnDir ownDir) {
        if (isRunning(context)) {
            Intent intent = new Intent(context, OwnDirService.class);
            intent.setAction(KILL);
            if (ownDir != null) {
                intent.putExtra("ownDir", ownDir);
            }
            context.startForegroundService(intent);
        }
    }

    public static void kill (Context context) {
        kill(context, null);
    }















    public OwnDir ownDir;
    


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean alreadyRunning = nodejs != null;

        Log.d("OwnDir",
                "OwnDirService onStartCommand" +
                        "\n\t" + "current: " +
                        "\n\t\t" + "running: " + (alreadyRunning ? "true" : "false") +
                        "\n\t\t" + "command: " + String.join(" ", command) +
                        "\n\t\t" + "owndir: " + (ownDir != null ? ownDir.toString() : "none")
        );


        if (intent == null) { return START_NOT_STICKY; }
        String action = intent.getAction();
        OwnDir newOwndir = intent.getParcelableExtra("ownDir");
        String[] newCommand = intent.getStringArrayExtra("command");
        String tag = intent.getStringExtra("tag");

        Log.d("OwnDir",
                "OwnDirService onStartCommand" +
                        "\n\t" + "new:" +
                        "\n\t\t" + "action: " + action +
                        "\n\t\t" + "command: " + String.join(" ", command) +
                        "\n\t\t" + "owndir: " + (newOwndir != null ? newOwndir.toString() : "none")
        );

        if (KILL.equals(action)) {
            if (newOwndir == null || ownDir == null || newOwndir.id == ownDir.id) {
                kill();
            }
            return START_NOT_STICKY;
        }

        if (alreadyRunning) {
            broadcastStatus();
            sendBroadcast(new Intent(BUSY));
            return START_NOT_STICKY;
        }

        if (RUN.equals(action)) {
            ownDir = newOwndir;
            ownDir.isRunningBuild = BUILD_TAG.equals(tag);
            ownDir.isRunningServer = SERVE_TAG.equals(tag);
            ownDir.isServerUp = false;
            broadcastStatus();

            run(ownDir.getLogFilePath(), newCommand);


            String[] commandWithoutPaths = Arrays.copyOfRange(command, 0, command.length -2);
            commandWithoutPaths[1] = "$(owndir.js)";

            // what a pain in the ass
            createNotification(
                String.join(" ", commandWithoutPaths),
                "    " + command[command.length-1]
            );

            return START_REDELIVER_INTENT;
        }

        return START_NOT_STICKY;
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("OwnDir", "NodeService onDestroy");
        ownDir.isServerUp = false;
        ownDir.isRunningServer = false;
        ownDir.isRunningBuild = false;
        broadcastStatus();
    }

    private void broadcastStatus () {
        Intent intent = new Intent(STATUS);
        intent.putExtra("ownDir", ownDir);
        sendBroadcast(intent);
    }






}
