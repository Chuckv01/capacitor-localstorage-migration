package com.vandersw.plugins.localstorage.migration;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.nio.charset.StandardCharsets;

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
    private String[] keysToFind = null;

    public LocalStorageMigration(Context context) {
        this.context = context;
    }

    /**
     * Set specific keys to look for in LevelDB storage.
     * If not set, all keys found will be returned for SQLite,
     * but LevelDB requires specific keys due to its binary format.
     * 
     * @param keys Array of localStorage key names to search for
     */
    public void setKeysToFind(String[] keys) {
        this.keysToFind = keys;
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
                readFromLevelDB(leveldbDir, data);
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
     * LevelDB log files contain key-value pairs in a specific binary format.
     * Chrome prefixes localStorage keys with "_file://\u0000\u0001" or similar origin prefixes.
     */
    private void readFromLevelDB(File leveldbDir, JSONObject data) {
        Log.i(TAG, "Attempting to read from LevelDB directory: " + leveldbDir.getAbsolutePath());
        
        File[] files = leveldbDir.listFiles();
        if (files == null) {
            Log.e(TAG, "Cannot list files in LevelDB directory");
            return;
        }
        
        // Look for .log files which contain the actual data
        for (File file : files) {
            if (file.getName().endsWith(".log")) {
                Log.i(TAG, "Processing LevelDB log file: " + file.getName() + " (" + file.length() + " bytes)");
                readLevelDBLogFile(file, data);
            }
        }
        
        Log.i(TAG, "LevelDB reading complete. Found " + data.length() + " keys");
    }
    
    /**
     * Parse a LevelDB log file to extract localStorage key-value pairs.
     * The format is complex, but localStorage entries typically appear as:
     * - A prefix (origin info like "_file://\0\1" or "_https://localhost\0\1")
     * - The key name
     * - The value (often JSON or simple values)
     */
    private void readLevelDBLogFile(File logFile, JSONObject data) {
        try {
            byte[] bytes = readFileBytes(logFile);
            Log.d(TAG, "Read " + bytes.length + " bytes from log file");
            
            if (this.keysToFind != null) {
                // If specific keys are configured, search for those
                for (String key : this.keysToFind) {
                    String value = extractValueForKey(bytes, key);
                    if (value != null && !value.isEmpty()) {
                        try {
                            data.put(key, value);
                            Log.i(TAG, "Found key '" + key + "' with value length: " + value.length());
                        } catch (Exception e) {
                            Log.e(TAG, "Error putting key " + key + ": " + e.getMessage());
                        }
                    }
                }
            } else {
                // Auto-discover keys by scanning for the origin prefix pattern
                discoverAndExtractKeys(bytes, data);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading LevelDB log file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Scan the LevelDB bytes to discover and extract all localStorage keys.
     * Looks for the pattern "_file://\0\1" which precedes each key name.
     * 
     * IMPORTANT: We ONLY look for _file:// origin keys, which indicates legacy Cordova data.
     * We deliberately ignore _https://localhost keys to avoid accidentally reading
     * data from the current Capacitor app on fresh installs.
     */
    private void discoverAndExtractKeys(byte[] bytes, JSONObject data) {
        // Only look for file:// origin - this is the ONLY reliable indicator of legacy Cordova data
        // New Capacitor apps use https://localhost, so we must NOT look for that prefix
        // to avoid reading the current app's data on fresh installs
        String legacyPrefix = "_file://\u0000\u0001";
        byte[] prefixBytes = legacyPrefix.getBytes(StandardCharsets.UTF_8);
        
        // Find all occurrences of the legacy prefix
        int searchStart = 0;
        while (searchStart < bytes.length) {
            int prefixIndex = findPatternFrom(bytes, prefixBytes, searchStart);
            if (prefixIndex < 0) break;
            
            // Extract the key name that follows the prefix
            int keyStart = prefixIndex + prefixBytes.length;
            String key = extractKeyName(bytes, keyStart);
            
            if (key != null && !key.isEmpty() && !key.startsWith("META:") && !key.equals("__sak")) {
                // Only process if we haven't already got this key (we want the last occurrence)
                // But we search forward, so we'll overwrite with later values
                String value = extractValueForKey(bytes, key);
                if (value != null && !value.isEmpty()) {
                    try {
                        data.put(key, value);
                        Log.i(TAG, "Discovered key '" + key + "' with value length: " + value.length());
                    } catch (Exception e) {
                        Log.e(TAG, "Error putting key " + key + ": " + e.getMessage());
                    }
                }
            }
            
            searchStart = prefixIndex + 1;
        }
    }
    
    /**
     * Extract a key name from the bytes starting at the given offset.
     * Key names are ASCII strings terminated by a non-printable character or known delimiter.
     */
    private String extractKeyName(byte[] bytes, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < Math.min(start + 256, bytes.length); i++) {
            byte b = bytes[i];
            // Key names are printable ASCII (excluding control characters)
            if (b >= 32 && b < 127) {
                sb.append((char) b);
            } else {
                break;
            }
        }
        return sb.toString();
    }
    
    /**
     * Find a pattern starting from a specific offset.
     */
    private int findPatternFrom(byte[] data, byte[] pattern, int startFrom) {
        outer:
        for (int i = startFrom; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
    
    private byte[] readFileBytes(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        byte[] bytes = new byte[(int) file.length()];
        bis.read(bytes);
        bis.close();
        fis.close();
        return bytes;
    }
    
    /**
     * Extract the value for a given localStorage key from the raw LevelDB bytes.
     * Chrome stores localStorage with a prefix like "_file://\0\1keyname"
     * followed by the value.
     * 
     * IMPORTANT: We ONLY look for _file:// origin keys to ensure we're reading
     * legacy Cordova data, not data from the current Capacitor app.
     */
    private String extractValueForKey(byte[] bytes, String key) {
        // Only use file:// origin prefix - this ensures we only read legacy Cordova data
        String legacyPrefix = "_file://\u0000\u0001";
        String searchPattern = legacyPrefix + key;
        int keyIndex = findPattern(bytes, searchPattern.getBytes(StandardCharsets.UTF_8));
        
        if (keyIndex >= 0) {
            Log.d(TAG, "Found key '" + key + "' at byte offset " + keyIndex + " with prefix '_file://\\0\\1'");
            String value = extractValueAfterKey(bytes, keyIndex + searchPattern.length());
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        
        Log.d(TAG, "Key '" + key + "' not found in LevelDB data with _file:// origin");
        return null;
    }
    
    /**
     * Find the LAST occurrence of a pattern in the data.
     * LevelDB is append-only, so the most recent value for a key is the last one.
     * This is critical for correct data retrieval when values have been updated.
     */
    private int findPattern(byte[] data, byte[] pattern) {
        int lastFound = -1;
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            lastFound = i; // Keep searching for later occurrences
        }
        return lastFound;
    }
    
    /**
     * Extract the value that follows a key in LevelDB format.
     * Handles both UTF-8 JSON values and UTF-16LE encoded strings.
     */
    private String extractValueAfterKey(byte[] bytes, int startOffset) {
        try {
            // Skip any null bytes or separators after the key
            int offset = startOffset;
            while (offset < bytes.length && (bytes[offset] == 0 || bytes[offset] == 1)) {
                offset++;
            }
            
            // Try to find the start of JSON data or simple value
            // Look for '[' (array), '{' (object), or digit which indicates value start
            int jsonStart = -1;
            for (int i = offset; i < Math.min(offset + 100, bytes.length); i++) {
                byte b = bytes[i];
                if (b == '[' || b == '{' || (b >= '0' && b <= '9')) {
                    jsonStart = i;
                    break;
                }
            }
            
            if (jsonStart < 0) {
                // Try UTF-16LE encoding (Chrome sometimes stores values this way)
                return tryExtractUTF16LE(bytes, offset);
            }
            
            // Find the end of the JSON/value
            int depth = 0;
            int valueEnd = jsonStart;
            boolean inString = false;
            boolean isSimpleValue = (bytes[jsonStart] >= '0' && bytes[jsonStart] <= '9');
            
            if (isSimpleValue) {
                // Simple numeric value - read until non-digit
                while (valueEnd < bytes.length) {
                    byte b = bytes[valueEnd];
                    if (b >= '0' && b <= '9') {
                        valueEnd++;
                    } else {
                        break;
                    }
                }
            } else {
                // JSON array or object - track depth to find matching bracket
                for (int i = jsonStart; i < bytes.length; i++) {
                    byte b = bytes[i];
                    
                    if (b == '"' && (i == 0 || bytes[i-1] != '\\')) {
                        inString = !inString;
                    }
                    
                    if (!inString) {
                        if (b == '[' || b == '{') depth++;
                        if (b == ']' || b == '}') depth--;
                        
                        if (depth == 0 && (b == ']' || b == '}')) {
                            valueEnd = i + 1;
                            break;
                        }
                    }
                    
                    // Safety limit to prevent runaway parsing
                    if (i - jsonStart > 500000) {
                        Log.w(TAG, "Value too large, truncating");
                        break;
                    }
                }
            }
            
            if (valueEnd > jsonStart) {
                String value = new String(bytes, jsonStart, valueEnd - jsonStart, StandardCharsets.UTF_8);
                Log.d(TAG, "Extracted value (first 100 chars): " + value.substring(0, Math.min(100, value.length())));
                return value;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting value: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Try to extract UTF-16LE encoded string (Chrome sometimes stores values this way)
     */
    private String tryExtractUTF16LE(byte[] bytes, int offset) {
        try {
            // Look for JSON start in UTF-16LE format (character followed by 0x00)
            for (int i = offset; i < Math.min(offset + 200, bytes.length - 1); i += 2) {
                if ((bytes[i] == '[' || bytes[i] == '{') && bytes[i + 1] == 0) {
                    // Found potential UTF-16LE JSON start
                    int jsonStart = i;
                    int depth = 0;
                    int valueEnd = jsonStart;
                    
                    for (int j = jsonStart; j < bytes.length - 1; j += 2) {
                        byte b = bytes[j];
                        byte high = bytes[j + 1];
                        
                        if (high != 0) continue; // Not ASCII in UTF-16LE
                        
                        if (b == '[' || b == '{') depth++;
                        if (b == ']' || b == '}') depth--;
                        
                        if (depth == 0 && (b == ']' || b == '}')) {
                            valueEnd = j + 2;
                            break;
                        }
                        
                        if (j - jsonStart > 1000000) break; // Safety limit
                    }
                    
                    if (valueEnd > jsonStart) {
                        byte[] valueBytes = new byte[valueEnd - jsonStart];
                        System.arraycopy(bytes, jsonStart, valueBytes, 0, valueEnd - jsonStart);
                        String value = new String(valueBytes, StandardCharsets.UTF_16LE);
                        Log.d(TAG, "Extracted UTF-16LE value (first 100 chars): " + value.substring(0, Math.min(100, value.length())));
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting UTF-16LE value: " + e.getMessage());
        }
        return null;
    }
}