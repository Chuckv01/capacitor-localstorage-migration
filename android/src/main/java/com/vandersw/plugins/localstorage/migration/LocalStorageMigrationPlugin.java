package com.vandersw.plugins.localstorage.migration;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "LocalStorageMigration")
public class LocalStorageMigrationPlugin extends Plugin {
    private LocalStorageMigration implementation = new LocalStorageMigration();

    @PluginMethod
    public void migrateData(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("success", implementation.migrateData());
        call.resolve(ret);
    }
}