package com.kidskiosk.tv;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.List;

/** Owns the native speech recognizer used by the origin-scoped WebView bridge. */
final class VoiceSearchBridge {

    static final int RECORD_AUDIO_REQUEST_CODE = 4201;

    private final Activity activity;
    private final WebView webView;
    private SpeechRecognizer recognizer;
    private boolean listening;
    private boolean destroyed;

    VoiceSearchBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    void startListening() {
        activity.runOnUiThread(this::startListeningOnUiThread);
    }

    private void startListeningOnUiThread() {
        if (destroyed || listening) return;
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    RECORD_AUDIO_REQUEST_CODE);
            return;
        }
        beginRecognition();
    }

    void onPermissionResult(boolean granted) {
        if (destroyed) return;
        if (granted) beginRecognition();
        else reportError("permission-denied");
    }

    private void beginRecognition() {
        if (destroyed || listening) return;
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            reportError("unavailable");
            return;
        }

        try {
            ensureRecognizer();
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            listening = true;
            recognizer.startListening(intent);
        } catch (RuntimeException error) {
            listening = false;
            reportError("unavailable");
        }
    }

    private void ensureRecognizer() {
        if (recognizer != null) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
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
                listening = false;
                reportError(error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ? "no-match"
                        : "error");
            }

            @Override
            public void onResults(Bundle results) {
                listening = false;
                List<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    reportResult(matches.get(0));
                } else {
                    reportError("no-match");
                }
            }
        });
    }

    private void reportResult(String text) {
        evaluateCallback("__tvVoiceSearchResult", text);
    }

    private void reportError(String reason) {
        evaluateCallback("__tvVoiceSearchError", reason);
    }

    private void evaluateCallback(String callbackName, String value) {
        if (destroyed) return;
        String quotedValue = JSONObject.quote(value);
        webView.evaluateJavascript(
                "window." + callbackName + " && window." + callbackName + "(" + quotedValue + ");",
                null);
    }

    void destroy() {
        if (destroyed) return;
        destroyed = true;
        listening = false;
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
    }
}
