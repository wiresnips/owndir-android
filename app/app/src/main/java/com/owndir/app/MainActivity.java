package com.owndir.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    public static final int dirPickerRequestCode = 23854;

    public List<OwnDir> ownDirList;

    private RecyclerView listView;
    private OwnDirListItemAdapter adapter;



    private Handler serverPollHandler = new Handler(Looper.getMainLooper());



    private Db db;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("OwnDir", "onReceive " + action);

            if (action == NodeService.DESTROYED) {
                for (OwnDir ownDir : ownDirList) {
                    ownDir.isRunningBuild = false;
                    ownDir.isRunningServer = false;
                    ownDir.isServerUp = false;
                }
            }

            if (action == NodeService.STATUS) {
                OwnDir ownDir = intent.getParcelableExtra("ownDir");
                if (ownDir != null) {
                    for (int i = ownDirList.size() - 1; i >= 0; i--) {
                        if (ownDirList.get(i).id == ownDir.id) {
                            ownDirList.set(i, ownDir);
                        }
                    }
                }
            }

            // this could be refined, but I do not care to.
            adapter.notifyDataSetChanged();
        }
    };





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // this is stupid, but it should work well enough
        OwnDir.appDir = getFilesDir().getAbsolutePath();

        Log.d("OwnDir", "onCreate ");

        SharedPreferences sharedPreferences = getSharedPreferences("app-prefs", MODE_PRIVATE);
        boolean isFirstLaunch = sharedPreferences.getBoolean("isFirstLaunch", true);
        if (isFirstLaunch) {
            copyAssetsToInternalStorage("owndir");
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("isFirstLaunch", false);
            editor.apply();
        }

        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        listView = findViewById(R.id.ownDirList);

        db = Db.getDatabase(this);
        Db.databaseWriteExecutor.execute(() -> {
            OwnDirDao dao = db.ownDirDao();
            ownDirList = dao.getAll();

            Log.d("OwnDir", "OwnDir DB: " +
                    ownDirList.stream().map(OwnDir::toString).collect(Collectors.joining(", ")));

            runOnUiThread(() -> {
                adapter = new OwnDirListItemAdapter(this);
                listView.setAdapter(adapter);
                listView.setLayoutManager(new LinearLayoutManager(this));
            });
        });

        if (!Environment.isExternalStorageManager()) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            Uri uri = Uri.fromParts("package", this.getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem help_button = menu.findItem(R.id.action_help);
        MenuItem add_button = menu.findItem(R.id.action_add_owndir);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_add_owndir) {
            launchDirectoryPicker();
            return true;
        }


        if (id == R.id.action_help) {
            Db.databaseWriteExecutor.execute(() -> {
                OwnDirDao dao = db.ownDirDao();
                List<OwnDir> data = dao.getAll();
                Log.d("OwnDir", "OwnDir DB: " +
                        data.stream().map(OwnDir::toString).collect(Collectors.joining(", ")));
            });

            return true;
        }

        return super.onOptionsItemSelected(item);
    }




    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(NodeService.STATUS);
        filter.addAction(NodeService.BUSY);
        filter.addAction(NodeService.DESTROYED);
        registerReceiver(receiver, filter);

        OwnDirService.status(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(receiver);
    }





    public void launchDirectoryPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        startActivityForResult(Intent.createChooser(i, "Choose directory"), dirPickerRequestCode);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == dirPickerRequestCode) {
            if (data != null) {
                Uri dirUri = data.getData();
                addOwnDir(dirUri);
            }
        }
    }

    public void addOwnDir(Uri dir) {
        String localPath = dir.getPathSegments().stream().collect(Collectors.joining("/")).split(":")[1];
        String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String absolutePath = rootPath + "/" + localPath;

        Log.d("Owndir", "addDirectory: " + absolutePath);

        Db.databaseWriteExecutor.execute(() -> {
            OwnDirDao dao = db.ownDirDao();
            List<OwnDir> existing = dao.getAll();
            for (OwnDir ownDir : existing) {
                if (ownDir.dir.equals(absolutePath)) {
                    String error = "There is already an OwnDir at:\n" + absolutePath;
                    Log.d("Owndir", error);
                    runOnUiThread(() -> { Toast.makeText(this, error, Toast.LENGTH_LONG).show(); });
                    return;
                }
            }

            OwnDir ownDir = new OwnDir(absolutePath);
            db.ownDirDao().insert(ownDir);
            ownDirList.add(ownDir);
            runOnUiThread(() -> { adapter.notifyItemInserted(ownDirList.size() - 1); });
        });
    }

    public void removeOwnDir(OwnDir ownDir) {
        Log.d("Owndir", "removeDirectory: " + ownDir.dir);
        Toast.makeText(this, "Removed " + ownDir.dir + " from this list.\n" + "(the directory has not been deleted)", Toast.LENGTH_SHORT).show();

        for (int i = ownDirList.size() - 1; i >= 0; i--) {
            if (ownDirList.get(i).id == ownDir.id) {
                ownDirList.remove(i);
                adapter.notifyItemRemoved(i);
            }
        }

        Db.databaseWriteExecutor.execute(() -> { db.ownDirDao().delete(ownDir); });
        OwnDirService.kill(this, ownDir);

    }











    private void copyAssetsToInternalStorage(String path) {
        //Log.d("OwnDir", "copyAssetsToInternalStorage: " + path);

        AssetManager assetManager = getAssets();
        String[] files = null;

        try {
            files = assetManager.list(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (files != null) {
            for (String file : files) {
                InputStream in = null;
                OutputStream out = null;
                try {
                    in = assetManager.open(path + File.separator + file);
                    File outFile = new File(getFilesDir(), path + File.separator + file);
                    outFile.getParentFile().mkdirs();
                    out = new FileOutputStream(outFile);
                    // Log.d("OwnDir", "copyAssetsToInternalStorage: writing: " + outFile.getAbsolutePath());
                    copyFile(in, out);
                } catch (IOException e) {
                    // If it's a directory, call the method recursively
                    copyAssetsToInternalStorage(path + File.separator + file);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }


    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }




}