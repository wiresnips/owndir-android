package com.owndir.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    public static final int dirPickerRequestCode = 23854;


    private RecyclerView dirList;
    private OwnDirListItemAdapter adapter;
    private FloatingActionButton newDirButton;


    private Db db;
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("OwnDir", "onReceive ");

            Bundle bundle = intent.getBundleExtra("update");
            HashMap<Integer, NodeService.ThreadStatus> updates = new HashMap<>();

            for (String key : bundle.keySet()) {
                int intKey = Integer.parseInt(key);
                updates.put(intKey, bundle.getParcelable(key));


                Log.d("OwnDir", "    " + intKey + ": " + updates.get(intKey).type.name());
                Log.d("OwnDir", "    " + intKey + ": " + updates.get(intKey).done);
                Log.d("OwnDir", "    " + intKey + ": " + updates.get(intKey).cancelled);
                Log.d("OwnDir", "    " + intKey + ": " + updates.get(intKey).exitCode);

            }



            adapter.setStatusMap(updates);
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        dirList = findViewById(R.id.dir_list);
        newDirButton = findViewById(R.id.new_dir_button);

        db = Db.getDatabase(this);

        Db.databaseWriteExecutor.execute(() -> {
            OwnDirDao dao = db.ownDirDao();
            List<OwnDir> data = dao.getAll();

            Log.d("OwnDir", "OwnDir DB: " +
                    data.stream().map(OwnDir::toString).collect(Collectors.joining(", ")));

            runOnUiThread(() -> {
                adapter = new OwnDirListItemAdapter(this, data, dao);
                dirList.setAdapter(adapter);
                dirList.setLayoutManager(new LinearLayoutManager(this));
            });
        });

        startService(new Intent(MainActivity.this, NodeService.class));

        newDirButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchDirectoryPicker();
            }
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
        MenuItem node_toggle = menu.findItem(R.id.node_service_toggle);

        Switch toggleSwitch = node_toggle.getActionView().findViewById(R.id.toggle);
        toggleSwitch.setChecked(isNodeServiceRunning());

        int padding = getResources().getDimensionPixelSize(R.dimen.service_toggle_padding);
        toggleSwitch.setPadding(0, 0, padding, 0);

        toggleSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d("OwnDir", "onCheckChanged " + (isChecked ? "checked" : "unchecked"));

                // Handle switch toggle
                if (isChecked) {
                    if (!isNodeServiceRunning()) {
                        startService(new Intent(MainActivity.this, NodeService.class));
                    }
                } else {
                    stopService(new Intent(MainActivity.this, NodeService.class));
                }
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

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
        registerReceiver(receiver, filter);
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
                addDirectory(dirUri);
            }
        }
    }

    public void addDirectory (Uri dir) {
        String localPath = dir.getPathSegments().stream().collect(Collectors.joining("/")).split(":")[1];
        String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String absolutePath = rootPath + "/" + localPath;

        Log.d("Owndir", "addDirectory: " + absolutePath);

        Db.databaseWriteExecutor.execute(() -> {
            OwnDirDao dao = db.ownDirDao();
            List<OwnDir> existing = dao.getAll();
            for (OwnDir owndir : existing) {
                if (owndir.dir.equals(absolutePath)) {
                    String error = "There is already an OwnDir at:\n" + absolutePath;
                    Log.d("Owndir", error);
                    runOnUiThread(() -> { Toast.makeText(this, error, Toast.LENGTH_LONG).show(); });
                    return;
                }
            }

            OwnDir ownDir = new OwnDir(this, absolutePath);
            db.ownDirDao().insert(ownDir);
            runOnUiThread(() -> { adapter.addDirInfo(ownDir); });
        });
    }

    public void removeDirectory (OwnDir ownDir) {
        Log.d("Owndir", "removeDirectory: " + ownDir.dir);

        Intent intent1 = new Intent(this, NodeService.class);
        intent1.setAction(NodeService.KILL_BUILD);
        intent1.putExtra("ownDir", ownDir);
        startService(intent1);

        Intent intent2 = new Intent(this, NodeService.class);
        intent2.setAction(NodeService.KILL_SERVE);
        intent2.putExtra("ownDir", ownDir);
        startService(intent2);

        Db.databaseWriteExecutor.execute(() -> { db.ownDirDao().delete(ownDir); });
        adapter.removeDirInfo(ownDir);
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




    public boolean isNodeServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (NodeService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }




}