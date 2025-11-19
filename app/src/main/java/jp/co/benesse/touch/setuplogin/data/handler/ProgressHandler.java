package jp.co.benesse.touch.setuplogin.data.handler;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import jp.co.benesse.touch.setuplogin.data.task.FileDownloadTask;

public class ProgressHandler extends Handler {
    public FileDownloadTask fileDownloadTask;
    private boolean isStopped = false;

    public ProgressHandler(Looper looper) {
        super(looper);
    }

    public void stop() {
        isStopped = true;
        removeCallbacksAndMessages(null);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        super.handleMessage(msg);
        if (isStopped || fileDownloadTask == null) {
            return;
        }
        int percent = fileDownloadTask.getLoadedBytePercent();
        boolean isFinish = fileDownloadTask.isFinish();
        if (percent >= 100 || isFinish) {
            fileDownloadTask.onProgressUpdate(100);
            stop();
            return;
        }
        fileDownloadTask.onProgressUpdate(percent);
        sendEmptyMessageDelayed(0, 100);
    }
}