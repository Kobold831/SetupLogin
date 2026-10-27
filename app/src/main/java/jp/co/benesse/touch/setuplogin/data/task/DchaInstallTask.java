package jp.co.benesse.touch.setuplogin.data.task;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jp.co.benesse.touch.setuplogin.data.service.DchaConnection;

public class DchaInstallTask {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public void execute(DchaConnection dcha, Listener listener, String installData) {
        if (listener != null) {
            listener.onShow();
        }

        EXECUTOR.execute(() -> {
            boolean success = dcha.installApp(installData);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (listener == null) {
                    return;
                }
                if (success) {
                    listener.onSuccess();
                } else {
                    listener.onFailure();
                }
            });
        });
    }

    public interface Listener {
        void onShow();

        void onSuccess();

        void onFailure();
    }
}
