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
            readFromSQLite(legacyFile, data);
        } else {
            Log.d(TAG, "No legacy storage file found");
        }
        
        return data;
    }

    private File findLegacyLocalStorageFile() {
        // Direct path to Crosswalk localStorage file
        String legacyPath = context.getApplicationInfo().dataDir + 
                           "/app_xwalkcore/Default/Local Storage/file__0.localstorage";
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
                        // Convert UTF-16LE to regular string
                        String value = new String(valueBlob, "UTF-16LE").trim();
                        
                        // Remove any null terminators that might be present
                        value = value.replace("\u0000", "");
                        
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