package com.alidinler.yuzyuze;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.MutableContextWrapper;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Tek WebView: arka plan servisi tarafından oluşturulur ve yaşatılır, ekran açıkken
 * MainActivity'ye takılır. Böylece uygulama kapatılsa bile bağlantı kopmaz.
 */
public final class WebHost {
    public static final String URL = "https://appassets.androidplatform.net/assets/index.html";

    private static WebHost instance;

    public static synchronized WebHost get(Context c) {
        if (instance == null) instance = new WebHost(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final MutableContextWrapper ctx;
    private WebView web;
    private WeakReference<MainActivity> act = new WeakReference<>(null);
    private boolean loaded = false;
    private final List<String> pendingJs = new ArrayList<>();

    public volatile boolean foreground = false;
    public boolean keepAwake = false;
    ValueCallback<Uri[]> fileCallback;

    private WebHost(Context app) {
        this.app = app;
        this.ctx = new MutableContextWrapper(app);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public synchronized WebView ensure() {
        if (web != null) return web;
        web = new WebView(ctx);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        web.setBackgroundColor(0xFF0E1420);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(app))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u == null || "appassets.androidplatform.net".equals(u.getHost())) return false;
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, u);
                    MainActivity a = act.get();
                    if (a != null) a.startActivity(i);
                    else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); app.startActivity(i); }
                } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                synchronized (WebHost.this) {
                    loaded = true;
                    for (String code : pendingJs) view.evaluateJavascript(code, null);
                    pendingJs.clear();
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                boolean cam = ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                boolean mic = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                ArrayList<String> ok = new ArrayList<>();
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && cam) ok.add(r);
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && mic) ok.add(r);
                }
                if (ok.isEmpty()) {
                    request.deny();
                    MainActivity a = act.get();
                    if (a != null) a.askMediaPermissions();
                } else {
                    request.grant(ok.toArray(new String[0]));
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                MainActivity a = act.get();
                if (a == null) { callback.onReceiveValue(null); return true; }
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    a.startActivityForResult(params.createIntent(), MainActivity.RC_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(app, this), "Android");
        web.loadUrl(URL);
        return web;
    }

    public void attach(MainActivity a, FrameLayout holder) {
        ensure();
        act = new WeakReference<>(a);
        ctx.setBaseContext(a);
        ViewGroup parent = (ViewGroup) web.getParent();
        if (parent != null) parent.removeView(web);
        holder.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.onResume();
        web.resumeTimers();
    }

    public void detach(MainActivity a) {
        if (web == null) return;
        if (act.get() == a) {
            act.clear();
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            ctx.setBaseContext(app);
        }
    }

    public MainActivity activity() {
        return act.get();
    }

    /** JS'i ana iş parçacığında çalıştırır; sayfa henüz yüklenmediyse bekletir. */
    public void js(final String code) {
        final WebView w = ensure();
        w.post(new Runnable() {
            @Override
            public void run() {
                synchronized (WebHost.this) {
                    if (!loaded) { pendingJs.add(code); return; }
                }
                w.evaluateJavascript(code, null);
            }
        });
    }
}
