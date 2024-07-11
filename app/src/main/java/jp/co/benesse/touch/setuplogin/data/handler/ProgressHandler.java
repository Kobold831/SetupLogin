package jp.co.benesse.touch.setuplogin.data.handler;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;

import jp.co.benesse.touch.setuplogin.data.task.FileDownloadTask;

public class ProgressHandler extends Handler {

    public FileDownloadTask fileDownloadTask;

    public ProgressHandler(Looper looper) {
        super(looper);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        super.handleMessage(msg);

        if (fileDownloadTask.getLoadedBytePercent() >= 100) {
            fileDownloadTask.onProgressUpdate(100);
            return;
        }

        if (fileDownloadTask.isFinish()) {
            return;
        }

        fileDownloadTask.onProgressUpdate(fileDownloadTask.getLoadedBytePercent());

        sendEmptyMessageDelayed(0, 100);
    }
}
