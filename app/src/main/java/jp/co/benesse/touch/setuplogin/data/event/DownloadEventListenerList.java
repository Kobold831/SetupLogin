package jp.co.benesse.touch.setuplogin.data.event;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class DownloadEventListenerList {

    private final Set<DownloadEventListener> listeners = new CopyOnWriteArraySet<>();

    public void addEventListener(DownloadEventListener l) { if (l != null) listeners.add(l); }

    public void removeEventListener(DownloadEventListener l) { listeners.remove(l); }

    public void downloadCompleteNotify(int r) { for (DownloadEventListener l : listeners) l.onDownloadComplete(r); }

    public void downloadErrorNotify(int r) { for (DownloadEventListener l : listeners) l.onDownloadError(r); }

    public void connectionErrorNotify(int r) { for (DownloadEventListener l : listeners) l.onConnectionError(r); }

    public void progressUpdate(int p, int c, int t) { for (DownloadEventListener l : listeners) l.onProgressUpdate(p, c, t); }
}