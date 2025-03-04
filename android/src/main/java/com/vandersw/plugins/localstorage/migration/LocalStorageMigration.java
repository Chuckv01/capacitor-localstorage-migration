package com.vandersw.plugins.localstorage.migration;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;

public class LocalStorageMigration {
    private static final String TAG = "LocalStorageMigration";
    private static final String SQLITE_FILE = "file__0.localstorage";
    private static final String XWALK_PATH = "app_xwalkcore/Default/Local Storage";
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
}