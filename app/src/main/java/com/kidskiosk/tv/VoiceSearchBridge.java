package com.kidskiosk.tv;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

/**
 * JS-callable bridge exposed on the page as window.AndroidVoiceSearch.
 * TvSearch.tsx calls startListening() from its mic button; results/errors
 * are delivered back via window.__tvVoiceSearchResult/__tvVoiceSearchError,
 * the same evaluateJavascript convention MainActivity's own
 * __tvRemotePlayPause bridge already uses for the native-to-JS direction.
 */
public class VoiceSearchBridge {

    public static final int RECORD_AUDIO_REQUEST_CODE = 4201;

    private final Activity activity;
    private final WebView webView;
    private SpeechRecognizer recognizer;

    public VoiceSearchBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public void startListening() {
        // @JavascriptInterface methods run on a WebView-internal thread, not
        // the main thread, but SpeechRecognizer and the permission request
        // both require the main thread — hop back onto it first.
        activity.runOnUiThread(this::startListeningOnUiThread);
    }

    private void startListeningOnUiThread() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_REQUEST_CODE);
            return;
        }
        beginRecognition();
    }

    // Called from MainActivity.onRequestPermissionsResult once the grown-up
    // responds to the system dialog. A denial here is a dead end for this
    // attempt — Android won't re-prompt without a settings-page detour — so
    // it reports the same reason the pre-check above would have.
    void onPermissionResult(boolean granted) {
        if (granted) beginRecognition();
        else reportError("permission-denied");
    }

    private void beginRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            reportError("unavailable");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
        }
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onError(int error) {
                reportError(error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ? "no-match" : "error");
            }

            @Override
            public void onResults(Bundle results) {
                List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    reportResult(matches.get(0));
                } else {
                    reportError("no-match");
                }
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizer.startListening(intent);
    }

    private void reportResult(String text) {
        webView.evaluateJavascript(
                "window.__tvVoiceSearchResult && window.__tvVoiceSearchResult(\"" + jsEscape(text) + "\");", null);
    }

    private void reportError(String reason) {
        webView.evaluateJavascript(
                "window.__tvVoiceSearchError && window.__tvVoiceSearchError(\"" + jsEscape(reason) + "\");", null);
    }

    // Recognized speech can itself contain a quote or apostrophe (a kid's
    // show title like "Blue's Clues" is exactly the kind of input this needs
    // to survive intact) — escape it before splicing into an eval'd string
    // literal rather than assuming it's always injection-safe plain text.
    private static String jsEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
