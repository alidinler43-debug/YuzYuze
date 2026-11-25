package com.alidinler.yuzyuze;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.webkit.JavascriptInterface;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;

/** window.Android — sayfanın telefona ulaştığı köprü. JS iş parçacığından çağrılır. */
public class Bridge {
    private final Context app;
    private final WebHost host;
    private final Handler ui = new Handler(Looper.getMainLooper());

    Bridge(Context app, WebHost host) {
        this.app = app;
        this.host = host;
    }

    @JavascriptInterface
    public boolean isForeground() {
        return host.foreground;
    }

    @JavascriptInterface
    public void notify(final String title, final String body, final String code) {
        ui.post(new Runnable() { public void run() { Notifier.message(app, title, body, code); } });
    }

    @JavascriptInterface
    public void share(final String text) {
        ui.post(new Runnable() {
            public void run() {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, text);
                Intent chooser = Intent.createChooser(send, "Davet gönder");
                MainActivity a = host.activity();
                if (a != null) a.startActivity(chooser);
                else { chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); app.startActivity(chooser); }
            }
        });
    }

    @JavascriptInterface
    public boolean hasContactsPermission() {
        return ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Rehberi JSON olarak verir: [{name, tels:[...]}]. İzin yoksa "" döner ve izni ister. */
    @JavascriptInterface
    public String getContacts() {
        if (!hasContactsPermission()) {
            ui.post(new Runnable() { public void run() { MainActivity a = host.activity(); if (a != null) a.askContactsPermission(); } });
            return "";
        }
        JSONArray out = new JSONArray();
        Cursor c = null;
        try {
            c = app.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
            LinkedHashMap<String, JSONObject> byContact = new LinkedHashMap<>();
            if (c != null) {
                while (c.moveToNext()) {
                    String id = c.getString(0);
                    String name = c.getString(1);
                    String num = c.getString(2);
                    if (num == null || id == null) continue;
                    JSONObject o = byContact.get(id);
                    if (o == null) {
                        o = new JSONObject();
                        o.put("name", name == null ? "" : name);
                        o.put("tels", new JSONArray());
                        byContact.put(id, o);
                    }
                    o.getJSONArray("tels").put(num);
                }
            }
            for (JSONObject o : byContact.values()) out.put(o);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out.toString();
    }

    @JavascriptInterface
    public void callRinging(final String name, final String kind) {
        ui.post(new Runnable() { public void run() { Notifier.incoming(app, name, kind); } });
    }

    @JavascriptInterface
    public void callStop() {
        ui.post(new Runnable() { public void run() { Notifier.cancelIncoming(app); } });
    }

    @JavascriptInterface
    public void callActive(final String kind) {
        ui.post(new Runnable() {
            public void run() {
                Notifier.cancelIncoming(app);
                KeepAliveService.setCall(app, true);
                AudioRouter.start(app, "video".equals(kind));
            }
        });
    }

    @JavascriptInterface
    public void callIdle() {
        ui.post(new Runnable() {
            public void run() {
                Notifier.cancelIncoming(app);
                KeepAliveService.setCall(app, false);
                AudioRouter.stop(app);
            }
        });
    }

    @JavascriptInterface
    public void setSpeaker(final boolean on) {
        ui.post(new Runnable() { public void run() { AudioRouter.speaker(app, on); } });
    }

    @JavascriptInterface
    public boolean isSpeaker() {
        return AudioRouter.isSpeaker(app);
    }

    @JavascriptInterface
    public void keepAwake(final boolean on) {
        ui.post(new Runnable() {
            public void run() {
                host.keepAwake = on;
                MainActivity a = host.activity();
                if (a != null) a.applyKeepAwake();
            }
        });
    }

    @JavascriptInterface
    public void bringToFront() {
        ui.post(new Runnable() {
            public void run() {
                if (host.activity() != null) return;
                Intent i = new Intent(app, MainActivity.class);
                i.setAction(MainActivity.ACT_SHOW);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                try { app.startActivity(i); } catch (Exception ignored) { }
            }
        });
    }
}
