package com.alidinler.yuzyuze;

import android.content.Context;
import android.media.AudioManager;

/** Görüşme ses yolu: görüntülüde hoparlör, seslide kulaklık; kullanıcı değiştirebilir. */
public final class AudioRouter {
    private AudioRouter() { }

    private static AudioManager am(Context c) {
        return (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    }

    public static void start(Context c, boolean speaker) {
        AudioManager am = am(c);
        if (am == null) return;
        try {
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            am.setSpeakerphoneOn(speaker);
        } catch (Exception ignored) { }
    }

    public static void stop(Context c) {
        AudioManager am = am(c);
        if (am == null) return;
        try {
            am.setSpeakerphoneOn(false);
            am.setMode(AudioManager.MODE_NORMAL);
        } catch (Exception ignored) { }
    }

    public static void speaker(Context c, boolean on) {
        AudioManager am = am(c);
        if (am == null) return;
        try { am.setSpeakerphoneOn(on); } catch (Exception ignored) { }
    }

    public static boolean isSpeaker(Context c) {
        AudioManager am = am(c);
        return am != null && am.isSpeakerphoneOn();
    }
}
