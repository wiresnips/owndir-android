package com.owndir.app;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class OwnDirListItemAdapter extends RecyclerView.Adapter<OwnDirListItem> {

    private LayoutInflater inflater;
    private MainActivity context;


    public OwnDirListItemAdapter(MainActivity context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public OwnDirListItem onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.dir_list_item, parent, false);
        return new OwnDirListItem(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OwnDirListItem listItemView, int position) {
        listItemView.update(context.ownDirList.get(position));
    }

    @Override
    public int getItemCount() {
        return context.ownDirList.size();
    }

}
