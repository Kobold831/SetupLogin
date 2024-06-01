package jp.co.benesse.touch.setuplogin.data.task;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jp.co.benesse.dcha.dchaservice.IDchaService;
import jp.co.benesse.touch.setuplogin.util.Constants;

public class DchaInstallTask {

    IDchaService mDchaService;

    public void execute(Context context, Listener listener, String installData) {
        onPreExecute(listener);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            Handler handler = new Handler(Looper.getMainLooper());
            new Thread(() -> {
                tryBindDchaService(context, mDchaService, mDchaServiceConnection, Constants.FLAG_CHECK, 0, "");

                Runnable runnable = () -> {
                    Boolean result = doInBackground(context, installData);
                    handler.post(() -> onPostExecute(listener, result));
                };

                new Handler(Looper.getMainLooper()).postDelayed(runnable, 1000);
            }).start();
        });
    }

    void onPreExecute(Listener listener) {
        listener.onShow();
    }

    void onPostExecute(Listener listener, Boolean result) {
        if (result) {
            listener.onSuccess();
        } else {
            listener.onFailure();
        }
    }

    protected Boolean doInBackground(Context context, String installData) {
        return tryBindDchaService(context, mDchaService, mDchaServiceConnection, Constants.FLAG_INSTALL_PACKAGE, 0, installData);
    }

    public interface Listener {

        void onShow();

        void onSuccess();

        void onFailure();
    }

    ServiceConnection mDchaServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mDchaService = IDchaService.Stub.asInterface(iBinder);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        }
    };

    public static boolean tryBindDchaService(Context context, IDchaService iDchaService, ServiceConnection serviceConnection, int reqCode, int i, String s) {
        try {
            switch (reqCode) {
                case Constants.FLAG_INSTALL_PACKAGE:
                    return iDchaService.installApp(s, i);
                case Constants.FLAG_CHECK:
                    return context.getApplicationContext().bindService(new Intent("jp.co.benesse.dcha.dchaservice.DchaService").setPackage("jp.co.benesse.dcha.dchaservice"), serviceConnection, Context.BIND_AUTO_CREATE);
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }
}
