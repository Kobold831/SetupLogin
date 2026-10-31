package jp.co.benesse.touch.setuplogin.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

import jp.co.benesse.touch.setuplogin.R;
import jp.co.benesse.touch.setuplogin.views.AppListView;

public class Common {

    public static String getDchaCheckError(Context context) {
        try {
            context.getPackageManager().getPackageInfo(Constants.DCHA_PACKAGE, 0);
        } catch (PackageManager.NameNotFoundException ignored) {
            return context.getString(R.string.dialog_error_check_dcha);
        }
        return "";
    }

    public static ArrayList<AppListView.AppData> buildAppList(File file) throws Exception {
        JSONArray appList = parseJson(file).getJSONArray(Constants.JSON_APP_LIST);
        String model = currentModel();
        ArrayList<AppListView.AppData> result = new ArrayList<>();

        for (int i = 0; i < appList.length(); i++) {
            try {
                JSONObject item = appList.getJSONObject(i);
                JSONArray targets = item.getJSONArray(Constants.JSON_TARGET_MODEL);
                for (int s = 0; s < targets.length(); s++) {
                    if (Objects.equals(targets.getString(s), model) || targets.getString(s).isEmpty()) {
                        AppListView.AppData data = new AppListView.AppData();
                        data.packageName = item.getString(Constants.JSON_PACKAGE_NAME);
                        data.name = item.getString(Constants.JSON_NAME);
                        data.description = item.getString(Constants.JSON_DESCRIPTION);
                        data.url = item.getString(Constants.JSON_URL);
                        data.appOpen = item.getBoolean(Constants.JSON_APP_OPEN);
                        result.add(data);
                        break;
                    }
                }
            } catch (JSONException ignored) {
            }
        }
        return result;
    }

    private static String currentModel() {
        return switch (Build.MODEL) {
            case Constants.BUILD_MODEL_CT3 -> Constants.MODEL_CT3;
            case Constants.BUILD_MODEL_CTX -> Constants.MODEL_CTX;
            case Constants.BUILD_MODEL_CTZ -> Constants.MODEL_CTZ;
            default -> Constants.MODEL_CT2;
        };
    }

    private static JSONObject parseJson(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            int len;
            while (offset < bytes.length && (len = in.read(bytes, offset, bytes.length - offset)) != -1) {
                offset += len;
            }
            return new JSONObject(new String(bytes, 0, offset, StandardCharsets.UTF_8));
        }
    }
}
