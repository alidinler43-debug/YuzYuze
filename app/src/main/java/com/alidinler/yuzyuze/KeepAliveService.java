package com.alidinler.yuzyuze;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

/**
 * Ön plan servisi: WebView'i yaşatır, böylece uygulama kapalıyken bile
 * arama ve mesaj gelir. Görüşme sırasında kamera/mikrofon türleriyle çalışır.
 */
public class KeepAliveService extends Service {
    public static final String A_DECLINE = "com.alidinler.yuzyuze.DECLINE";
    public static final String A_CALL_ON = "com.alidinler.yuzyuze.CALL_ON";
    public static final String A_CALL_OFF = "com.alidinler.yuzyuze.CALL_OFF";

    private static boolean inCall = false;

    public static void start(Context c) {
        try {
            ContextCompat.startForegroundService(c, new Intent(c, KeepAliveService.class));
        } catch (Exception ignored) { }
    }

    public static void setCall(Context c, boolean on) {
        try {
            ContextCompat.startForegroundService(c,
                    new Intent(c, KeepAliveService.class).setAction(on ? A_CALL_ON : A_CALL_OFF));
        } catch (Exception ignored) { }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        foreground(inCall);
        WebHost.get(this).ensure();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (A_CALL_ON.equals(a)) {
            foreground(true);
        } else if (A_CALL_OFF.equals(a)) {
            foreground(false);
        } else if (A_DECLINE.equals(a)) {
            foreground(inCall);
            Notifier.cancelIncoming(this);
            WebHost.get(this).js("window.__decline && window.__decline()");
        } else {
            foreground(inCall);
        }
        WebHost.get(this).ensure();
        return START_STICKY;
    }

    private void foreground(boolean call) {
        inCall = call;
        int type = 0;
        if (Build.VERSION.SDK_INT >= 29) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            if (call && Build.VERSION.SDK_INT >= 30) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
        }
        try {
            ServiceCompat.startForeground(this, Notifier.ID_LINK, Notifier.link(this, call), type);
        } catch (Exception e) {
            try {
                startForeground(Notifier.ID_LINK, Notifier.link(this, call));
            } catch (Exception ignored) { }
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Kullanıcı uygulamayı son uygulamalardan kaydırsa da servis ve WebView yaşamaya devam eder.
        super.onTaskRemoved(rootIntent);
        foreground(inCall);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
