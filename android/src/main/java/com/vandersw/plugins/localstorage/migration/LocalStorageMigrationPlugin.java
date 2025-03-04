package com.vandersw.plugins.localstorage.migration;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.Iterator;

@CapacitorPlugin(name = "LocalStorageMigration")
public class LocalStorageMigrationPlugin extends Plugin {
    private LocalStorageMigration implementation;

    @Override
    public void load() {
        implementation = new LocalStorageMigration(getContext());
    }

    @PluginMethod
    public void getLegacyData(PluginCall call) {
        try {
            JSONObject jsonData = implementation.getLegacyData();
            JSObject jsData = new JSObject();
            
            // Convert JSONObject to JSObject
            Iterator<String> keys = jsonData.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                jsData.put(key, jsonData.getString(key));
            }
            
            call.resolve(jsData);
        } catch (Exception e) {
            call.reject("Error reading legacy data", e);
        }
    }
}