package com.owndir.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class OwnDirListItemAdapter extends RecyclerView.Adapter<OwnDirListItemAdapter.ViewHolder> {

    private List<OwnDir> ownDirList;
    private LayoutInflater inflater;

    public OwnDirListItemAdapter(Context context, List<OwnDir> ownDirList) {
        this.ownDirList = ownDirList;
        this.inflater = LayoutInflater.from(context);
    }

    public void addDirInfo(OwnDir ownDir) {
        ownDirList.add(ownDir);
        notifyItemInserted(ownDirList.size() - 1);
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
        holder.name.setText(ownDir.name);
        holder.filepath.setText(ownDir.getDir().toString());
        holder.url.setText(ownDir.getUrl().toString());
    }

    @Override
    public int getItemCount() {
        return ownDirList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, filepath, url;
        Button run, edit;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.dir_item_name);
            filepath = itemView.findViewById(R.id.dir_item_filepath);
            url = itemView.findViewById(R.id.dir_item_url);

            run = itemView.findViewById(R.id.dir_item_run_button);
            edit = itemView.findViewById(R.id.dir_item_edit_button);
        }
    }
}
