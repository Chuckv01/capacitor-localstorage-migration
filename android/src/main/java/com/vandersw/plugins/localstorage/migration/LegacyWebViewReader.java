package com.vandersw.plugins.localstorage.migration;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Uses a hidden WebView to read legacy localStorage data.
 * 
 * This is the most reliable way to read Chrome/WebView LevelDB localStorage
 * because we're using the same code that the WebView uses internally.
 * 
 * The trick is to create a WebView that loads a file:// URL, which gives it
 * access to the legacy localStorage data stored under the file:// origin.
 */
public class LegacyWebViewReader {
    private static final String TAG = "LegacyWebViewReader";
    private static final int TIMEOUT_SECONDS = 10;
    
    private final Context context;
    private final AtomicReference<JSONObject> resultRef = new AtomicReference<>();
    private final CountDownLatch latch = new CountDownLatch(1);
    
    public LegacyWebViewReader(Context context) {
        this.context = context;
    }
    
    /**
     * Read all localStorage data from the file:// origin.
     * This must be called from a background thread.
     */
    public JSONObject readLegacyLocalStorage() {
        Log.i(TAG, "Starting legacy localStorage read via WebView");
        
        // We need to run WebView operations on the main thread
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(this::createAndLoadWebView);
        
        try {
            // Wait for the WebView to finish reading
            boolean completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                Log.e(TAG, "Timeout waiting for WebView to read localStorage");
                return new JSONObject();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for WebView", e);
            return new JSONObject();
        }
        
        JSONObject result = resultRef.get();
        if (result == null) {
            result = new JSONObject();
        }
        
        Log.i(TAG, "Legacy localStorage read complete. Found " + result.length() + " keys");
        return result;
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void createAndLoadWebView() {
        try {
            WebView webView = new WebView(context);
            WebSettings settings = webView.getSettings();
            
            // Enable JavaScript and DOM storage
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            
            // Allow file:// access - this is key for reading legacy data
            settings.setAllowFileAccess(true);
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
            
            // Add JavaScript interface to receive the data
            webView.addJavascriptInterface(new LocalStorageBridge(), "LocalStorageBridge");
            
            // Set up client to inject our extraction script
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    Log.d(TAG, "WebView page finished loading: " + url);
                    
                    // Inject JavaScript to read all localStorage and send it back
                    String script = 
                        "(function() {" +
                        "  try {" +
                        "    var data = {};" +
                        "    for (var i = 0; i < localStorage.length; i++) {" +
                        "      var key = localStorage.key(i);" +
                        "      data[key] = localStorage.getItem(key);" +
                        "    }" +
                        "    LocalStorageBridge.onDataReceived(JSON.stringify(data));" +
                        "  } catch (e) {" +
                        "    LocalStorageBridge.onError(e.toString());" +
                        "  }" +
                        "})();";
                    
                    view.evaluateJavascript(script, null);
                }
            });
            
            // Load a minimal file:// URL to access the file:// origin's localStorage
            // We'll create a simple data URL that runs in the file:// context
            // Actually, we need a real file URL. Let's use the app's own cache dir.
            String html = "<!DOCTYPE html><html><head></head><body>Reading localStorage...</body></html>";
            
            // Save a temp file and load it via file:// URL
            java.io.File tempFile = new java.io.File(context.getCacheDir(), "migration_reader.html");
            java.io.FileWriter writer = new java.io.FileWriter(tempFile);
            writer.write(html);
            writer.close();
            
            String fileUrl = "file://" + tempFile.getAbsolutePath();
            Log.d(TAG, "Loading WebView with file URL: " + fileUrl);
            webView.loadUrl(fileUrl);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating WebView: " + e.getMessage(), e);
            resultRef.set(new JSONObject());
            latch.countDown();
        }
    }
    
    /**
     * JavaScript interface to receive localStorage data from the WebView
     */
    private class LocalStorageBridge {
        @JavascriptInterface
        public void onDataReceived(String jsonData) {
            Log.i(TAG, "Received localStorage data from WebView (length: " + jsonData.length() + ")");
            try {
                JSONObject data = new JSONObject(jsonData);
                Log.i(TAG, "Parsed " + data.length() + " localStorage entries");
                
                // Log the keys found
                java.util.Iterator<String> keys = data.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = data.optString(key, "");
                    Log.d(TAG, "Found key: " + key + " (value length: " + value.length() + ")");
                }
                
                resultRef.set(data);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing localStorage JSON: " + e.getMessage(), e);
                resultRef.set(new JSONObject());
            }
            latch.countDown();
        }
        
        @JavascriptInterface
        public void onError(String error) {
            Log.e(TAG, "JavaScript error reading localStorage: " + error);
            resultRef.set(new JSONObject());
            latch.countDown();
        }
    }
}
