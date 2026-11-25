package com.alidinler.yuzyuze;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon açılınca ve uygulama güncellenince arka plan servisini başlatır. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)) {
            KeepAliveService.start(context);
        }
    }
}
