package com.alidinler.yuzyuze;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public final class Notifier {
    public static final int ID_LINK = 1;
    public static final int ID_CALL = 2;
    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private Notifier() { }

    private static PendingIntent open(Context c, String action, String code, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.setAction(action);
        if (code != null) i.putExtra("code", code);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, req, i, FLAGS);
    }

    static boolean canNotify(Context c) {
        return Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Ön plan servisinin kalıcı bildirimi. */
    public static Notification link(Context c, boolean inCall) {
        return new NotificationCompat.Builder(c, App.CH_LINK)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(inCall ? "Yüz Yüze · görüşme sürüyor" : "Yüz Yüze")
                .setContentText(inCall ? "Dokunarak görüşmeye dön" : "Aramalar ve mesajlar için bağlı")
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(inCall ? NotificationCompat.CATEGORY_CALL : NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(open(c, MainActivity.ACT_SHOW, null, 0))
                .build();
    }

    public static void message(Context c, String title, String body, String code) {
        if (!canNotify(c)) return;
        int id = code == null ? 100 : ("m" + code).hashCode();
        Notification n = new NotificationCompat.Builder(c, App.CH_MSG)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(open(c, MainActivity.ACT_OPEN, code, id))
                .build();
        NotificationManagerCompat.from(c).notify(id, n);
    }

    public static void incoming(Context c, String name, String kind) {
        if (!canNotify(c)) return;
        boolean video = "video".equals(kind);
        Intent decline = new Intent(c, KeepAliveService.class).setAction(KeepAliveService.A_DECLINE);
        PendingIntent declinePi = PendingIntent.getForegroundService(c, 3, decline, FLAGS);
        PendingIntent show = open(c, MainActivity.ACT_SHOW, null, 2);
        Notification n = new NotificationCompat.Builder(c, App.CH_CALL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(name)
                .setContentText(video ? "Görüntülü arıyor…" : "Sesli arıyor…")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(show, true)
                .setContentIntent(show)
                .addAction(0, "Reddet", declinePi)
                .addAction(0, "Cevapla", open(c, MainActivity.ACT_ANSWER, null, 4))
                .build();
        NotificationManagerCompat.from(c).notify(ID_CALL, n);
    }

    public static void cancelIncoming(Context c) {
        NotificationManagerCompat.from(c).cancel(ID_CALL);
    }
}
