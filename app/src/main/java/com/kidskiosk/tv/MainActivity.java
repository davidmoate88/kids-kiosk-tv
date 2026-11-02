package com.kidskiosk.tv;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Collections;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "KidsKioskTV";
    private static final String KIOSK_URL = BuildConfig.KIOSK_URL;
    // Set by the Tablet Control launcher on the kids' tablets ("george",
    // "arthur"), so kids-kiosk keeps that child's history and suggestions.
    // Remembered, so later launches from elsewhere still say who it is.
    // The living-room box never sets it and gets plain /tv.
    static final String VIEWER_EXTRA = "com.kidskiosk.tv.VIEWER";
    private static final String PREFS = "kids_tv";
    private static final String PREF_VIEWER = "viewer";
    private static final String VOICE_MESSAGE_OBJECT = "AndroidVoiceSearchNative";
    private static final long SPLASH_MIN_HOLD_MS = 800;
    private static final long SPLASH_FADE_MS = 300;
    private static final long RETRY_DELAY_MS = 5_000;

    private WebView webView;
    private FrameLayout rootLayout;
    private SplashView splashView;
    private KioskErrorView errorView;
    private KioskUrlPolicy urlPolicy;
    private long splashShownAt;
    private boolean splashDismissed;
    private boolean mainFrameFailed;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private VoiceSearchBridge voiceSearchBridge;

    private final Runnable retryRunnable = this::retryKiosk;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        urlPolicy = new KioskUrlPolicy(KIOSK_URL);
        rootLayout = new FrameLayout(this);
        setContentView(rootLayout);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (customView != null) {
                    hideCustomView();
                } else if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        webView = new WebView(this);
        rootLayout.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        showSplash();

        // Without this, the embedded YouTube player (served from the
        // youtube-nocookie.com origin, cross-origin to the kiosk's own)
        // can never persist any cookie — third-party cookies default to
        // blocked per-WebView unless explicitly enabled. A client that never
        // accumulates any YouTube-side cookie continuity looks like a fresh,
        // automated visitor on every single load, which is exactly what
        // trips YouTube's embedded sign-in/"confirm you're not a bot" wall
        // far more readily than a normal browser tab ever does.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(100);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame() || urlPolicy.isAllowed(request.getUrl().toString())) {
                    return false;
                }
                Log.w(TAG, "Blocked navigation outside kiosk origin: " + request.getUrl());
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (urlPolicy.isAllowed(url)) {
                    mainFrameFailed = false;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (urlPolicy.isAllowed(url) && !mainFrameFailed) {
                    onKioskLoaded();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    showLoadError(getString(R.string.load_error_detail));
                }
            }

            @Override
            public void onReceivedHttpError(
                    WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (request.isForMainFrame()) {
                    showLoadError(getString(
                            R.string.http_error_detail, errorResponse.getStatusCode()));
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage msg) {
                Log.d("KidsKioskWeb", msg.message() + " [" + msg.sourceId() + ":" + msg.lineNumber() + "]");
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                rootLayout.addView(customView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });

        voiceSearchBridge = new VoiceSearchBridge(this, webView);
        installOriginScopedVoiceBridge();

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();
        webView.loadUrl(startUrl());
    }

    /** Saves a valid viewer from the launching intent; true if it changed. */
    private boolean rememberViewer(Intent intent) {
        String viewer = intent == null ? null : intent.getStringExtra(VIEWER_EXTRA);
        if (viewer == null || !viewer.matches("[a-z]{1,20}")) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (viewer.equals(prefs.getString(PREF_VIEWER, null))) return false;
        prefs.edit().putString(PREF_VIEWER, viewer).apply();
        return true;
    }

    private String startUrl() {
        rememberViewer(getIntent());
        String viewer = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_VIEWER, null);
        if (viewer == null) return KIOSK_URL;
        return Uri.parse(KIOSK_URL).buildUpon().appendQueryParameter("viewer", viewer).build().toString();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (rememberViewer(intent) && webView != null) webView.loadUrl(startUrl());
    }

    // Both feature guards execute before the annotated AndroidX calls. Lint
    // cannot currently follow the two independent WebView feature checks.
    @SuppressLint("RequiresFeature")
    private void installOriginScopedVoiceBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Log.w(TAG, "Voice search disabled: installed WebView is missing secure messaging support");
            return;
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Log.w(TAG, "Voice search disabled: installed WebView is missing secure messaging support");
            return;
        }

        Set<String> allowedOrigins = Collections.singleton(urlPolicy.originRule());
        WebViewCompat.addWebMessageListener(
                webView,
                VOICE_MESSAGE_OBJECT,
                allowedOrigins,
                (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                    if (!isMainFrame || message.getType() != WebMessageCompat.TYPE_STRING) {
                        return;
                    }
                    if ("startListening".equals(message.getData())) {
                        voiceSearchBridge.startListening();
                    } else {
                        Log.w(TAG, "Ignored unknown voice bridge message");
                    }
                });

        WebViewCompat.addDocumentStartJavaScript(
                webView,
                "window.AndroidVoiceSearch=Object.freeze({startListening:function(){"
                        + "window." + VOICE_MESSAGE_OBJECT + ".postMessage('startListening');}});",
                allowedOrigins);
    }

    private void showSplash() {
        removeErrorView();
        if (splashView != null) {
            splashView.stopAnimating();
            rootLayout.removeView(splashView);
        }
        splashDismissed = false;
        splashView = new SplashView(this);
        rootLayout.addView(splashView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splashShownAt = SystemClock.elapsedRealtime();
    }

    private void dismissSplash() {
        if (splashDismissed || splashView == null) return;
        splashDismissed = true;

        long elapsed = SystemClock.elapsedRealtime() - splashShownAt;
        long remaining = Math.max(0, SPLASH_MIN_HOLD_MS - elapsed);
        SplashView viewToDismiss = splashView;

        viewToDismiss.postDelayed(() -> {
            if (splashView != viewToDismiss) return;
            viewToDismiss.animate()
                    .alpha(0f)
                    .setDuration(SPLASH_FADE_MS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (splashView != viewToDismiss) return;
                            viewToDismiss.stopAnimating();
                            rootLayout.removeView(viewToDismiss);
                            splashView = null;
                        }
                    })
                    .start();
        }, remaining);
    }

    private void onKioskLoaded() {
        rootLayout.removeCallbacks(retryRunnable);
        removeErrorView();
        webView.setVisibility(View.VISIBLE);
        dismissSplash();
        webView.requestFocus();
    }

    private void showLoadError(String detail) {
        mainFrameFailed = true;
        webView.setVisibility(View.GONE);
        removeSplashImmediately();
        removeErrorView();
        errorView = new KioskErrorView(this, detail, this::retryKiosk);
        rootLayout.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.removeCallbacks(retryRunnable);
        rootLayout.postDelayed(retryRunnable, RETRY_DELAY_MS);
    }

    private void retryKiosk() {
        if (isFinishing() || isDestroyed()) return;
        rootLayout.removeCallbacks(retryRunnable);
        mainFrameFailed = false;
        webView.setVisibility(View.VISIBLE);
        showSplash();
        webView.loadUrl(startUrl());
    }

    private void removeSplashImmediately() {
        if (splashView == null) return;
        splashView.animate().cancel();
        splashView.stopAnimating();
        rootLayout.removeView(splashView);
        splashView = null;
        splashDismissed = true;
    }

    private void removeErrorView() {
        if (errorView == null) return;
        rootLayout.removeView(errorView);
        errorView = null;
    }

    private void hideCustomView() {
        if (customView == null) return;
        View viewToRemove = customView;
        WebChromeClient.CustomViewCallback callback = customViewCallback;
        customView = null;
        customViewCallback = null;
        webView.setVisibility(View.VISIBLE);
        rootLayout.removeView(viewToRemove);
        if (callback != null) callback.onCustomViewHidden();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
            webView.requestFocus();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    webView.evaluateJavascript(
                            "window.__tvRemotePlayPause && window.__tvRemotePlayPause();", null);
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == VoiceSearchBridge.RECORD_AUDIO_REQUEST_CODE) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            voiceSearchBridge.onPermissionResult(granted);
        }
    }

    @SuppressWarnings("deprecation")
    private void enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onDestroy() {
        rootLayout.removeCallbacks(retryRunnable);
        hideCustomView();
        removeSplashImmediately();
        if (voiceSearchBridge != null) {
            voiceSearchBridge.destroy();
        }
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            rootLayout.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
