package com.alidinler.yuzyuze;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;

public class App extends Application {
    public static final String CH_LINK = "link";
    public static final String CH_MSG = "messages";
    public static final String CH_CALL = "calls";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel link = new NotificationChannel(CH_LINK, "Arka plan bağlantısı", NotificationManager.IMPORTANCE_LOW);
        link.setDescription("Uygulama kapalıyken de arama ve mesaj alabilmek için açık kalır");
        link.setShowBadge(false);
        nm.createNotificationChannel(link);

        NotificationChannel msg = new NotificationChannel(CH_MSG, "Mesajlar", NotificationManager.IMPORTANCE_HIGH);
        msg.enableVibration(true);
        nm.createNotificationChannel(msg);

        NotificationChannel call = new NotificationChannel(CH_CALL, "Gelen aramalar", NotificationManager.IMPORTANCE_HIGH);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        call.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), attrs);
        call.enableVibration(true);
        call.setVibrationPattern(new long[]{0, 700, 400, 700, 400, 700});
        call.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(call);
    }
}
