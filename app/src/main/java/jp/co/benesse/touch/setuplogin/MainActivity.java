package jp.co.benesse.touch.setuplogin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

    String tmpPackageName;
    boolean tmpAppOpen;

    AlertDialog progressDialog;
    TextView progressPercentText;
    TextView progressByteText;
    ProgressBar dialogProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();
        check();
    }

    // 動作確認、デバイスの設定
    private void check() {
        // タブレットチェック
        try {
            if (new ArrayList<>(Arrays.asList(Constants.CT2_MODELS)).contains(Build.PRODUCT) &&
                    getPackageManager().getPackageInfo(Constants.DCHA_PACKAGE, 0).versionCode < 5) {
                new AlertDialog.Builder(this)
                        .setCancelable(false)
                        .setMessage(R.string.dialog_error_check_device)
                        .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                        .show();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setMessage(R.string.dialog_error_check_dcha)
                    .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                    .show();
        }

        // バージョン表示
        TextView textView = findViewById(R.id.main_text_v);
        textView.setText(new StringBuilder("v").append(BuildConfig.VERSION_NAME));

        // 学習モード有効、ナビゲーションバー表示
        if (!bindService(new Intent(Constants.DCHA_SERVICE).setPackage(Constants.DCHA_PACKAGE), new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                try {
                    IDchaService iDchaService = IDchaService.Stub.asInterface(iBinder);
                    iDchaService.setSetupStatus(3);
                    iDchaService.setSetupStatus(0);
                    iDchaService.hideNavigationBar(false);

                    // json ダウンロード開始
                    new FileDownloadTask().execute(MainActivity.this, Constants.URL_CHECK, new File(getExternalCacheDir(), "Check.json"), Constants.REQUEST_DOWNLOAD_CHECK_FILE);
                } catch (Exception ignored) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setCancelable(false)
                            .setMessage(R.string.dialog_error)
                            .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                            .show();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        }, Context.BIND_AUTO_CREATE)) {
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setMessage(R.string.dialog_error)
                    .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                    .show();
        }
    }

    // jsonダウンロード完了時にリストの初期化処理
    private void init() {
        try {
            ArrayList<AppListView.AppData> appDataArrayList = new ArrayList<>();
            JSONObject jsonObj1 = parseJson();
            JSONObject jsonObj2 = jsonObj1.getJSONObject("setupLogin");
            JSONArray jsonArray = jsonObj2.getJSONArray("appList");

            String model;

            switch (Build.MODEL) {
                case "TAB-A03-BR3" -> model = "CT3";
                case "TAB-A05-BD" -> model = "CTX";
                case "TAB-A05-BA1" -> model = "CTZ";
                default -> model = "CT2";
            }

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONArray jsonArray1 = jsonArray.getJSONObject(i).getJSONArray("targetModel");
                for (int s = 0; s < jsonArray1.length(); s++) {
                    if (Objects.equals(jsonArray1.getString(s), model) || jsonArray1.getString(s).isEmpty()) {
                        AppListView.AppData data = new AppListView.AppData();
                        data.packageName = jsonArray.getJSONObject(i).getString("packageName");
                        data.name = jsonArray.getJSONObject(i).getString("name");
                        data.description = jsonArray.getJSONObject(i).getString("description");
                        data.url = jsonArray.getJSONObject(i).getString("url");
                        data.appOpen = jsonArray.getJSONObject(i).getBoolean("appOpen");
                        appDataArrayList.add(data);
                    }
                }
            }

            ListView listView = findViewById(R.id.main_listview);
            listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            listView.setAdapter(new AppListView.AppListAdapter(this, appDataArrayList));
            listView.setOnItemClickListener((parent, view, position, id) -> {
                try {
                    StringBuilder str = new StringBuilder();
                    str.append("アプリ名：")
                            .append("\n")
                            .append(appDataArrayList.get(position).name)
                            .append("\n\n")
                            .append("説明：")
                            .append("\n")
                            .append(appDataArrayList.get(position).description)
                            .append("\n");

                    tmpPackageName = appDataArrayList.get(position).packageName;
                    tmpAppOpen = appDataArrayList.get(position).appOpen;

                    if (str.toString().isEmpty()) {
                        return;
                    }

                    new AlertDialog.Builder(this)
                            .setMessage(str + "\n" + "よろしければ OK を押下してください。")
                            .setNegativeButton(R.string.dialog_cancel, null)
                            .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                                try {
                                    if (!Objects.equals(appDataArrayList.get(position).url, "SETTINGS")) {
                                        startDownload(appDataArrayList.get(position).url);
                                    } else {
                                        bindService(new Intent(Constants.DCHA_SERVICE).setPackage(Constants.DCHA_PACKAGE), new ServiceConnection() {

                                            @Override
                                            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                                                try {
                                                    IDchaService iDchaService = IDchaService.Stub.asInterface(iBinder);
                                                    iDchaService.setSetupStatus(3);
                                                    iDchaService.hideNavigationBar(false);

                                                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                                                    finishAffinity();
                                                } catch (Exception e) {
                                                    new AlertDialog.Builder(MainActivity.this)
                                                            .setCancelable(false)
                                                            .setTitle(R.string.dialog_title_error)
                                                            .setMessage(e.getMessage())
                                                            .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                                                            .show();
                                                }
                                            }

                                            @Override
                                            public void onServiceDisconnected(ComponentName componentName) {
                                            }
                                        }, Context.BIND_AUTO_CREATE);
                                    }
                                } catch (Exception e) {
                                    new AlertDialog.Builder(this)
                                            .setCancelable(false)
                                            .setTitle(R.string.dialog_title_error)
                                            .setMessage(e.getMessage())
                                            .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                                            .show();
                                }
                            })
                            .show();

                    listView.invalidateViews();
                } catch (Exception e) {
                    new AlertDialog.Builder(this)
                            .setCancelable(false)
                            .setTitle(R.string.dialog_title_error)
                            .setMessage(e.getMessage())
                            .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                            .show();
                }
            });
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setTitle(R.string.dialog_title_error)
                    .setMessage(e.getMessage())
                    .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                    .show();
        }
    }

    public DchaInstallTask.Listener dchaInstallTaskListener() {
        return new DchaInstallTask.Listener() {

            /* プログレスバーの表示 */
            @Override
            public void onShow() {
                showLoadingDialog(getResources().getString(R.string.progress_state_installing));
            }

            /* 成功 */
            @Override
            public void onSuccess() {
                cancelLoadingDialog();

                if (!tmpAppOpen) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setCancelable(false)
                            .setMessage(R.string.dialog_success_silent_install)
                            .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                            .show();
                    return;
                }

                bindService(new Intent(Constants.DCHA_SERVICE).setPackage(Constants.DCHA_PACKAGE), new ServiceConnection() {

                    @Override
                    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                        try {
                            IDchaService iDchaService = IDchaService.Stub.asInterface(iBinder);
                            iDchaService.setSetupStatus(0);
                            iDchaService.hideNavigationBar(false);

                            MainActivity.this.startActivity(getPackageManager().getLaunchIntentForPackage(tmpPackageName));
                            finishAffinity();
                        } catch (Exception e) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setCancelable(false)
                                    .setTitle(R.string.dialog_title_error)
                                    .setMessage(e.getMessage())
                                    .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                                    .show();
                        }
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName componentName) {
                    }
                }, Context.BIND_AUTO_CREATE);
            }

            /* 失敗 */
            @Override
            public void onFailure() {
                cancelLoadingDialog();
                new AlertDialog.Builder(MainActivity.this)
                        .setCancelable(false)
                        .setMessage(R.string.dialog_failure_silent_install)
                        .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                        .show();
            }
        };
    }


    @Override
    public void onDownloadComplete(int reqCode) {
        switch (reqCode) {
            case Constants.REQUEST_DOWNLOAD_CHECK_FILE -> init();
            case Constants.REQUEST_DOWNLOAD_APK -> {
                cancelLoadingDialog();
                new DchaInstallTask().execute(this, dchaInstallTaskListener(), new File(getExternalCacheDir(), "base.apk").getAbsolutePath());
            }
        }
    }

    @Override
    public void onDownloadError(int reqCode) {
        switch (reqCode) {
            case Constants.REQUEST_DOWNLOAD_CHECK_FILE,
                 Constants.REQUEST_DOWNLOAD_APK -> {
                cancelLoadingDialog();
                new AlertDialog.Builder(this)
                        .setCancelable(false)
                        .setMessage(getString(R.string.dialog_error_download))
                        .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                        .show();
            }
        }
    }

    @Override
    public void onConnectionError(int reqCode) {
        switch (reqCode) {
            case Constants.REQUEST_DOWNLOAD_CHECK_FILE,
                 Constants.REQUEST_DOWNLOAD_APK -> {
                cancelLoadingDialog();
                new AlertDialog.Builder(this)
                        .setCancelable(false)
                        .setMessage(getString(R.string.dialog_error_connection))
                        .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                        .show();
            }
        }
    }

    @Override
    public void onProgressUpdate(int progress, int currentByte, int totalByte) {
        progressPercentText.setText(new StringBuilder(String.valueOf(progress)).append("%"));
        progressByteText.setText(new StringBuilder(String.valueOf(currentByte)).append(" MB").append("/").append(totalByte).append(" MB"));
        dialogProgressBar.setProgress(progress);
        progressDialog.setMessage(new StringBuilder(getString(R.string.progress_state_download_file)));
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

    private void startDownload(String downloadFileUrl) {
        FileDownloadTask fileDownloadTask = new FileDownloadTask();
        fileDownloadTask.execute(this, downloadFileUrl, new File(getExternalCacheDir(), "base.apk"), Constants.REQUEST_DOWNLOAD_APK);
        ProgressHandler progressHandler = new ProgressHandler(Looper.getMainLooper());
        progressHandler.fileDownloadTask = fileDownloadTask;
        progressHandler.sendEmptyMessage(0);
        View view = getLayoutInflater().inflate(R.layout.view_progress, null);
        progressPercentText = view.findViewById(R.id.progress_percent);
        progressPercentText.setText("");
        progressByteText = view.findViewById(R.id.progress_byte);
        progressByteText.setText("");
        dialogProgressBar = view.findViewById(R.id.progress);
        dialogProgressBar.setProgress(0);
        progressDialog = new AlertDialog.Builder(this).setCancelable(false).setView(view).create();
        progressDialog.setMessage("");
        progressDialog.show();
    }

    public void showLoadingDialog(String message) {
        View view = getLayoutInflater().inflate(R.layout.view_progress_spinner, null);
        TextView textView = view.findViewById(R.id.view_progress_spinner_text);
        textView.setText(message);
        progressDialog = new AlertDialog.Builder(this).setCancelable(false).setView(view).create();
        progressDialog.show();
    }

    public void cancelLoadingDialog() {
        if (progressDialog == null) {
            return;
        }

        if (progressDialog.isShowing()) {
            progressDialog.cancel();
        }
    }
}
