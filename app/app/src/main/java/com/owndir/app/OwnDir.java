package com.owndir.app;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import android.net.Uri;

import java.security.SecureRandom;
import java.util.Base64;
import java.io.IOException;
import java.net.ServerSocket;

@Entity(tableName = "owndir")
public class OwnDir {
    @PrimaryKey(autoGenerate = true)
    public int id = 0;

    @NonNull
    public String dir;
    public String name;

    public int port;
    public String token;
    public String cookie; // we'll see if we get this far


    public OwnDir(@NonNull String dir, int port, String token, String cookie, String name) {
        this.dir = dir;
        this.port = port;
        this.token = token;
        this.cookie = cookie;
        this.name = name;
    }

    public OwnDir(@NonNull Uri uri) {
        this(
            uri.toString(),
            generateOpenPort(),
            generateSecureToken(),
            "",
            uri.getLastPathSegment().replaceAll("^[^:]*:", "")
        );
    }

    public Uri getDir() {
        return Uri.parse(dir);
    }

    public Uri getUrl () {
        Boolean useToken = token != null && !token.trim().isEmpty();
        return Uri.parse(
                "http://localhost:" + port +
                        (useToken ? ('/' + token) : "")
        );
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




}
