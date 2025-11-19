package jp.co.benesse.touch.setuplogin.data.task;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jp.co.benesse.touch.setuplogin.data.event.DownloadEventListener;
import jp.co.benesse.touch.setuplogin.data.event.DownloadEventListenerList;

public class FileDownloadTask {
    private DownloadEventListenerList downloadEventListenerList;
    private volatile int totalByte = 0;
    private volatile int currentByte = 0;
    private volatile boolean isRunning = false;

    public void execute(DownloadEventListener downloadEventListener, String downloadUrl, File outputFile, int reqCode) {
        onPreExecute(downloadEventListener);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executorService.submit(() -> {
            isRunning = true;
            Boolean result = doInBackground(downloadUrl, outputFile);
            isRunning = false;
            // UIスレッドへ通知
            handler.post(() -> onPostExecute(result, reqCode));
            executorService.shutdown();
        });
    }

    void onPreExecute(DownloadEventListener downloadEventListener) {
        downloadEventListenerList = new DownloadEventListenerList();
        downloadEventListenerList.addEventListener(downloadEventListener);
        totalByte = 0;
        currentByte = 0;
    }

    /**
     * 結果の判定ロジック
     * true  : 成功
     * false : 接続エラー (Timeout, URL不正, サーバーエラー)
     * null  : 保存エラー (IO例外, ディスク容量不足など)
     */
    void onPostExecute(Boolean result, int reqCode) {
        // 完了を表すために -1 に設定
        totalByte = -1;
        if (result == null) {
            // IOException 等
            downloadEventListenerList.downloadErrorNotify(reqCode);
        } else if (result) {
            // 成功
            downloadEventListenerList.downloadCompleteNotify(reqCode);
        } else {
            // 接続エラー
            downloadEventListenerList.connectionErrorNotify(reqCode);
        }
    }

    protected Boolean doInBackground(String downloadUrl, File outputFile) {
        HttpURLConnection httpURLConnection = null;
        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        BufferedInputStream bufferedInputStream = null;

        try {
            URL url = new URL(downloadUrl);
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setReadTimeout(20000);
            httpURLConnection.setConnectTimeout(20000);
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.connect();
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return false;
            }
            totalByte = httpURLConnection.getContentLength();
            inputStream = httpURLConnection.getInputStream();
            bufferedInputStream = new BufferedInputStream(inputStream, 8192);
            fileOutputStream = new FileOutputStream(outputFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = bufferedInputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, len);
                currentByte += len;
            }
            fileOutputStream.flush();
            return true;

        } catch (SocketTimeoutException | MalformedURLException ignored) {
            return false;
        } catch (IOException ignored) {
            return null;
        } finally {
            try {
                if (fileOutputStream != null) fileOutputStream.close();
                if (bufferedInputStream != null) bufferedInputStream.close();
                if (inputStream != null) inputStream.close();
                if (httpURLConnection != null) httpURLConnection.disconnect();
            } catch (IOException ignored) {
            }
        }
    }

    // 進捗(%)の計算
    public int getLoadedBytePercent() {
        // 分母が0以下なら進捗は0とする（ゼロ除算防止）
        if (totalByte <= 0) return 0;
        long percent = ((long) currentByte * 100) / totalByte;
        // 100を超えないようにガード
        if (percent > 100) return 100;
        return (int) percent;
    }

    // 現在のダウンロード量 (MB)
    public int getLoadedCurrentByte() {
        return currentByte / (1024 * 1024);
    }

    // 合計サイズ (MB)
    public int getLoadedTotalByte() {
        if (totalByte <= 0) return 0;
        return totalByte / (1024 * 1024);
    }

    public void onProgressUpdate(int progress) {
        if (downloadEventListenerList != null) {
            downloadEventListenerList.progressUpdate(progress, getLoadedCurrentByte(), getLoadedTotalByte());
        }
    }

    public boolean isFinish() {
        return totalByte == -1;
    }
}