package jp.co.benesse.touch.setuplogin.data.task;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jp.co.benesse.touch.setuplogin.data.event.DownloadEventListener;
import jp.co.benesse.touch.setuplogin.data.event.DownloadEventListenerList;

public class FileDownloadTask {

    DownloadEventListenerList downloadEventListenerList;
    int totalByte = 0, currentByte = 0;

    public void execute(DownloadEventListener downloadEventListener, String downloadUrl, File outputFile, int reqCode) {
        onPreExecute(downloadEventListener);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            Handler handler = new Handler(Looper.getMainLooper());
            new Thread(() -> {
                Boolean result = doInBackground(downloadUrl, outputFile);
                handler.post(() -> onPostExecute(result, reqCode));
            }).start();
        });
    }

    void onPreExecute(DownloadEventListener downloadEventListener) {
        downloadEventListenerList = new DownloadEventListenerList();
        downloadEventListenerList.addEventListener(downloadEventListener);
    }

    void onPostExecute(Boolean result, int reqCode) {
        if (result != null) {
            if (result) {
                totalByte = -1;
                downloadEventListenerList.downloadCompleteNotify(reqCode);
            } else {
                totalByte = -1;
                downloadEventListenerList.downloadErrorNotify(reqCode);
            }
        } else {
            totalByte = -1;
            downloadEventListenerList.connectionErrorNotify(reqCode);
        }
    }

    protected Boolean doInBackground(String downloadUrl, File outputFile) {
        BufferedInputStream bufferedInputStream;
        FileOutputStream fileOutputStream;
        byte[] buffer = new byte[1024];

        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(downloadUrl).openConnection();
            httpURLConnection.setReadTimeout(20000);
            httpURLConnection.setConnectTimeout(20000);
            bufferedInputStream = new BufferedInputStream(httpURLConnection.getInputStream(), 1024);
            fileOutputStream = new FileOutputStream(outputFile);
            totalByte = httpURLConnection.getContentLength();
        } catch (SocketTimeoutException | MalformedURLException ignored) {
            return false;
        } catch (IOException ignored) {
            return null;
        }

        try {
            int len;

            while ((len = bufferedInputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, len);
                currentByte += len;
            }
        } catch (IOException ignored) {
            return false;
        }

        try {
            fileOutputStream.flush();
            fileOutputStream.close();
            bufferedInputStream.close();
        } catch (IOException ignored) {
        }

        return true;
    }

    public int getLoadedBytePercent() {
        if (totalByte <= 0) return 0;
        return (int) Math.floor(((double) getLoadedCurrentByte() / getLoadedTotalByte()) * 100);
    }

    public int getLoadedCurrentByte() {
        if (totalByte <= 0) return 0;
        return currentByte / (1024 * 1024);
    }

    public int getLoadedTotalByte() {
        return totalByte / (1024 * 1024);
    }

    public void onProgressUpdate(int progress) {
        downloadEventListenerList.progressUpdate(progress, getLoadedCurrentByte(), getLoadedTotalByte());
    }

    public boolean isFinish() {
        return totalByte == -1;
    }
}
