package com.vandersw.plugins.localstorage.migration;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;

public class LocalStorageMigration {
    private static final String TAG = "LocalStorageMigration";
    private Context context;

    public LocalStorageMigration(Context context) {
        this.context = context;
    }

    public JSONObject getLegacyData() {
        JSONObject data = new JSONObject();
        File legacyFile = findLegacyLocalStorageFile();
        
        if (legacyFile != null && legacyFile.exists()) {
            Log.d(TAG, "Found legacy storage at: " + legacyFile.getAbsolutePath());
            readFromSQLite(legacyFile, data);
        } else {
            Log.d(TAG, "No legacy storage file found");
        }
        
        return data;
    }

    private File findLegacyLocalStorageFile() {
        // Check common locations for legacy localStorage SQLite files
        File[] locations = {
            context.getFilesDir(),
            context.getExternalFilesDir(null)
        };

        for (File location : locations) {
            // First try Crosswalk path
            File xwalkFile = new File(location, "app_xwalkcore/Default/Local Storage/file__0.localstorage");
            if (xwalkFile.exists()) {
                return xwalkFile;
            }
            
            // Then try WebView path
            File webviewFile = new File(location, "app_webview/Local Storage/file__0.localstorage");
            if (webviewFile.exists()) {
                return webviewFile;
            }
        }

        return null;
    }

    private void readFromSQLite(File dbFile, JSONObject data) {
        SQLiteDatabase db = null;

        try {
            db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            
            Cursor cursor = db.query("ItemTable", 
                                   new String[]{"key", "value"}, 
                                   null, null, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String key = cursor.getString(0);
                    byte[] valueBlob = cursor.getBlob(1);
                    
                    if (key != null && valueBlob != null) {
                        String value = new String(valueBlob, "UTF-8");
                        data.put(key, value);
                        Log.d(TAG, "Read item: " + key + " (length: " + valueBlob.length + ")");
                    }
                } while (cursor.moveToNext());
                
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading from SQLite", e);
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }
}