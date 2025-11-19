package jp.co.benesse.touch.setuplogin.data.task;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;

import jp.co.benesse.dcha.dchaservice.IDchaService;
import jp.co.benesse.touch.setuplogin.util.Constants;

public class IDchaTask {

    public interface Listener {
        void onSuccess(IDchaService iDchaService, ServiceConnection connection);
        void onFailure();
    }

    public void execute(Context context, Listener listener) {
        new Handler(Looper.getMainLooper()).post(() -> {
            doInBackground(context, listener);
        });
    }

    protected void doInBackground(Context context, Listener listener) {
        if (!tryBindDchaService(context, listener)) {
            listener.onFailure();
        }
    }

    public boolean tryBindDchaService(@NonNull Context context, Listener listener) {
        Intent intent = new Intent(Constants.DCHA_SERVICE).setPackage(Constants.DCHA_PACKAGE);
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                IDchaService iDchaService = IDchaService.Stub.asInterface(iBinder);
                listener.onSuccess(iDchaService, this);
            }
            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };

        return context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }
}