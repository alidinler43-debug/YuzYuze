package com.alidinler.yuzyuze;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.widget.FrameLayout;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends Activity {
    public static final String ACT_OPEN = "com.alidinler.yuzyuze.OPEN_CHAT";
    public static final String ACT_SHOW = "com.alidinler.yuzyuze.SHOW";
    public static final String ACT_ANSWER = "com.alidinler.yuzyuze.ANSWER";

    public static final int RC_FILE = 11;
    public static final int RC_MEDIA = 12;
    public static final int RC_CONTACTS = 13;

    private FrameLayout holder;
    private WebHost host;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        holder = new FrameLayout(this);
        holder.setBackgroundColor(0xFF0E1420);
        setContentView(holder);

        host = WebHost.get(this);
        host.attach(this, holder);
        applyKeepAwake();

        KeepAliveService.start(this);
        requestStartupPermissions();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        String a = i.getAction();
        if (ACT_OPEN.equals(a)) {
            String code = i.getStringExtra("code");
            if (code != null) host.js("window.__openChat && window.__openChat(" + JSONObject.quote(code) + ")");
        } else if (ACT_ANSWER.equals(a)) {
            host.js("window.__answer && window.__answer()");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        host.foreground = true;
        host.js("window.__fg && window.__fg(true)");
    }

    @Override
    protected void onPause() {
        host.foreground = false;
        host.js("window.__fg && window.__fg(false)");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        host.detach(this);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        host.ensure().evaluateJavascript("(window.__back ? window.__back() : 0)", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (!"1".equals(value)) moveTaskToBack(true);
            }
        });
    }

    public void applyKeepAwake() {
        if (host != null && host.keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /* ---------- izinler ---------- */

    private boolean has(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStartupPermissions() {
        ArrayList<String> need = new ArrayList<>();
        if (!has(Manifest.permission.CAMERA)) need.add(Manifest.permission.CAMERA);
        if (!has(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO);
        if (!has(Manifest.permission.READ_CONTACTS)) need.add(Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (need.isEmpty()) askSystemSettings();
        else ActivityCompat.requestPermissions(this, need.toArray(new String[0]), RC_MEDIA);
    }

    public void askMediaPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, RC_MEDIA);
    }

    public void askContactsPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, RC_CONTACTS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean contacts = false;
        for (int k = 0; k < permissions.length && k < grantResults.length; k++) {
            if (Manifest.permission.READ_CONTACTS.equals(permissions[k]) && grantResults[k] == PackageManager.PERMISSION_GRANTED) contacts = true;
        }
        if (contacts) host.js("window.__contactsReady && window.__contactsReady()");
        if (requestCode == RC_MEDIA) {
            KeepAliveService.start(this); // bildirim izni geldiyse kalıcı bildirim artık görünür
            askSystemSettings();
        }
    }

    /** Pil iyileştirmesi muafiyeti (arka planda kesintisiz bağlantı) ve tam ekran arama izni; her biri bir kez sorulur. */
    private void askSystemSettings() {
        SharedPreferences sp = getSharedPreferences("yy", MODE_PRIVATE);
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName()) && !sp.getBoolean("askedBattery", false)) {
                sp.edit().putBoolean("askedBattery", true).apply();
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                return;
            }
        } catch (Exception ignored) { }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null && !nm.canUseFullScreenIntent() && !sp.getBoolean("askedFsi", false)) {
                    sp.edit().putBoolean("askedFsi", true).apply();
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
                }
            }
        } catch (Exception ignored) { }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_FILE && host.fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
            host.fileCallback.onReceiveValue(result);
            host.fileCallback = null;
        }
    }
}
