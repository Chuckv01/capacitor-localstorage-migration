package com.vandersw.plugins.localstorage.migration;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;

/**
 * LocalStorageMigration - Reads legacy localStorage data from Cordova/Crosswalk apps.
 * 
 * Supports two storage formats:
 * 1. Crosswalk SQLite format (app_xwalkcore/Default/Local Storage/file__0.localstorage)
 * 2. System WebView LevelDB format (app_webview/Default/Local Storage/leveldb/)
 * 
 * The LevelDB support is important for apps that used cordova-plugin-crosswalk-data-migration,
 * which moved data from Crosswalk to the system WebView location.
 */
public class LocalStorageMigration {
    private static final String TAG = "LocalStorageMigration";
    private static final String SQLITE_FILE = "file__0.localstorage";
    private static final String XWALK_PATH = "app_xwalkcore/Default/Local Storage";
    private static final String WEBVIEW_LEVELDB_PATH = "app_webview/Default/Local Storage/leveldb";
    private Context context;

    public LocalStorageMigration(Context context) {
        this.context = context;
    }

    public JSONObject getLegacyData() {
        JSONObject data = new JSONObject();
        
        String dataDir = context.getApplicationInfo().dataDir;
        Log.i(TAG, "App data directory: " + dataDir);
        
        // First try Crosswalk SQLite location
        File legacyFile = findLegacyLocalStorageFile();
        
        if (legacyFile != null && legacyFile.exists()) {
            Log.i(TAG, "Reading from Crosswalk SQLite storage");
            readFromSQLite(legacyFile, data);
        } else {
            Log.d(TAG, "No Crosswalk storage file found, checking system webview LevelDB...");
            
            // Try system webview LevelDB location (cordova-plugin-crosswalk-data-migration moves data here)
            File leveldbDir = new File(dataDir + "/" + WEBVIEW_LEVELDB_PATH);
            if (leveldbDir.exists() && leveldbDir.isDirectory()) {
                Log.i(TAG, "Found system webview LevelDB at: " + leveldbDir.getAbsolutePath());
                readFromLevelDB(data);
            } else {
                Log.d(TAG, "No LevelDB found at: " + leveldbDir.getAbsolutePath());
            }
        }
        
        return data;
    }

    private File findLegacyLocalStorageFile() {
        String legacyPath = context.getApplicationInfo().dataDir +
                           "/" + XWALK_PATH + "/" + SQLITE_FILE;
        File legacyFile = new File(legacyPath);
        
        if (legacyFile.exists()) {
            Log.d(TAG, "Found legacy storage at: " + legacyFile.getAbsolutePath());
            return legacyFile;
        }
        
        Log.d(TAG, "No legacy storage found at: " + legacyFile.getAbsolutePath());
        return null;
    }

    private void readFromSQLite(File dbFile, JSONObject data) {
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            
            cursor = db.query("ItemTable", 
                             new String[]{"key", "value"}, 
                             null, null, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int itemCount = 0;
                do {
                    String key = cursor.getString(0);
                    byte[] valueBlob = cursor.getBlob(1);
                    
                    if (key != null && valueBlob != null) {
                        String value = new String(valueBlob, "UTF-16LE").trim()
                                         .replace("\u0000", "");
                        
                        data.put(key, value);
                        itemCount++;
                        Log.d(TAG, String.format("Read item %d: %s (length: %d)", 
                              itemCount, key, valueBlob.length));
                    }
                } while (cursor.moveToNext());
                
                Log.i(TAG, String.format("Successfully read %d items from legacy storage", 
                       itemCount));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading from SQLite: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
    }
    
    /**
     * Read localStorage data from Chrome/WebView LevelDB format.
     * 
     * Uses a hidden WebView to read the data, which is the most reliable approach
     * because WebView knows how to read its own LevelDB localStorage format.
     * The WebView loads a file:// URL which gives it access to the legacy 
     * localStorage data stored under the file:// origin.
     */
    private void readFromLevelDB(JSONObject data) {
        Log.i(TAG, "Reading from LevelDB via WebView");
        
        try {
            LegacyWebViewReader reader = new LegacyWebViewReader(context);
            JSONObject webViewData = reader.readLegacyLocalStorage();
            
            if (webViewData.length() > 0) {
                Log.i(TAG, "WebView successfully read " + webViewData.length() + " localStorage entries");
                
                // Copy all entries from webViewData to data
                java.util.Iterator<String> keys = webViewData.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    try {
                        data.put(key, webViewData.get(key));
                    } catch (Exception e) {
                        Log.e(TAG, "Error copying key " + key + ": " + e.getMessage());
                    }
                }
            } else {
                Log.w(TAG, "WebView returned no localStorage data");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading from LevelDB via WebView: " + e.getMessage(), e);
        }
        
        Log.i(TAG, "LevelDB reading complete. Found " + data.length() + " keys");
    }
}
