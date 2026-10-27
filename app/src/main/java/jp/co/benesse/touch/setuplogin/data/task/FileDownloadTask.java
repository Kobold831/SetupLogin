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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import jp.co.benesse.touch.setuplogin.data.event.DownloadEventListener;
import jp.co.benesse.touch.setuplogin.data.net.Ipv4Proxy;
import jp.co.benesse.touch.setuplogin.util.Constants;

public class FileDownloadTask {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 30000;
    private static final String HEADER_ETAG = "ETag";

    private static final Map<String, String> ETAG_MAP = new ConcurrentHashMap<>();
    private static final AtomicInteger TMP_SEQ = new AtomicInteger();

    private static volatile boolean sslInitialized = false;

    private DownloadEventListener listener;
    private volatile int totalByte = 0;
    private volatile int currentByte = 0;
    private volatile boolean finished = false;
    private volatile boolean cancelled = false;
    private volatile HttpURLConnection connection;

    public void execute(DownloadEventListener downloadEventListener, String downloadUrl, File outputFile, int reqCode) {
        onPreExecute(downloadEventListener);
        Handler handler = new Handler(Looper.getMainLooper());
        EXECUTOR.execute(() -> {
            Boolean outcome;
            try {
                outcome = doInBackground(downloadUrl, outputFile, false);
            } catch (Throwable ignored) {
                outcome = null;
            }
            final Boolean result = outcome;
            if (!cancelled) {
                handler.post(() -> onPostExecute(result, reqCode));
            }
        });
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection current = connection;
        if (current != null) {
            current.disconnect();
        }
    }

    public static void clearCache(String downloadUrl) {
        ETAG_MAP.remove(downloadUrl);
    }

    void onPreExecute(DownloadEventListener downloadEventListener) {
        listener = downloadEventListener;
        totalByte = 0;
        currentByte = 0;
        finished = false;
        cancelled = false;
        connection = null;
    }

    void onPostExecute(Boolean result, int reqCode) {
        if (cancelled || listener == null) {
            return;
        }
        finished = true;
        totalByte = -1;
        if (result == null) {
            listener.onDownloadError(reqCode);
        } else if (result) {
            listener.onDownloadComplete(reqCode);
        } else {
            listener.onConnectionError(reqCode);
        }
    }

    protected Boolean doInBackground(String downloadUrl, File outputFile, boolean withoutCache) {
        HttpURLConnection httpURLConnection = null;
        removeStaleTempFiles(outputFile);
        File tempFile = new File(outputFile.getPath() + "." + TMP_SEQ.incrementAndGet() + Constants.TMP_EXT);

        try {
            initSslIfNeeded();

            httpURLConnection = (HttpURLConnection) new URL(downloadUrl).openConnection(Ipv4Proxy.get());
            connection = httpURLConnection;
            httpURLConnection.setReadTimeout(READ_TIMEOUT);
            httpURLConnection.setConnectTimeout(CONNECT_TIMEOUT);
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setRequestProperty("Accept-Encoding", "identity");

            if (!withoutCache) {
                String ETag = ETAG_MAP.get(downloadUrl);
                if (ETag != null) {
                    httpURLConnection.setRequestProperty("If-None-Match", ETag);
                }
            }

            int responseCode = httpURLConnection.getResponseCode();
            if (cancelled) {
                return null;
            }

            if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                if (outputFile.length() > 0) {
                    return true;
                }
                ETAG_MAP.remove(downloadUrl);
                return doInBackground(downloadUrl, outputFile, true);
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return false;
            }

            String ETag = httpURLConnection.getHeaderField(HEADER_ETAG);
            totalByte = httpURLConnection.getContentLength();

            try (InputStream bufferedInputStream = new BufferedInputStream(httpURLConnection.getInputStream(), BUFFER_SIZE);
                 FileOutputStream fileOutputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = bufferedInputStream.read(buffer)) != -1) {
                    if (cancelled) {
                        return null;
                    }
                    fileOutputStream.write(buffer, 0, len);
                    currentByte += len;
                }
                fileOutputStream.flush();
            }

            if (totalByte > 0 && currentByte < totalByte) {
                return null;
            }

            if (outputFile.exists() && !outputFile.delete()) {
                return false;
            }
            if (!tempFile.renameTo(outputFile)) {
                return false;
            }

            if (ETag != null) {
                ETAG_MAP.put(downloadUrl, ETag);
            } else {
                ETAG_MAP.remove(downloadUrl);
            }
            return true;
        } catch (SocketTimeoutException | MalformedURLException | NoSuchAlgorithmException | KeyManagementException ignored) {
            return false;
        } catch (IOException ignored) {
            return null;
        } finally {
            connection = null;
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private static void removeStaleTempFiles(File outputFile) {
        File[] files = outputFile.getParentFile().listFiles((dir, name) ->
                name.startsWith(outputFile.getName() + ".") && name.endsWith(Constants.TMP_EXT));
        if (files == null) {
            return;
        }
        for (File file : files) {
            file.delete();
        }
    }

    private static synchronized void initSslIfNeeded() throws NoSuchAlgorithmException, KeyManagementException {
        if (sslInitialized) {
            return;
        }

        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HostnameVerifier hostnameVerifier = (hostname, session) -> true;
        HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
        sslInitialized = true;
    }

    public int getLoadedBytePercent() {
        if (finished) return 100;
        if (totalByte <= 0) return 0;
        long percent = ((long) currentByte * 100) / totalByte;
        return (int) Math.min(99, percent);
    }

    public void onProgressUpdate(int progress) {
        if (listener != null) {
            listener.onProgressUpdate(progress, currentByte, totalByte);
        }
    }

    public boolean isFinish() {
        return finished;
    }
}
