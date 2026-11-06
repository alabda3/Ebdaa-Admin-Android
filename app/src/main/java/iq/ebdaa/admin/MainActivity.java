package iq.ebdaa.admin;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

public final class MainActivity extends ComponentActivity {
    private WebView page;
    private ValueCallback<Uri[]> uploadCallback;
    private String panelUrl;
    private String trustedHost;

    private final ActivityResultLauncher<String[]> uploadPicker = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> {
            if (uploadCallback != null) {
                uploadCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
                uploadCallback = null;
            }
        }
    );
    private final ActivityResultLauncher<String> noticePermission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> {
            if (!granted) Toast.makeText(this, "فعّل الإشعارات من إعدادات التطبيق حتى توصلك الطلبات", Toast.LENGTH_LONG).show();
        }
    );

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        panelUrl = getString(R.string.panel_url).trim();
        Uri parsed = Uri.parse(panelUrl);
        trustedHost = parsed.getHost();
        if (!"https".equals(parsed.getScheme()) || trustedHost == null) {
            Toast.makeText(this, "رابط لوحة التحكم يجب أن يبدأ بـ https://", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        setupChannel();
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            noticePermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        page = new WebView(this);
        setContentView(page);
        WebSettings s = page.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        page.setWebContentsDebuggingEnabled(false);
        page.addJavascriptInterface(new PushBridge(), "EbdaaAndroid");
        page.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                String[] types = params.getAcceptTypes();
                uploadPicker.launch(types == null || types.length == 0 || types[0].isEmpty() ? new String[]{"image/*"} : types);
                return true;
            }
        });
        page.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (isTrusted(url)) return false;
                if (request.isForMainFrame()) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, url)); }
                    catch (Exception ignored) { }
                }
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (isTrusted(Uri.parse(url))) emitTokenReady();
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (page.canGoBack()) page.goBack();
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });
        page.loadUrl(panelUrl);
        initFirebase();
    }

    private boolean isTrusted(Uri uri) {
        return "https".equals(uri.getScheme()) && trustedHost.equalsIgnoreCase(uri.getHost());
    }

    public final class PushBridge {
        @JavascriptInterface public String getPushToken() {
            return getSharedPreferences(EbdaaMessagingService.PREFS, MODE_PRIVATE)
                .getString(EbdaaMessagingService.TOKEN_KEY, "");
        }
    }

    private void emitTokenReady() {
        if (page != null && isTrusted(Uri.parse(page.getUrl()))) {
            page.evaluateJavascript("window.dispatchEvent(new Event('ebdaa-token-ready'));", null);
        }
    }

    private void setupChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(EbdaaMessagingService.CHANNEL_ID,
                "طلبات المتجر", NotificationManager.IMPORTANCE_HIGH);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private void initFirebase() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            Toast.makeText(this, "Firebase غير مهيأ: افحص google-services.json", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    getSharedPreferences(EbdaaMessagingService.PREFS, MODE_PRIVATE)
                        .edit().putString(EbdaaMessagingService.TOKEN_KEY, task.getResult()).apply();
                    emitTokenReady();
                }
            });
        } catch (Exception err) {
            Toast.makeText(this, "راجع إعدادات Firebase: " + err.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy() {
        if (uploadCallback != null) { uploadCallback.onReceiveValue(null); uploadCallback = null; }
        if (page != null) { page.destroy(); page = null; }
        super.onDestroy();
    }
}
