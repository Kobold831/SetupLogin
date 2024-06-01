package jp.co.benesse.touch.setuplogin.views;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.List;

import jp.co.benesse.touch.setuplogin.R;
import jp.co.benesse.touch.setuplogin.util.Constants;
import jp.co.benesse.touch.setuplogin.util.Preferences;

public class AppListView {

    public static class AppData {
        public String str;
    }

    public static class AppListAdapter extends ArrayAdapter<AppListView.AppData> {

        private final LayoutInflater mInflater;

        public AppListAdapter(Context context, List<AppListView.AppData> dataList) {
            super(context, R.layout.view_app_list_item);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            addAll(dataList);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            AppListView.ViewHolder holder = new AppListView.ViewHolder();

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.view_app_list_item, parent, false);
                holder.tv = convertView.findViewById(R.id.v_app_list_text);
                convertView.setTag(holder);
            } else {
                holder = (AppListView.ViewHolder) convertView.getTag();
            }

            final AppListView.AppData data = getItem(position);

            if (data != null) {
                holder.tv.setText(data.str);
            }

            /* RadioButtonの更新 */
            RadioButton button = convertView.findViewById(R.id.v_app_list_radio);
            button.setChecked(Preferences.load(getContext(), Constants.KEY_RADIO_TMP, 0) == position);

            return convertView;
        }
    }

    public static class ViewHolder {
        TextView tv;
    }
}
