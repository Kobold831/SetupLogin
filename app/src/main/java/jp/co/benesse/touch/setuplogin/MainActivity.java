package jp.co.benesse.touch.setuplogin;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.RadioButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import jp.co.benesse.touch.setuplogin.data.event.DownloadEventListener;
import jp.co.benesse.touch.setuplogin.data.handler.ProgressHandler;
import jp.co.benesse.touch.setuplogin.data.task.DchaInstallTask;
import jp.co.benesse.touch.setuplogin.data.task.FileDownloadTask;
import jp.co.benesse.touch.setuplogin.util.Constants;
import jp.co.benesse.touch.setuplogin.util.Preferences;
import jp.co.benesse.touch.setuplogin.views.AppListView;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements DownloadEventListener {

    ProgressDialog progressDialog;
    public String DOWNLOAD_FILE_URL;
    int tmpIndex;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            Settings.System.putInt(getContentResolver(), "dcha_state", 3);
        } catch (Exception ignored) {
        }

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        new FileDownloadTask().execute(this, Constants.URL_CHECK, new File(getExternalCacheDir(), "Check.json"), Constants.REQUEST_DOWNLOAD_CHECK_FILE);
    }

    private void selectApkDialog() {
        ArrayList<AppListView.AppData> appDataArrayList = new ArrayList<>();

        try {
            JSONObject jsonObj1 = parseJson();
            JSONObject jsonObj2 = jsonObj1.getJSONObject("setupLogin");
            JSONArray jsonArray = jsonObj2.getJSONArray("appList");

            for (int i = 0; i < jsonArray.length(); i++) {
                AppListView.AppData data = new AppListView.AppData();
                data.str = jsonArray.getJSONObject(i).getString("name");
                appDataArrayList.add(data);
            }
        } catch (JSONException | IOException ignored) {
        }

        View view = getLayoutInflater().inflate(R.layout.layout_app_list, null);
        ListView listView = view.findViewById(R.id.app_list);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listView.setAdapter(new AppListView.AppListAdapter(this, appDataArrayList));
        listView.setOnItemClickListener((parent, view1, position, id) -> {
            Preferences.save(this, Constants.KEY_RADIO_TMP, (int) id);
            listView.invalidateViews();
        });

        new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .setTitle("アプリを選択してください")
                .setMessage("選択してOKを押下すると詳細な情報が表示されます")
                .setPositiveButton("OK", (dialog, which) -> {
                    StringBuilder str = new StringBuilder();

                    for (int i = 0; i < listView.getCount(); i++) {
                        RadioButton radioButton = listView.getChildAt(i).findViewById(R.id.v_app_list_radio);
                        if (radioButton.isChecked()) {
                            try {
                                JSONObject jsonObj1 = parseJson();
                                JSONObject jsonObj2 = jsonObj1.getJSONObject("setupLogin");
                                JSONArray jsonArray = jsonObj2.getJSONArray("appList");
                                str.append("アプリ名：").append(jsonArray.getJSONObject(i).getString("name")).append("\n\n").append("説明：").append(jsonArray.getJSONObject(i).getString("description")).append("\n");
                                DOWNLOAD_FILE_URL = jsonArray.getJSONObject(i).getString("url");
                                tmpIndex = i;
                            } catch (JSONException | IOException | NullPointerException ignored) {
                            }
                        }
                    }

                    if (str.toString().isEmpty()) {
                        return;
                    }

                    new AlertDialog.Builder(this)
                            .setCancelable(false)
                            .setMessage(str + "\n" + "よろしければOKを押下してください")
                            .setNegativeButton("キャンセル", (dialog1, which1) -> selectApkDialog())
                            .setPositiveButton("OK", (dialog2, which2) -> {
                                if (!Objects.equals(DOWNLOAD_FILE_URL, "SETTINGS")) {
                                    showLoadingDialog();
                                    startDownload();
                                    dialog.dismiss();
                                } else {
                                    try {
                                        Settings.System.putInt(getContentResolver(), "dcha_state", 3);
                                    } catch (Exception ignored) {
                                    }
                                    startActivity(new Intent().setClassName("com.android.settings", "com.android.settings.Settings"));
                                }
                            })
                            .show();
                })
                .show();
    }

    @SuppressWarnings("deprecation")
    public DchaInstallTask.Listener installListener() {
        return new DchaInstallTask.Listener() {

            ProgressDialog progressDialog;

            /* プログレスバーの表示 */
            @Override
            public void onShow() {
                progressDialog = new ProgressDialog(MainActivity.this);
                progressDialog.setCancelable(false);
                progressDialog.setMessage("インストール中...");
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progressDialog.show();
            }

            /* 成功 */
            @Override
            public void onSuccess() {
                if (progressDialog.isShowing()) {
                    progressDialog.cancel();
                }
                try {
                    JSONObject jsonObj1 = parseJson();
                    JSONObject jsonObj2 = jsonObj1.getJSONObject("setupLogin");
                    JSONArray jsonArray = jsonObj2.getJSONArray("appList");
                    MainActivity.this.startActivity(getPackageManager().getLaunchIntentForPackage(jsonArray.getJSONObject(tmpIndex).getString("packageName")));
                } catch (JSONException | IOException ignored) {
                    selectApkDialog();
                }
            }

            /* 失敗 */
            @Override
            public void onFailure() {
                if (progressDialog.isShowing()) {
                    progressDialog.cancel();
                }

                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("インストールに失敗しました")
                        .setCancelable(false)
                        .setPositiveButton("OK", (dialog, which) -> selectApkDialog())
                        .show();
            }
        };
    }


    @Override
    public void onDownloadComplete(int reqCode) {
        if (reqCode == Constants.REQUEST_DOWNLOAD_CHECK_FILE) {
            selectApkDialog();
        } else if (reqCode == Constants.REQUEST_DOWNLOAD_APK) {
            cancelLoadingDialog();
            new DchaInstallTask().execute(this, installListener(), new File(getExternalCacheDir(), "base.apk").getAbsolutePath());
        }
    }

    @Override
    public void onDownloadError(int reqCode) {
        cancelLoadingDialog();
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage("ダウンロードに失敗しました\nネットワークが安定しているか確認してください")
                .setPositiveButton("OK", (dialog, which) -> selectApkDialog())
                .show();
    }

    @Override
    public void onConnectionError(int reqCode) {
        cancelLoadingDialog();
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage("データ取得に失敗しました\nネットワークを確認してください")
                .setPositiveButton("OK", (dialog, which) -> selectApkDialog())
                .show();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onProgressUpdate(int progress, int currentByte, int totalByte) {
        progressDialog.setMessage(new StringBuilder("インストールファイルをサーバーからダウンロードしています...\nしばらくお待ち下さい...\n進行状況：").append(progress).append("%"));
        progressDialog.setProgress(progress);
    }

    public JSONObject parseJson() throws JSONException, IOException {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(new File(getExternalCacheDir(), "Check.json").getPath()));
        JSONObject json;
        StringBuilder data = new StringBuilder();
        String str = bufferedReader.readLine();

        while (str != null) {
            data.append(str);
            str = bufferedReader.readLine();
        }

        json = new JSONObject(data.toString());

        bufferedReader.close();
        return json;
    }

    private void startDownload() {
        FileDownloadTask fileDownloadTask = new FileDownloadTask();
        fileDownloadTask.execute(this, DOWNLOAD_FILE_URL, new File(getExternalCacheDir(), "base.apk"), Constants.REQUEST_DOWNLOAD_APK);
        ProgressHandler progressHandler = new ProgressHandler(Looper.getMainLooper());
        progressHandler.fileDownloadTask = fileDownloadTask;
        progressHandler.sendEmptyMessage(0);
    }

    @SuppressWarnings("deprecation")
    public void showLoadingDialog() {
        progressDialog.setMessage("");
        progressDialog.show();
    }

    public void cancelLoadingDialog() {
        if (progressDialog.isShowing()) {
            progressDialog.cancel();
        }
    }
}
