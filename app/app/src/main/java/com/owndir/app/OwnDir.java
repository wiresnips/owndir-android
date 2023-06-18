package com.owndir.app;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.io.IOException;
import java.net.ServerSocket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Entity(tableName = "owndir")
public class OwnDir implements Parcelable {
    @PrimaryKey(autoGenerate = true)
    public int id = 0;

    @NonNull
    public String dir;
    public int port;
    public String token;

    @Ignore
    public boolean isRunningBuild = false;
    @Ignore
    public boolean isRunningServer = false;
    @Ignore
    public boolean isServerUp = false;


    public static String appDir;

    public OwnDir(@NonNull String dir, int port, String token) {
        this.dir = dir;
        this.port = port;
        this.token = token;
    }

    public OwnDir(String absPath) {
        this(absPath,
            generateOpenPort(),
            generateSecureToken()
        );
    }

    public Uri getDir() {
        return Uri.parse(dir);
    }

    public Uri getUrl () {
        boolean useToken = token != null && !token.trim().isEmpty();
        useToken = false; // for now, tokens are just broken

        return Uri.parse(
    "http://localhost:" + port +
            (useToken ? ('/' + token) : "")
        );
    }

    public String getName () {
        String[] splitPath = dir.split(File.separator);
        return splitPath[splitPath.length - 1];
    }


    private String getHash () {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            messageDigest.update(dir.getBytes(StandardCharsets.UTF_8));
            byte[] digest = messageDigest.digest();
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating hash", e);
        }
    }

    public String getLogFilePath() {
        return appDir + File.separator + getHash() + ".log";
    }
    public File getLogFile() { return new File(getLogFilePath()); }

    public String getJsDir () {
        return appDir + File.separator + "owndir";
    }
    public String getBuildDir () {
        return getJsDir() + File.separator + "build" + File.separator + getHash();
    }

    public boolean isBuilt () {
        File moduleFile = new File(getBuildDir() + File.separator + "module" + File.separator + "index.js");
        File serverBundle = new File(getBuildDir() + File.separator + "server" + File.separator + "dist.js");
        File clientBundle = new File(getBuildDir() + File.separator + "client" + File.separator + "dist.js");
        return (
            moduleFile.exists() && moduleFile.isFile() &&
            serverBundle.exists() && serverBundle.isFile() &&
            clientBundle.exists() && clientBundle.isFile()
        );
    }

    public boolean pingServer() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(getUrl().toString());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(250);
            connection.setReadTimeout(250);
            connection.connect();
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }







    public static String generateSecureToken() {
        SecureRandom secureRandom = new SecureRandom();
        Base64.Encoder base64Encoder = Base64.getUrlEncoder();
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }

    public static int generateOpenPort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            return port;
        } catch (IOException e) {
            throw new RuntimeException("Failed to find an available port", e);
        }
    }


    public String[] runCmd() {
        String srcPath = getJsDir() + File.separator + "src" + File.separator + "index.js";

        ArrayList<String> args = new ArrayList<>();
        args.add("node");
        args.add(srcPath);

        args.add("-p");
        args.add("" + port);


        /*
        // this doesn't work conceptually as well as I'd thought
        if (token != null && token.length() > 0) {
            args.add("-t");
            args.add(token);
        }
        //*/

        args.add(dir);

        return args.toArray(new String[args.size()]);
    }

    public String[] buildCmd() {
        String srcPath = getJsDir() + File.separator + "src" + File.separator + "index.js";
        return new String[] {"node", srcPath, "--build", "--run", "false", dir};
    }


    @NonNull
    @Override
    public String toString() {
        return ("{" +
                "id: " + id + ", " +
                "dir: " + dir + ", " +
                "build?: " + (isRunningBuild ? "true" : "false") + ", " +
                "serve?: " + (isRunningServer ? "true" : "false") + ", " +
                "visible?: " + (isServerUp ? "true" : "false") + ", " +
        "}");
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(dir);
        dest.writeInt(port);
        dest.writeString(token);
        dest.writeString(appDir);
        dest.writeBoolean(isRunningBuild);
        dest.writeBoolean(isRunningServer);
        dest.writeBoolean(isServerUp);
    }

    public static final Parcelable.Creator<OwnDir> CREATOR = new Parcelable.Creator<OwnDir>() {
        public OwnDir createFromParcel(Parcel in) {
            return new OwnDir(in);
        }

        public OwnDir[] newArray(int size) {
            return new OwnDir[size];
        }
    };

    private OwnDir(Parcel in) {
        id = in.readInt();
        dir = in.readString();
        port = in.readInt();
        token = in.readString();
        appDir = in.readString();
        isRunningBuild = in.readBoolean();
        isRunningServer = in.readBoolean();
        isServerUp = in.readBoolean();
    }



}
