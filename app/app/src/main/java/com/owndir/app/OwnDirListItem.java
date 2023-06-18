package com.owndir.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;

public class OwnDirListItem extends RecyclerView.ViewHolder {

    private TextView name, filepath;
    private Button serveStart, serveStop, buildStart, buildStop, logs;
    private AppCompatImageButton delete;



    OwnDirListItem(View itemView) {
        super(itemView);
        name = itemView.findViewById(R.id.dir_item_name);
        filepath = itemView.findViewById(R.id.dir_item_filepath);
        serveStart = itemView.findViewById(R.id.serveButton_start);
        serveStop = itemView.findViewById(R.id.serveButton_stop);
        buildStart = itemView.findViewById(R.id.buildButton_start);
        buildStop = itemView.findViewById(R.id.buildButton_stop);
        logs = itemView.findViewById(R.id.logsButton);
        delete = itemView.findViewById(R.id.deleteButton);
    }


    public void update (OwnDir ownDir) {

        filepath.setText(ownDir.getDir().toString());
        logs.setEnabled(ownDir.getLogFile().exists());

        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((MainActivity) view.getContext()).removeOwnDir(ownDir);
            }
        });

        logs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = view.getContext();
                Uri fileUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", new File(ownDir.getLogFilePath()));
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(Intent.createChooser(intent, "Log"));
            }
        });

        // name
        if (ownDir.isServerUp) {
            name.setText(Html.fromHtml("<u>" + ownDir.getName() + "</u>", 0));
            name.setTextColor(Color.parseColor("#0000EE"));
            name.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, ownDir.getUrl()));
                }
            });
        } else {
            name.setText(ownDir.getName());
            name.setTextColor(Color.parseColor("#000000"));
        }


        if (ownDir.isRunningBuild) {
            buildStart.setVisibility(View.GONE);
            buildStop.setVisibility(View.VISIBLE);
            buildStop.setEnabled(true);

            serveStart.setVisibility(View.VISIBLE);
            serveStart.setEnabled(false);
            serveStop.setVisibility(View.GONE);

            buildStop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    OwnDirService.kill(view.getContext(), ownDir);
                }
            });
        }

        else if (ownDir.isRunningServer) {
            buildStart.setVisibility(View.VISIBLE);
            buildStart.setEnabled(false);
            buildStop.setVisibility(View.GONE);

            serveStart.setVisibility(View.GONE);
            serveStop.setVisibility(View.VISIBLE);
            serveStop.setEnabled(true);

            serveStop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    OwnDirService.kill(view.getContext(), ownDir);
                }
            });
        }

        else {
            buildStart.setVisibility(View.VISIBLE);
            buildStart.setEnabled(true);
            buildStop.setVisibility(View.GONE);

            serveStart.setVisibility(View.VISIBLE);
            serveStart.setEnabled(true);
            serveStop.setVisibility(View.GONE);

            buildStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    OwnDirService.build(view.getContext(), ownDir);
                }
            });

            serveStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    OwnDirService.serve(view.getContext(), ownDir);
                }
            });
        }
    }

}
