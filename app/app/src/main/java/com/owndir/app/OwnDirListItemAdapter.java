package com.owndir.app;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.Html;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OwnDirListItemAdapter extends RecyclerView.Adapter<OwnDirListItemAdapter.ViewHolder> {

    private OwnDirDao dao;
    private List<OwnDir> ownDirList;
    private Map<Integer, NodeService.ThreadStatus> statusMap = new HashMap<>();
    private LayoutInflater inflater;

    private MainActivity context;


    public OwnDirListItemAdapter(MainActivity context, List<OwnDir> ownDirList, OwnDirDao dao) {
        this.context = context;
        this.dao = dao;
        this.ownDirList = ownDirList;
        this.inflater = LayoutInflater.from(context);
    }

    public void addDirInfo(OwnDir ownDir) {
        ownDirList.add(ownDir);
        notifyItemInserted(ownDirList.size() - 1);
    }

    public void removeDirInfo (OwnDir target) {
        for (int i = ownDirList.size() - 1; i >= 0; i--) {
            if (ownDirList.get(i).id == target.id) {
                ownDirList.remove(i);
                notifyItemRemoved(i);
            }
        }
    }

    public void setStatusMap(Map<Integer, NodeService.ThreadStatus> statusMap) {
        this.statusMap = statusMap;
        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.dir_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OwnDir ownDir = ownDirList.get(position);
        if (ownDir == null) {
            return;
        }
        holder.updateView(ownDir);

        holder.name.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NodeService.ThreadStatus status = statusMap.get(ownDir.id);
                if (status != null && !status.done && status.type == NodeService.Task.SERVE) {
                    context.startActivity(new Intent(Intent.ACTION_VIEW, ownDir.getUrl()));
                }
            }
        });

        holder.serve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, NodeService.class);
                intent.setAction(NodeService.SERVE);
                intent.putExtra("ownDir", ownDir);
                context.startService(intent);
            }
        });


        holder.delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                context.removeDirectory(ownDir);
                removeDirInfo(ownDir);
                Toast.makeText(context, "Removed " + ownDir.dir + " from this list.\n" + "(the directory has not been deleted)", Toast.LENGTH_SHORT).show();
            }
        });

        holder.logs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Uri fileUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", new File(ownDir.getLogFilePath()));
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(Intent.createChooser(intent, "Log"));
            }
        });

        holder.build.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, NodeService.class);
                intent.setAction(NodeService.BUILD);
                intent.putExtra("ownDir", ownDir);
                context.startService(intent);
            }
        });

        holder.buildStatusWaiting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, NodeService.class);
                intent.setAction(NodeService.KILL_BUILD);
                intent.putExtra("ownDir", ownDir);
                context.startService(intent);

            }
        });
    }

    @Override
    public int getItemCount() {
        return ownDirList.size();
    }




    class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, filepath;
        Button build, logs;
        AppCompatImageButton delete;
        // SwitchCompat toggle;
        Button serve;

        ImageView buildStatusGood, buildStatusBad;
        ProgressBar buildStatusWaiting;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.dir_item_name);
            filepath = itemView.findViewById(R.id.dir_item_filepath);
            // toggle = itemView.findViewById(R.id.toggleEnabled);
            serve = itemView.findViewById(R.id.serveButton);
            build = itemView.findViewById(R.id.buildButton);
            logs = itemView.findViewById(R.id.logsButton);
            buildStatusGood = itemView.findViewById(R.id.build_status_indicator_good);
            buildStatusBad= itemView.findViewById(R.id.build_status_indicator_bad);
            buildStatusWaiting= itemView.findViewById(R.id.build_status_indicator_waiting);
            delete = itemView.findViewById(R.id.deleteButton);
        }

        private void updateView (OwnDir ownDir) {
            NodeService.ThreadStatus status = statusMap.get(ownDir.id);
            boolean isBuilding = status != null && !status.done && status.type == NodeService.Task.BUILD;
            boolean isRunning = status != null && !status.done && status.type == NodeService.Task.SERVE;

            // subtitle filepath
            filepath.setText(ownDir.getDir().toString());

            // name
            if (!isRunning) {
                name.setText(ownDir.getName());
                name.setTextColor(Color.parseColor("#000000AA"));
            } else {
                name.setText(Html.fromHtml("<u>" + ownDir.getName() + "</u>", 0));
                name.setTextColor(Color.parseColor("#018786"));
            }

            // buttons
            logs.setEnabled(ownDir.getLogFile().exists());
            build.setEnabled(context.isNodeServiceRunning() && !isBuilding);
            serve.setEnabled(context.isNodeServiceRunning() && !isBuilding && !isRunning);

            // build status indicator
            buildStatusGood.setVisibility(View.GONE);
            buildStatusBad.setVisibility(View.GONE);
            buildStatusWaiting.setVisibility(View.GONE);
            if (isBuilding) {
                buildStatusWaiting.setVisibility(View.VISIBLE);
            } else if (ownDir.isBuilt()) {
                buildStatusGood.setVisibility(View.VISIBLE);
            } else {
                buildStatusBad.setVisibility(View.VISIBLE);
            }






            // toggle.setChecked(ownDir.enabled);
        }


    }

}
