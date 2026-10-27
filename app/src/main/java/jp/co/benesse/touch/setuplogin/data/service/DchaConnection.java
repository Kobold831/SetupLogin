package jp.co.benesse.touch.setuplogin.data.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import jp.co.benesse.dcha.dchaservice.IDchaService;
import jp.co.benesse.touch.setuplogin.util.Constants;

public class DchaConnection implements ServiceConnection {

    private final Context context;
    private IDchaService mDchaService;
    private boolean bound;
    private Runnable onConnected;

    public DchaConnection(Context context) {
        this.context = context.getApplicationContext();
    }

    public void connect(Runnable onConnected, Runnable onBindFailed) {
        this.onConnected = onConnected;

        if (mDchaService != null) {
            consume(onConnected);
            return;
        }
        if (bound) {
            return;
        }
        try {
            bound = context.bindService(new Intent(Constants.DCHA_SERVICE).setPackage(Constants.DCHA_PACKAGE), this, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException ignored) {
            bound = false;
        }
        if (!bound) {
            consume(onBindFailed);
        }
    }

    public void disconnect() {
        if (bound) {
            try {
                context.unbindService(this);
            } catch (IllegalArgumentException ignored) {
            }
            bound = false;
        }
        mDchaService = null;
        onConnected = null;
    }

    public void setSetupStatus(int status) {
        try {
            mDchaService.setSetupStatus(status);
        } catch (RemoteException ignored) {
        }
    }

    public void hideNavigationBar(boolean hide) {
        try {
            mDchaService.hideNavigationBar(hide);
        } catch (RemoteException ignored) {
        }
    }

    public boolean installApp(String path) {
        try {
            return mDchaService.installApp(path, 1);
        } catch (RemoteException ignored) {
            return false;
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        mDchaService = IDchaService.Stub.asInterface(binder);
        consume(onConnected);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mDchaService = null;
    }

    private void consume(Runnable runnable) {
        onConnected = null;
        if (runnable != null) {
            runnable.run();
        }
    }
}
