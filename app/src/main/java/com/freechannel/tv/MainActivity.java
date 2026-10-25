package com.freechannel.tv;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * FREE CHANNEL - full-screen WebView wrapper tuned for Fire TV (Fire OS) and Google TV / Android TV.
 * Change START_URL below to point the app at a different site.
 */
public class MainActivity extends Activity {

    private static final String START_URL = "https://iptv-player-stream-navigator.ai.studio/";

    /** Injects remote-friendly focus rings and makes click-only elements reachable with the D-pad. */
    private static final boolean ENABLE_TV_HELPER = true;

    private static final long AUTO_RETRY_MS = 10_000;
    private static final long BACK_EXIT_WINDOW_MS = 2_000;
    private static final int SEEK_SECONDS = 10;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private View loadingView;
    private View errorView;
    private TextView errorText;
    private Button retryButton;
    private FrameLayout fullscreenContainer;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private String helperJs;
    private String currentUrl = START_URL;
    private boolean pageFailed = false;
    private long lastBackPress = 0;

    private final Runnable autoRetry = new Runnable() {
        @Override
        public void run() {
            if (errorView.getVisibility() == View.VISIBLE) {
                if (isOnline()) {
                    reload();
                } else {
                    handler.postDelayed(this, AUTO_RETRY_MS);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        loadingView = findViewById(R.id.loading);
        errorView = findViewById(R.id.error);
        errorText = findViewById(R.id.error_text);
        retryButton = findViewById(R.id.retry);
        fullscreenContainer = findViewById(R.id.fullscreen_container);

        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reload();
            }
        });

        if (ENABLE_TV_HELPER) {
            helperJs = loadAsset("tv_helper.js");
        }

        setupWebView();
        applyImmersive();
        playSplashAnimation();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(START_URL);
        }
    }

    private void setupWebView() {
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debuggable);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);       // streams start without a tap
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); // many IPTV streams are http://
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " FreeChannelTV/1.0 (TV; FireOS; GoogleTV)");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(0xFF0B1220);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                pageFailed = false;
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    currentUrl = url;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!pageFailed) {
                    hideSplash();
                    errorView.setVisibility(View.GONE);
                    handler.removeCallbacks(autoRetry);
                    if (ENABLE_TV_HELPER && helperJs != null) {
                        view.evaluateJavascript(helperJs, null);
                    }
                }
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // On API 23+ this is only called for the main frame; on API 22 it is called for
                // every resource, so compare against the page URL.
                boolean mainFrame = failingUrl == null
                        || failingUrl.equals(currentUrl)
                        || failingUrl.equals(view.getUrl())
                        || failingUrl.equals(START_URL);
                if (mainFrame) {
                    showError();
                }
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Keep web pages inside the app; swallow links that would leave it (no browser on most TVs).
                return url.startsWith("intent:")
                        || url.startsWith("market:")
                        || url.startsWith("mailto:")
                        || url.startsWith("tel:")
                        || url.startsWith("sms:");
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                fullscreenContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
                applyImmersive();
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }

            @Override
            public Bitmap getDefaultVideoPoster() {
                // Avoids the grey default "play" poster flashing before video starts.
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }
        });
    }

    // ---- Animated splash -------------------------------------------------------------------

    private static final long SPLASH_MIN_MS = 2200;
    private long splashStart = 0;

    private void playSplashAnimation() {
        splashStart = System.currentTimeMillis();
        final View logo = findViewById(R.id.splash_logo);
        final View title = findViewById(R.id.splash_title);
        final View progress = findViewById(R.id.splash_progress);
        logo.setAlpha(0f); logo.setScaleX(0.3f); logo.setScaleY(0.3f); logo.setRotation(-20f);
        title.setAlpha(0f); title.setTranslationY(40f);
        progress.setAlpha(0f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f).setDuration(800)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f))
                .withEndAction(new Runnable() { @Override public void run() { pulse(logo); } })
                .start();
        title.animate().alpha(1f).translationY(0f).setStartDelay(450).setDuration(600).start();
        progress.animate().alpha(1f).setStartDelay(900).setDuration(400).start();
    }

    private void pulse(final View v) {
        if (loadingView.getVisibility() != View.VISIBLE) return;
        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(600).withEndAction(new Runnable() {
            @Override public void run() {
                v.animate().scaleX(1f).scaleY(1f).setDuration(600).withEndAction(new Runnable() {
                    @Override public void run() { pulse(v); }
                }).start();
            }
        }).start();
    }

    private void hideSplash() {
        if (loadingView.getVisibility() != View.VISIBLE) return;
        long wait = Math.max(0, SPLASH_MIN_MS - (System.currentTimeMillis() - splashStart));
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (pageFailed) return;
                loadingView.animate().alpha(0f).setDuration(400).withEndAction(new Runnable() {
                    @Override public void run() {
                        loadingView.setVisibility(View.GONE);
                        loadingView.setAlpha(1f);
                    }
                }).start();
            }
        }, wait);
    }

    private void hideCustomView() {
        if (customView == null) {
            return;
        }
        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        customView = null;
        webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        webView.requestFocus();
    }

    private void showError() {
        pageFailed = true;
        loadingView.setVisibility(View.GONE);
        errorText.setText(isOnline() ? R.string.error_generic : R.string.error_offline);
        webView.setVisibility(View.INVISIBLE);
        errorView.setVisibility(View.VISIBLE);
        retryButton.requestFocus();
        handler.removeCallbacks(autoRetry);
        handler.postDelayed(autoRetry, AUTO_RETRY_MS);
    }

    private void reload() {
        handler.removeCallbacks(autoRetry);
        pageFailed = false;
        errorView.setVisibility(View.GONE);
        loadingView.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);
        webView.requestFocus();
        webView.loadUrl(currentUrl != null ? currentUrl : START_URL);
    }

    @SuppressWarnings("deprecation")
    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return true;
        }
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    // ---- Remote control handling -------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    videoCommand("if(v.paused){v.play();}else{v.pause();}");
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    videoCommand("v.play();");
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    videoCommand("v.pause();");
                    return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    videoCommand("v.currentTime=v.currentTime+" + SEEK_SECONDS + ";");
                    return true;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    videoCommand("v.currentTime=Math.max(0,v.currentTime-" + SEEK_SECONDS + ");");
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    Toast.makeText(this, R.string.reloading, Toast.LENGTH_SHORT).show();
                    reload();
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (errorView.getVisibility() != View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPress < BACK_EXIT_WINDOW_MS) {
            super.onBackPressed();
        } else {
            lastBackPress = now;
            Toast.makeText(this, R.string.exit_hint, Toast.LENGTH_SHORT).show();
        }
    }

    private void videoCommand(String body) {
        runJs("(function(){var v=document.querySelector('video');if(!v)return;try{" + body + "}catch(e){}})();");
    }

    private void runJs(String js) {
        if (webView != null) {
            webView.evaluateJavascript(js, null);
        }
    }

    // ---- Lifecycle ---------------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        applyImmersive();
    }

    @Override
    protected void onPause() {
        // Stop playback when the user goes Home so audio doesn't keep playing in the background.
        runJs("document.querySelectorAll('video,audio').forEach(function(m){try{m.pause();}catch(e){}});");
        super.onPause();
    }

    @Override
    protected void onStop() {
        webView.onPause();
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersive();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void applyImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private String loadAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
