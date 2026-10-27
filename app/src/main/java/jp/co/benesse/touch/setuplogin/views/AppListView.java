package jp.co.benesse.touch.setuplogin.views;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

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

    public static class AppListAdapter extends BaseAdapter {

        private final List<AppData> dataList;
        private final OnItemClickListener listener;

        public AppListAdapter(List<AppData> dataList, OnItemClickListener listener) {
            this.dataList = dataList;
            this.listener = listener;
        }

        @Override
        public int getCount() {
            return dataList.size();
        }

        @Override
        public AppData getItem(int position) {
            return dataList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.list_item_app, parent, false);
                holder = new ViewHolder();
                holder.textView = convertView.findViewById(R.id.item_text);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppData data = getItem(position);
            holder.textView.setText(data.name);
            convertView.setOnClickListener(v -> listener.onItemClick(data));

            return convertView;
        }

        private static class ViewHolder {
            TextView textView;
        }
    }
}
