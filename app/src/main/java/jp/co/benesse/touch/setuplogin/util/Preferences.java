package jp.co.benesse.touch.setuplogin.util;

import android.content.Context;
import android.preference.PreferenceManager;

@SuppressWarnings("deprecation")
public class Preferences {

    /* データ管理 */
    public static void save(Context context, String key, int value) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(key, value).apply();
    }

    public static int load(Context context, String key, int value) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(key, value);
    }
}
