package jp.co.benesse.touch.setuplogin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import jp.co.benesse.touch.setuplogin.data.event.DownloadEventListener;
import jp.co.benesse.touch.setuplogin.data.handler.ProgressHandler;
import jp.co.benesse.touch.setuplogin.data.net.Ipv4Proxy;
import jp.co.benesse.touch.setuplogin.data.service.DchaConnection;
import jp.co.benesse.touch.setuplogin.data.task.DchaInstallTask;
import jp.co.benesse.touch.setuplogin.data.task.FileDownloadTask;
import jp.co.benesse.touch.setuplogin.util.Common;
import jp.co.benesse.touch.setuplogin.util.Constants;
import jp.co.benesse.touch.setuplogin.views.AppListView;

public class MainActivity extends Activity implements DownloadEventListener, View.OnClickListener {

    private static final String STATE_ERROR_COUNT = "error_count";
    private static final String STATE_RECOVERY_DISABLED = "recovery_disabled";
    private static final String STATE_SETTINGS_OPENED = "settings_opened";
    private static final int ERROR_LIMIT = 3;

    private String tmpPackageName;
    private boolean tmpAppOpen;
    private AlertDialog progressDialog;
    private TextView progressPercentText;
    private TextView progressByteText;
    private ProgressBar dialogProgressBar;
    private ProgressHandler progressHandler;
    private TextView selectAppText;
    private DchaConnection dcha;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable downloadTimeout = this::onDownloadTimeout;
    private FileDownloadTask downloadTask;
    private boolean checking;
    private boolean listRetried;
    private boolean settingsOpened;
    private int errorCount;
    private boolean recoveryDisabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.open_settings).setOnClickListener(this);
        selectAppText = findViewById(R.id.select_app_text);
        selectAppText.setVisibility(View.GONE);
        dcha = new DchaConnection(this);

        if (savedInstanceState != null) {
            errorCount = savedInstanceState.getInt(STATE_ERROR_COUNT, 0);
            recoveryDisabled = savedInstanceState.getBoolean(STATE_RECOVERY_DISABLED, false);
            settingsOpened = savedInstanceState.getBoolean(STATE_SETTINGS_OPENED, false);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_ERROR_COUNT, errorCount);
        outState.putBoolean(STATE_RECOVERY_DISABLED, recoveryDisabled);
        outState.putBoolean(STATE_SETTINGS_OPENED, settingsOpened);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (settingsOpened) {
            settingsOpened = false;
            errorCount = 0;
            listRetried = false;
            recoveryDisabled = false;
        }
        check();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeoutHandler.removeCallbacks(downloadTimeout);
        cancelDownload();
        cancelLoadingDialog();
        dcha.disconnect();
    }

    private void connectDcha(int setupStatus, Runnable action) {
        dcha.setSetupStatus(setupStatus);
        dcha.hideNavigationBar(false);
        action.run();
    }

    private void check() {
        if (recoveryDisabled || checking || downloadTask != null) {
            return;
        }

        String error = Common.getDchaCheckError(this);
        if (!error.isEmpty()) {
            showErrorDialog(error);
            return;
        }
        checking = true;

        TextView version = findViewById(R.id.main_text_v);
        version.setText(getString(R.string.version_prefix) + BuildConfig.VERSION_NAME);

        dcha.connect(
                () -> {
                    dcha.setSetupStatus(3);
                    dcha.setSetupStatus(0);
                    dcha.hideNavigationBar(false);
                },
                () -> {
                    checking = false;
                    cancelLoadingDialog();
                    showErrorDialog(getString(R.string.dialog_error));
                });

        if (!checking) {
            return;
        }

        showLoadingDialog(getString(R.string.progress_state_please_wait, getString(R.string.progress_state_connecting)));
        downloadCheckFile();
    }

    private void downloadCheckFile() {
        File outputFile = new File(getExternalCacheDir(), Constants.FILE_CHECK_JSON);
        startDownloadTask(Constants.URL_CHECK, outputFile, Constants.REQUEST_DOWNLOAD_CHECK_FILE);
        timeoutHandler.removeCallbacks(downloadTimeout);
        timeoutHandler.postDelayed(downloadTimeout, Constants.TIMEOUT_DOWNLOAD_CHECK_FILE);
    }

    private void onDownloadTimeout() {
        if (!checking) {
            return;
        }
        checking = false;
        cancelDownload();
        cancelLoadingDialog();
        showErrorDialog(getString(R.string.dialog_error_timeout));
    }

    private void init() {
        File file = new File(getExternalCacheDir(), Constants.FILE_CHECK_JSON);
        try {
            ArrayList<AppListView.AppData> list = Common.buildAppList(file);
            ListView listView = findViewById(R.id.main_list_view);
            listView.setAdapter(new AppListView.AppListAdapter(list, this::onItemClick));
            selectAppText.setVisibility(View.VISIBLE);
            listRetried = false;
            errorCount = 0;
            prefetchHosts(list);
        } catch (Exception ignored) {
            if (!listRetried) {
                listRetried = true;
                FileDownloadTask.clearCache(Constants.URL_CHECK);
                file.delete();
                check();
                return;
            }
            showErrorDialog(getString(R.string.dialog_error_json));
        }
    }

    private static void prefetchHosts(ArrayList<AppListView.AppData> list) {
        Thread thread = new Thread(() -> {
            for (AppListView.AppData data : list) {
                Ipv4Proxy.prefetch(data.url);
            }
        }, "dns-prefetch");
        thread.setDaemon(true);
        thread.start();
    }

    private void onItemClick(AppListView.AppData data) {
        tmpPackageName = data.packageName;
        tmpAppOpen = data.appOpen;

        new AlertDialog.Builder(this)
                .setMessage(
                        data.name + System.lineSeparator() + System.lineSeparator() +
                        data.description + System.lineSeparator() + System.lineSeparator() +
                        getString(R.string.dialog_confirm))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                    try {
                        startDownload(data.url);
                    } catch (Exception e) {
                        showErrorDialog(e.getMessage());
                    }
                })
                .show();
    }

    public DchaInstallTask.Listener dchaInstallTaskListener() {
        return new DchaInstallTask.Listener() {
            @Override
            public void onShow() {
                showLoadingDialog(getString(R.string.progress_state_please_wait, getString(R.string.progress_state_installing)));
            }

            @Override
            public void onSuccess() {
                cancelLoadingDialog();
                errorCount = 0;

                if (!tmpAppOpen) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setCancelable(false)
                            .setMessage(getString(R.string.dialog_install, getString(R.string.dialog_success)))
                            .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                            .show();
                    return;
                }

                connectDcha(0, () -> {
                    MainActivity.this.startActivity(getPackageManager().getLaunchIntentForPackage(tmpPackageName));
                    finishAffinity();
                });
            }

            @Override
            public void onFailure() {
                cancelLoadingDialog();
                showErrorDialog(getString(R.string.dialog_install, getString(R.string.dialog_failure)));
            }
        };
    }

    @Override
    public void onDownloadComplete(int reqCode) {
        timeoutHandler.removeCallbacks(downloadTimeout);
        downloadTask = null;

        if (reqCode == Constants.REQUEST_DOWNLOAD_CHECK_FILE) {
            checking = false;
            cancelLoadingDialog();
            init();
        } else if (reqCode == Constants.REQUEST_DOWNLOAD_APK) {
            cancelLoadingDialog();
            new DchaInstallTask().execute(
                    dcha,
                    dchaInstallTaskListener(),
                    new File(getExternalCacheDir(), Constants.FILE_APK).getAbsolutePath());
        }
    }

    @Override
    public void onDownloadError(int reqCode) {
        timeoutHandler.removeCallbacks(downloadTimeout);
        downloadTask = null;
        checking = false;
        cancelLoadingDialog();
        showErrorDialog(getString(R.string.dialog_error_check_internet, getString(R.string.dialog_error_download)));
    }

    @Override
    public void onConnectionError(int reqCode) {
        timeoutHandler.removeCallbacks(downloadTimeout);
        downloadTask = null;
        checking = false;
        cancelLoadingDialog();
        showErrorDialog(getString(R.string.dialog_error_check_internet, getString(R.string.dialog_error_connection)));
    }

    @Override
    public void onProgressUpdate(int progress, int currentByte, int totalByte) {
        if (totalByte > 0 && dialogProgressBar.isIndeterminate()) {
            dialogProgressBar.setIndeterminate(false);
        }
        progressPercentText.setText(getString(R.string.progress_percent, progress));
        if (totalByte > 0) {
            progressByteText.setText(getString(R.string.progress_byte, formatByte(currentByte), formatByte(totalByte)));
        } else if (progress == 0) {
            progressByteText.setText(R.string.progress_waiting);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) dialogProgressBar.setProgress(progress, true);
        else dialogProgressBar.setProgress(progress);
    }

    private static String formatByte(int bytes) {
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }

    private void startDownload(String downloadFileUrl) {
        cancelLoadingDialog();

        View view = getLayoutInflater().inflate(R.layout.view_progress, null);

        progressPercentText = view.findViewById(R.id.progress_percent);
        progressByteText = view.findViewById(R.id.progress_byte);
        dialogProgressBar = view.findViewById(R.id.progress);

        progressPercentText.setText(getString(R.string.progress_percent, 0));
        progressByteText.setText(R.string.progress_waiting);

        dialogProgressBar.setIndeterminate(true);
        dialogProgressBar.setMax(100);

        progressDialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage(getString(R.string.progress_state_please_wait, getString(R.string.progress_state_download_file)))
                .setView(view)
                .create();
        showSizedDialog(progressDialog);

        FileDownloadTask task = startDownloadTask(
                downloadFileUrl,
                new File(getExternalCacheDir(), Constants.FILE_APK),
                Constants.REQUEST_DOWNLOAD_APK);

        progressHandler = new ProgressHandler(Looper.getMainLooper());
        progressHandler.fileDownloadTask = task;
        progressHandler.sendEmptyMessage(0);
    }

    private FileDownloadTask startDownloadTask(String url, File outputFile, int reqCode) {
        cancelDownload();
        FileDownloadTask task = new FileDownloadTask();
        downloadTask = task;
        task.execute(this, url, outputFile, reqCode);
        return task;
    }

    private void cancelDownload() {
        if (downloadTask != null) {
            downloadTask.cancel();
            downloadTask = null;
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.open_settings) {
            settingsOpened = true;
            connectDcha(3, () -> startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }
    }

    public void showLoadingDialog(String message) {
        cancelLoadingDialog();

        View view = getLayoutInflater().inflate(R.layout.view_progress_spinner, null);
        TextView textView = view.findViewById(R.id.view_progress_spinner_text);
        textView.setText(message);

        progressDialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(view)
                .create();

        showSizedDialog(progressDialog);
    }

    private void showSizedDialog(AlertDialog dialog) {
        dialog.show();
        dialog.getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    public void cancelLoadingDialog() {
        if (progressHandler != null) {
            progressHandler.stop();
            progressHandler = null;
        }
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
        progressPercentText = null;
        progressByteText = null;
        dialogProgressBar = null;
    }

    private void showErrorDialog(String message) {
        if (recoveryDisabled) {
            return;
        }
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.dialog_title_error)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> onErrorConfirmed(dialog))
                .show();
    }

    private void onErrorConfirmed(DialogInterface dialog) {
        if (++errorCount >= ERROR_LIMIT) {
            recoveryDisabled = true;
            dialog.dismiss();
            return;
        }
        check();
    }
}
