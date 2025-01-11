package jp.co.benesse.touch.setuplogin.data.task;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jp.co.benesse.dcha.dchaservice.IDchaService;

public class DchaInstallTask {

    final Object objLock = new Object();

    IDchaService mDchaService;

    public void execute(Context context, Listener listener, String installData) {
        onPreExecute(listener);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            Handler handler = new Handler(Looper.getMainLooper());
            new Thread(() -> {
                boolean result = doInBackground(context, installData);
                handler.post(() -> onPostExecute(listener, result));
            }).start();
        });
    }

    void onPreExecute(Listener listener) {
        listener.onShow();
    }

    void onPostExecute(Listener listener, boolean result) {
        if (result) {
            listener.onSuccess();
        } else {
            listener.onFailure();
        }
    }

    protected boolean doInBackground(Context context, String installData) {
        try {
            new IDchaTask().execute(context, iDchaTaskListener());
            synchronized (objLock) {
                objLock.wait();
            }
            if (mDchaService == null) return false;
            return mDchaService.installApp(installData, 0);
        } catch (Exception ignored) {
            return false;
        }
    }

    public interface Listener {
        void onShow();

        void onSuccess();

        void onFailure();
    }

    private IDchaTask.Listener iDchaTaskListener() {
        return new IDchaTask.Listener() {
            @Override
            public void onSuccess(IDchaService iDchaService) {
                mDchaService = iDchaService;
                synchronized (objLock) {
                    objLock.notify();
                }
            }

            @Override
            public void onFailure() {
                synchronized (objLock) {
                    objLock.notify();
                }
            }
        };
    }
}
