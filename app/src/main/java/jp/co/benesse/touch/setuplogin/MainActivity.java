package jp.co.benesse.touch.setuplogin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

import jp.co.benesse.touch.setuplogin.data.event.DownloadEventListener;
import jp.co.benesse.touch.setuplogin.data.handler.ProgressHandler;
import jp.co.benesse.touch.setuplogin.data.service.DchaConnection;
import jp.co.benesse.touch.setuplogin.data.task.DchaInstallTask;
import jp.co.benesse.touch.setuplogin.data.task.FileDownloadTask;
import jp.co.benesse.touch.setuplogin.util.Common;
import jp.co.benesse.touch.setuplogin.util.Constants;
import jp.co.benesse.touch.setuplogin.views.AppListView;

public class MainActivity extends Activity implements DownloadEventListener, View.OnClickListener {

    private String tmpPackageName;
    private boolean tmpAppOpen;
    private AlertDialog progressDialog;
    private TextView progressPercentText;
    private TextView progressByteText;
    private ProgressBar dialogProgressBar;
    private ProgressHandler progressHandler;
    private TextView selectAppText;
    private DchaConnection dcha;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.open_settings).setOnClickListener(this);
        selectAppText = findViewById(R.id.select_app_text);
        selectAppText.setVisibility(View.GONE);
        dcha = new DchaConnection(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        check();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelLoadingDialog();
        dcha.disconnect();
    }

    private void connectDcha(int setupStatus, Runnable action) {
        dcha.setSetupStatus(setupStatus);
        dcha.hideNavigationBar(false);
        action.run();
    }

    private void check() {
        String error = Common.getDchaCheckError(this);
        if (!error.isEmpty()) {
            showErrorDialog(error);
            return;
        }

        TextView version = findViewById(R.id.main_text_v);
        version.setText(getString(R.string.version_prefix) + BuildConfig.VERSION_NAME);

        dcha.connect(
                () -> {
                    dcha.setSetupStatus(3);
                    dcha.setSetupStatus(0);
                    dcha.hideNavigationBar(false);
                    showLoadingDialog(getString(R.string.progress_state_please_wait, getString(R.string.progress_state_connecting)));
                    new FileDownloadTask().execute(
                            MainActivity.this,
                            Constants.URL_CHECK,
                            new File(getExternalCacheDir(), Constants.FILE_CHECK_JSON),
                            Constants.REQUEST_DOWNLOAD_CHECK_FILE);
                },
                () -> showErrorDialog(getString(R.string.dialog_error)));
    }

    private void init() {
        try {
            ArrayList<AppListView.AppData> list = Common.buildAppList(new File(getExternalCacheDir(), Constants.FILE_CHECK_JSON));
            ListView listView = findViewById(R.id.main_list_view);
            listView.setAdapter(new AppListView.AppListAdapter(list, this::onItemClick));
            selectAppText.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            showErrorDialog(e.getMessage());
        }
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
                new AlertDialog.Builder(MainActivity.this)
                        .setCancelable(false)
                        .setMessage(getString(R.string.dialog_install, getString(R.string.dialog_failure)))
                        .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> check())
                        .show();
            }
        };
    }

    @Override
    public void onDownloadComplete(int reqCode) {
        if (reqCode == Constants.REQUEST_DOWNLOAD_CHECK_FILE) {
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
        cancelLoadingDialog();
        showErrorDialog(getString(R.string.dialog_error_check_internet, getString(R.string.dialog_error_download)));
    }

    @Override
    public void onConnectionError(int reqCode) {
        cancelLoadingDialog();
        showErrorDialog(getString(R.string.dialog_error_check_internet, getString(R.string.dialog_error_connection)));
    }

    @Override
    public void onProgressUpdate(int progress, int currentByte, int totalByte) {
        progressPercentText.setText(getString(R.string.progress_percent, progress));
        progressByteText.setText(getString(R.string.progress_byte, currentByte, totalByte));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) dialogProgressBar.setProgress(progress, true);
        else dialogProgressBar.setProgress(progress);
    }

    private void startDownload(String downloadFileUrl) {
        cancelLoadingDialog();

        View view = getLayoutInflater().inflate(R.layout.view_progress, null);

        progressPercentText = view.findViewById(R.id.progress_percent);
        progressByteText = view.findViewById(R.id.progress_byte);
        dialogProgressBar = view.findViewById(R.id.progress);

        progressPercentText.setText(getString(R.string.progress_percent, 0));
        progressByteText.setText(getString(R.string.progress_byte, 0, 0));

        dialogProgressBar.setIndeterminate(false);
        dialogProgressBar.setMax(100);
        dialogProgressBar.setProgress(0);

        FileDownloadTask fileDownloadTask = new FileDownloadTask();
        fileDownloadTask.execute(
                this,
                downloadFileUrl,
                new File(getExternalCacheDir(), Constants.FILE_APK),
                Constants.REQUEST_DOWNLOAD_APK);

        progressHandler = new ProgressHandler(Looper.getMainLooper());
        progressHandler.fileDownloadTask = fileDownloadTask;
        progressHandler.sendEmptyMessage(0);

        progressDialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage(getString(R.string.progress_state_please_wait, getString(R.string.progress_state_download_file)))
                .setView(view)
                .create();

        showSizedDialog(progressDialog);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.open_settings) {
            connectDcha(3, () -> {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                //finishAffinity();
            });
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
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.dialog_title_error)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok, (d, i) -> check())
                .show();
    }
}
