package jp.co.benesse.touch.setuplogin.data.task;

import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jp.co.benesse.dcha.dchaservice.IDchaService;

public class DchaInstallTask {

    private final CountDownLatch latch = new CountDownLatch(1);
    private IDchaService mDchaService = null;
    private ServiceConnection mConnection = null;

    public void execute(Context context, Listener listener, String installData) {
        onPreExecute(listener);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Handler uiHandler = new Handler(Looper.getMainLooper());

        executorService.submit(() -> {
            boolean result = doInBackground(context, installData);
            uiHandler.post(() -> onPostExecute(listener, result));
            executorService.shutdown();
        });
    }

    void onPreExecute(Listener listener) {
        if (listener != null) {
            listener.onShow();
        }
    }

    void onPostExecute(Listener listener, boolean result) {
        if (listener != null) {
            if (result) {
                listener.onSuccess();
            } else {
                listener.onFailure();
            }
        }
    }

    protected boolean doInBackground(Context context, String installData) {
        try {
            new IDchaTask().execute(context, new IDchaTask.Listener() {
                @Override
                public void onSuccess(IDchaService iDchaService, ServiceConnection connection) {
                    mDchaService = iDchaService;
                    mConnection = connection;
                    latch.countDown();
                }

                @Override
                public void onFailure() {
                    latch.countDown();
                }
            });

            latch.await();

            if (mDchaService == null) {
                return false;
            }

            return mDchaService.installApp(installData, 1);

        } catch (Exception e) {
            return false;
        } finally {
            if (mConnection != null) {
                try {
                    context.unbindService(mConnection);
                } catch (IllegalArgumentException ignored) {
                }
                mConnection = null;
            }
        }
    }

    public interface Listener {
        void onShow();
        void onSuccess();
        void onFailure();
    }
}