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

public class IDchaTask {

    public void execute(Context context, Listener listener) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            Handler handler = new Handler(Looper.getMainLooper());
            new Thread(() -> handler.post(() -> doInBackground(context, listener))).start();
        });
    }

    protected void doInBackground(Context context, Listener listener) {
        if (!tryBindDchaService(context, listener)) {
            listener.onFailure();
        }
    }

    public interface Listener {
        void onSuccess(IDchaService iDchaService);

        void onFailure();
    }

    public boolean tryBindDchaService(Context context, Listener listener) {
        return context.bindService(new Intent(Constants.DCHA_SERVICE).setPackage(Constants.DCHA_PACKAGE), new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                IDchaService iDchaService = IDchaService.Stub.asInterface(iBinder);
                listener.onSuccess(iDchaService);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        }, Context.BIND_AUTO_CREATE);
    }
}
