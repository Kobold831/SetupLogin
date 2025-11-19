package jp.co.benesse.touch.setuplogin.views;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import jp.co.benesse.touch.setuplogin.R;

public class AppListView {

    public static class AppData {
        public String name;
        public String description;
        public String url;
        public String packageName;
        public boolean appOpen;
    }

    public interface OnItemClickListener {
        void onItemClick(AppData data);
    }

    public static class AppListAdapter extends RecyclerView.Adapter<ViewHolder> {

        private final List<AppData> dataList;
        private final OnItemClickListener listener;

        public AppListAdapter(List<AppData> dataList, OnItemClickListener listener) {
            this.dataList = dataList;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppData data = dataList.get(position);
            holder.textView.setText(data.name);
            holder.itemView.setOnClickListener(v -> listener.onItemClick(data));
        }

        @Override
        public int getItemCount() {
            return dataList.size();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public ViewHolder(View view) {
            super(view);
            textView = view.findViewById(R.id.item_text);
        }
    }
}