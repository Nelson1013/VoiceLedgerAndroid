package org.vledger.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final int REQ_MIC = 1;

    private WebView webView;
    private SpeechRecognizer recognizer;
    private boolean listening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new VoiceBridge(), "AndroidVoice");
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class VoiceBridge {
        @JavascriptInterface public void startListening() { runOnUiThread(() -> startVoice()); }
        @JavascriptInterface public void stopListening()  { runOnUiThread(() -> stopVoice()); }
        @JavascriptInterface public boolean hasMicPermission() {
            return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ Manifest.permission.RECORD_AUDIO }, REQ_MIC);
            return;
        }
        if (listening) return;
        if (recognizer == null) buildRecognizer();

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-NG");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        listening = true;
        recognizer.startListening(intent);
    }

    private void stopVoice() {
        if (recognizer != null && listening) recognizer.stopListening();
    }

    private void buildRecognizer() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { jsState("listening"); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { jsState("processing"); }

            @Override public void onError(int error) {
                listening = false;
                String msg;
                switch (error) {
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        msg = "Mic permission denied — turn am on for Voice Ledger, or type below."; break;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        msg = "I no hear wetin you talk — try again."; break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        msg = "I no hear anything — try again."; break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        msg = "Voice needs internet — check your data, or type below."; break;
                    default:
                        msg = "Voice no work just now — use the type box below."; break;
                }
                jsError(msg);
            }

            @Override public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = (matches != null && !matches.isEmpty()) ? matches.get(0) : "";
                if (text.isEmpty()) jsError("I no hear wetin you talk — try again.");
                else jsResult(text);
            }

            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    private void jsState(String state) { evalJs("onNativeSpeechState(" + q(state) + ")"); }
    private void jsResult(String text) { evalJs("onNativeSpeechResult(" + q(text) + ")"); }
    private void jsError(String msg)   { evalJs("onNativeSpeechError(" + q(msg) + ")"); }
    private String q(String s) { try { return JSONObject.quote(s); } catch (Exception e) { return "\"\""; } }
    private void evalJs(String js) { runOnUiThread(() -> webView.evaluateJavascript(js, null)); }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQ_MIC) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoice();
        } else {
            jsError("Mic permission denied — turn am on for Voice Ledger, or type below.");
        }
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
