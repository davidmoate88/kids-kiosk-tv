package com.kidskiosk.tv;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String KIOSK_URL = "http://192.168.1.58/tv";

    // Never show the splash for less than this even if the page loads
    // faster (avoids an unpleasant flash), but never hold it longer than
    // necessary either — see the onPageFinished override below.
    private static final long SPLASH_MIN_HOLD_MS = 800;
    private static final long SPLASH_FADE_MS = 300;

    private WebView webView;
    private FrameLayout rootLayout;
    private SplashView splashView;
    private long splashShownAt;
    private boolean splashDismissed;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private VoiceSearchBridge voiceSearchBridge;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootLayout = new FrameLayout(this);
        setContentView(rootLayout);

        webView = new WebView(this);
        rootLayout.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        splashView = new SplashView(this);
        rootLayout.addView(splashView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splashShownAt = System.currentTimeMillis();

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setTextZoom(100);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                dismissSplash();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage msg) {
                android.util.Log.d("KidsKioskWeb", msg.message() + " [" + msg.sourceId() + ":" + msg.lineNumber() + "]");
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
                if (customView == null) return;
                webView.setVisibility(View.VISIBLE);
                rootLayout.removeView(customView);
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                customView = null;
                customViewCallback = null;
            }
        });

        voiceSearchBridge = new VoiceSearchBridge(this, webView);
        webView.addJavascriptInterface(voiceSearchBridge, "AndroidVoiceSearch");

        // Without explicit focus, D-pad key events have no focused view to
        // land on and are silently dropped before they ever reach the page.
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();

        webView.loadUrl(KIOSK_URL);
    }

    // Called from onPageFinished. Guarded by splashDismissed since
    // onPageFinished can fire more than once per real navigation (redirects,
    // sub-frame loads) — the splash should only ever run its hold+fade once.
    private void dismissSplash() {
        if (splashDismissed || splashView == null) return;
        splashDismissed = true;

        long elapsed = System.currentTimeMillis() - splashShownAt;
        long remaining = Math.max(0, SPLASH_MIN_HOLD_MS - elapsed);

        splashView.postDelayed(() -> {
            splashView.animate()
                    .alpha(0f)
                    .setDuration(SPLASH_FADE_MS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (splashView == null) return;
                            splashView.stopAnimating();
                            rootLayout.removeView(splashView);
                            splashView = null;
                        }
                    })
                    .start();
        }, remaining);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
            webView.requestFocus();
        }
    }

    // Hardware media keys (the dedicated play/pause button on a TV remote)
    // aren't reliably delivered to the page as DOM keyboard events through
    // WebView's normal key-event translation, so they're bridged directly
    // into a JS hook the web app registers while a video is playing.
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
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            voiceSearchBridge.onPermissionResult(granted);
        }
    }

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
    public void onBackPressed() {
        if (customView != null) {
            webView.getWebChromeClient().onHideCustomView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
