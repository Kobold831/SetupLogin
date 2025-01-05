package jp.co.benesse.touch.setuplogin;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import jp.co.benesse.dcha.dchaservice.IDchaService;
import jp.co.benesse.touch.setuplogin.data.event.DownloadEventListener;
import jp.co.benesse.touch.setuplogin.data.handler.ProgressHandler;
import jp.co.benesse.touch.setuplogin.data.task.DchaInstallTask;
import jp.co.benesse.touch.setuplogin.data.task.FileDownloadTask;
import jp.co.benesse.touch.setuplogin.util.Constants;
import jp.co.benesse.touch.setuplogin.views.AppListView;

/**
 * SetupLogin
 * @author Kobold
 * @noinspection deprecation <b>{@code ProgressDialog}</b>
 */
public class MainActivity extends Activity implements DownloadEventListener {

    ProgressDialog progressDialog;
    String DOWNLOAD_FILE_URL;
    int tmpIndex;
    static final String BC_PASSWORD_HIT_FLAG = "bc_password_hit";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView textView = findViewById(R.id.main_text_v);
        textView.setText(new StringBuilder("v").append(BuildConfig.VERSION_NAME));

        bindService(new Intent("jp.co.benesse.dcha.dchaservice.DchaService").setPackage("jp.co.benesse.dcha.dchaservice"), new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                try {
                    IDchaService mDchaService = IDchaService.Stub.asInterface(iBinder);
                    mDchaService.setSetupStatus(3);
                    mDchaService.hideNavigationBar(false);
                } catch (RemoteException ignored) {
                }
            }
            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        }, Context.BIND_AUTO_CREATE);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        new FileDownloadTask().execute(this, Constants.URL_CHECK, new File(getExternalCacheDir(), "Check.json"), Constants.REQUEST_DOWNLOAD_CHECK_FILE);
    }

    private void init() {
        // BenesseExtension 搭載機において ADB の無効化を阻止
        Settings.System.putInt(getContentResolver(), BC_PASSWORD_HIT_FLAG, 1);

        try {
            ArrayList<AppListView.AppData> appDataArrayList = new ArrayList<>();
            JSONObject jsonObj1 = parseJson();
            JSONObject jsonObj2 = jsonObj1.getJSONObject("setupLogin");
            JSONArray jsonArray = jsonObj2.getJSONArray("appList");

            for (int i = 0; i < jsonArray.length(); i++) {
                AppListView.AppData data = new AppListView.AppData();
                data.str = jsonArray.getJSONObject(i).getString("name");
                appDataArrayList.add(data);
            }

            ListView listView = findViewById(R.id.main_listview);
            listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            listView.setAdapter(new AppListView.AppListAdapter(this, appDataArrayList));
            listView.setOnItemClickListener((parent, view1, position, id) -> {
                try {
                    DOWNLOAD_FILE_URL = jsonArray.getJSONObject(position).getString("url");
                    tmpIndex = position;

                    new AlertDialog.Builder(this)
                            .setMessage("アプリ名：" + jsonArray.getJSONObject(position).getString("name") + "\n\n" + "詳細：" + jsonArray.getJSONObject(position).getString("description") + "\n\n" + "続行する場合はOKを押してください")
                            .setNegativeButton("キャンセル", (dialog1, which1) -> init())
                            .setPositiveButton("OK", (dialog2, which2) -> {
                                if (!Objects.equals(DOWNLOAD_FILE_URL, "SETTINGS")) {
                                    showLoadingDialog();
                                    startDownload();
                                } else {
                                    bindService(new Intent("jp.co.benesse.dcha.dchaservice.DchaService").setPackage("jp.co.benesse.dcha.dchaservice"), new ServiceConnection() {

                                        @Override
                                        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                                            try {
                                                IDchaService mDchaService = IDchaService.Stub.asInterface(iBinder);
                                                mDchaService.setSetupStatus(3);
                                                mDchaService.hideNavigationBar(false);
                                            } catch (RemoteException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }

                                        @Override
                                        public void onServiceDisconnected(ComponentName componentName) {
                                        }
                                    }, Context.BIND_AUTO_CREATE);
                                    startActivity(new Intent().setClassName("com.android.settings", "com.android.settings.Settings"));
                                    finishAffinity();
                                }
                            })
                            .show();
                } catch (Exception e) {
                    new AlertDialog.Builder(this)
                            .setTitle("エラーが発生しました")
                            .setMessage(e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("エラーが発生しました")
                    .setMessage(e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

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
                    bindService(new Intent("jp.co.benesse.dcha.dchaservice.DchaService").setPackage("jp.co.benesse.dcha.dchaservice"), new ServiceConnection() {

                        @Override
                        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                            try {
                                IDchaService mDchaService = IDchaService.Stub.asInterface(iBinder);
                                mDchaService.setSetupStatus(0);
                                mDchaService.hideNavigationBar(false);
                            } catch (RemoteException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void onServiceDisconnected(ComponentName componentName) {
                        }
                    }, Context.BIND_AUTO_CREATE);
                    MainActivity.this.startActivity(getPackageManager().getLaunchIntentForPackage(jsonArray.getJSONObject(tmpIndex).getString("packageName")));
                    finishAffinity();
                } catch (Exception e) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("エラーが発生しました")
                            .setMessage(e.getMessage())
                            .setPositiveButton("OK", (dialog, which) -> init())
                            .show();
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
                        .setPositiveButton("OK", (dialog, which) -> init())
                        .show();
            }
        };
    }


    @Override
    public void onDownloadComplete(int reqCode) {
        if (reqCode == Constants.REQUEST_DOWNLOAD_CHECK_FILE) {
            init();
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
                .setPositiveButton("OK", (dialog, which) -> init())
                .show();
    }

    @Override
    public void onConnectionError(int reqCode) {
        cancelLoadingDialog();
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage("データ取得に失敗しました\nネットワークを確認してください")
                .setPositiveButton("OK", (dialog, which) -> init())
                .show();
    }

    @Override
    public void onProgressUpdate(int progress, int currentByte, int totalByte) {
        progressDialog.setMessage(new StringBuilder("インストールファイルをサーバーからダウンロードしています…\nしばらくお待ち下さい…\n進行状況：").append(progress).append("%"));
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
