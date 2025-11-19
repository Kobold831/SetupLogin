package jp.co.benesse.touch.setuplogin;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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

public class MainActivity extends AppCompatActivity implements DownloadEventListener {

    private String tmpPackageName;
    private boolean tmpAppOpen;
    private AlertDialog progressDialog;
    private TextView progressPercentText;
    private TextView progressByteText;
    private ProgressBar dialogProgressBar;
    private ProgressHandler progressHandler;
    private ServiceConnection activeConnection;
    private boolean isBound = false;

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressHandler != null) {
            progressHandler.stop();
        }
        unbindCurrentService();
    }

    private void unbindCurrentService() {
        if (isBound && activeConnection != null) {
            try {
                unbindService(activeConnection);
            } catch (IllegalArgumentException ignored) {
                // 既に切断されている場合などは無視
            }
            isBound = false;
            activeConnection = null;
        }
    }

    // 動作確認、デバイスの設定
    private void check() {
        // タブレットチェック
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(Constants.DCHA_PACKAGE, 0);
            long versionCode = PackageInfoCompat.getLongVersionCode(pInfo);

            if (new ArrayList<>(Arrays.asList(Constants.CT2_MODELS)).contains(Build.PRODUCT) && versionCode < 5) {
                showErrorDialog(getString(R.string.dialog_error_check_device));
                return;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            showErrorDialog(getString(R.string.dialog_error_check_dcha));
            return;
        }

        // バージョン表示
        TextView textView = findViewById(R.id.main_text_v);
        if (textView != null) {
            textView.setText("v" + BuildConfig.VERSION_NAME);
        }

        // サービス接続定義
        activeConnection = new ServiceConnection() {
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
                    showErrorDialog(getString(R.string.dialog_error));
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                isBound = false;
            }
        };

        // 学習モード有効、ナビゲーションバー表示のためのバインド
        try {
            Intent intent = new Intent(Constants.DCHA_SERVICE).setPackage(Constants.DCHA_PACKAGE);
            boolean bound = bindService(intent, activeConnection, Context.BIND_AUTO_CREATE);
            if (!bound) {
                showErrorDialog(getString(R.string.dialog_error));
            } else {
                isBound = true;
            }
        } catch (Exception e) {
            showErrorDialog(getString(R.string.dialog_error));
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

            // RecyclerViewの設定
            RecyclerView recyclerView = findViewById(R.id.main_recycler_view);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));

            // アダプターの設定
            AppListView.AppListAdapter adapter = new AppListView.AppListAdapter(appDataArrayList, this::onItemClick);
            recyclerView.setAdapter(adapter);

        } catch (Exception e) {
            showErrorDialog(e.getMessage());
        }
    }

    // リストアイテムクリック時の処理
    private void onItemClick(AppListView.AppData data) {
        try {
            StringBuilder str = new StringBuilder();
            str.append("アプリ名：\n").append(data.name).append("\n\n")
                    .append("説明：\n").append(data.description).append("\n");

            tmpPackageName = data.packageName;
            tmpAppOpen = data.appOpen;

            new MaterialAlertDialogBuilder(this)
                    .setMessage(str + "\n" + "よろしければ OK を押下してください。")
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                        try {
                            if (!Objects.equals(data.url, "SETTINGS")) {
                                startDownload(data.url);
                            } else {
                                // 設定画面を開く処理
                                unbindCurrentService(); // 既存の接続を切る
                                activeConnection = new ServiceConnection() {
                                    @Override
                                    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                                        try {
                                            IDchaService iDchaService = IDchaService.Stub.asInterface(iBinder);
                                            iDchaService.setSetupStatus(3);
                                            iDchaService.hideNavigationBar(false);

                                            startActivity(new Intent(Settings.ACTION_SETTINGS));
                                            finishAffinity();
                                        } catch (Exception e) {
                                            showErrorDialog(e.getMessage());
                                        }
                                    }

                                    @Override
                                    public void onServiceDisconnected(ComponentName componentName) {
                                        isBound = false;
                                    }
                                };
                                boolean bound = bindService(new Intent(Constants.DCHA_SERVICE).setPackage(Constants.DCHA_PACKAGE), activeConnection, Context.BIND_AUTO_CREATE);
                                if (bound) isBound = true;
                            }
                        } catch (Exception e) {
                            showErrorDialog(e.getMessage());
                        }
                    })
                    .show();

        } catch (Exception e) {
            showErrorDialog(e.getMessage());
        }
    }

    public DchaInstallTask.Listener dchaInstallTaskListener() {
        return new DchaInstallTask.Listener() {
            @Override
            public void onShow() {
                showLoadingDialog(getString(R.string.progress_state_installing));
            }

            @Override
            public void onSuccess() {
                cancelLoadingDialog();

                if (!tmpAppOpen) {
                    new MaterialAlertDialogBuilder(MainActivity.this)
                            .setCancelable(false)
                            .setMessage(R.string.dialog_success_silent_install)
                            .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                            .show();
                    return;
                }

                unbindCurrentService();
                activeConnection = new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                        try {
                            IDchaService iDchaService = IDchaService.Stub.asInterface(iBinder);
                            iDchaService.setSetupStatus(0);
                            iDchaService.hideNavigationBar(false);

                            MainActivity.this.startActivity(getPackageManager().getLaunchIntentForPackage(tmpPackageName));
                            finishAffinity();
                        } catch (Exception e) {
                            showErrorDialog(e.getMessage());
                        }
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName componentName) {
                        isBound = false;
                    }
                };
                boolean bound = bindService(new Intent(Constants.DCHA_SERVICE).setPackage(Constants.DCHA_PACKAGE), activeConnection, Context.BIND_AUTO_CREATE);
                if (bound) isBound = true;
            }

            @Override
            public void onFailure() {
                cancelLoadingDialog();
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setCancelable(false)
                        .setMessage(R.string.dialog_failure_silent_install)
                        .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
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
            new DchaInstallTask().execute(this, dchaInstallTaskListener(), new File(getExternalCacheDir(), "base.apk").getAbsolutePath());
        }
    }

    @Override
    public void onDownloadError(int reqCode) {
        cancelLoadingDialog();
        showErrorDialog(getString(R.string.dialog_error_download));
    }

    @Override
    public void onConnectionError(int reqCode) {
        cancelLoadingDialog();
        showErrorDialog(getString(R.string.dialog_error_connection));
    }

    @Override
    public void onProgressUpdate(int progress, int currentByte, int totalByte) {
        if (progressPercentText != null) progressPercentText.setText(progress + "%");
        if (progressByteText != null) progressByteText.setText(currentByte + " MB / " + totalByte + " MB");
        if (dialogProgressBar != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) dialogProgressBar.setProgress(progress, true);
            else dialogProgressBar.setProgress(progress);
        }
    }

    public JSONObject parseJson() throws JSONException, IOException {
        File file = new File(getExternalCacheDir(), "Check.json");
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        StringBuilder data = new StringBuilder();
        String str;
        while ((str = bufferedReader.readLine()) != null) {
            data.append(str);
        }
        bufferedReader.close();
        return new JSONObject(data.toString());
    }

    private void startDownload(String downloadFileUrl) {
        // 古いダイアログが残っていたら確実に消す
        cancelLoadingDialog();

        // Viewは毎回新しく作る
        View view = getLayoutInflater().inflate(R.layout.view_progress, null);

        // View内のパーツを取得
        progressPercentText = view.findViewById(R.id.progress_percent);
        progressByteText = view.findViewById(R.id.progress_byte);
        dialogProgressBar = view.findViewById(R.id.progress);

        // 初期値セット
        progressPercentText.setText("0%");
        progressByteText.setText("0 MB / 0 MB");

        // ProgressBarの設定（動かない問題対策）
        dialogProgressBar.setIndeterminate(false);
        dialogProgressBar.setMax(100);
        dialogProgressBar.setProgress(0);

        // タスク開始
        FileDownloadTask fileDownloadTask = new FileDownloadTask();
        fileDownloadTask.execute(this, downloadFileUrl, new File(getExternalCacheDir(), "base.apk"), Constants.REQUEST_DOWNLOAD_APK);

        // ハンドラー開始
        progressHandler = new ProgressHandler(Looper.getMainLooper());
        progressHandler.fileDownloadTask = fileDownloadTask;
        progressHandler.sendEmptyMessage(0);

        // ダイアログ作成・表示
        progressDialog = new MaterialAlertDialogBuilder(this)
                .setCancelable(false)
                .setView(view) // ここでViewをセット
                .create();

        progressDialog.show();    }

    public void showLoadingDialog(String message) {
        cancelLoadingDialog();
        View view = getLayoutInflater().inflate(R.layout.view_progress_spinner, null);
        TextView textView = view.findViewById(R.id.view_progress_spinner_text);
        textView.setText(message);

        progressDialog = new MaterialAlertDialogBuilder(this)
                .setCancelable(false)
                .setView(view)
                .create();
        progressDialog.show();
    }

    public void cancelLoadingDialog() {
        // ハンドラーの停止
        if (progressHandler != null) {
            progressHandler.stop();
            progressHandler = null;
        }

        // ダイアログの削除
        if (progressDialog != null) {
            // dismiss() は cancel() よりも安全に閉じる処理です
            progressDialog.dismiss();
            progressDialog = null;
        }

        // 念のため参照を切る
        progressPercentText = null;
        progressByteText = null;
        dialogProgressBar = null;
    }

    private void showErrorDialog(String message) {
        new MaterialAlertDialogBuilder(this)
                .setCancelable(false)
                .setTitle(R.string.dialog_title_error)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                .show();
    }
}