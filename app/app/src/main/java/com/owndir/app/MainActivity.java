package com.owndir.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final int dirPickerRequestCode = 23854;

    private RecyclerView dirList;
    private OwnDirListItemAdapter adapter;
    private FloatingActionButton newDirButton;

    private Db db;


    private BroadcastReceiver nodeStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if ( action.equals(NodeService.BROADCAST_FINISHED) ||
                    action.equals(NodeService.BROADCAST_STARTED) ) {
                updateView();
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dirList = findViewById(R.id.dir_list);
        newDirButton = findViewById(R.id.new_dir_button);

        db = Db.getDatabase(this);

        Db.databaseWriteExecutor.execute(() -> {
            List<OwnDir> directories = db.dirInfoDao().getAll();

            runOnUiThread(() -> {
                adapter = new OwnDirListItemAdapter(this, directories);
                dirList.setAdapter(adapter);
                dirList.setLayoutManager(new LinearLayoutManager(this));
            });
        });




        newDirButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchDirectoryPicker();

                /*
                Log.d("OwnDir", "click start!");

                Intent i = new Intent(MainActivity.this, NodeService.class);
                if (!NodeService.isServiceRunning(MainActivity.this))
                    startService(i);
                else
                    stopService(i);

                */
            }
        });
 //*/
    }


    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter intentFilter = new IntentFilter(NodeService.BROADCAST_STARTED);
        intentFilter.addAction(NodeService.BROADCAST_FINISHED);
        registerReceiver(nodeStatusReceiver, intentFilter);
        updateView();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(nodeStatusReceiver);
    }

    private void updateView() {
        /*
        if (NodeService.isServiceRunning(this)) {
            startButton.setText(R.string.button_text_stop);
        }
        else {
            startButton.setText(R.string.button_text_start);
        }
        */
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
                getContentResolver().takePersistableUriPermission(
                        dirUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
                addDirectory(dirUri);
            }
        }
    }

    /*
    private void addConnectionInfo(String token, String uri, int port) {
        ConnectionInfo connectionInfo = new ConnectionInfo(token, uri, port);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.connectionInfoDao().insert(connectionInfo);
            runOnUiThread(() -> {
                // Update the RecyclerView
                adapter.connectionInfoList.add(connectionInfo);
                adapter.notifyItemInserted(adapter.connectionInfoList.size() - 1);
            });
        });
    }
//*/

    public void addDirectory (Uri dir) {
        Log.d("OwnDir", "addDirectory: " + dir.toString());
        OwnDir ownDir = new OwnDir(dir);

        Db.databaseWriteExecutor.execute(() -> {
            db.dirInfoDao().insert(ownDir);
            runOnUiThread(() -> { adapter.addDirInfo(ownDir); });
        });
    }






}