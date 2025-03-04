import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(LocalStorageMigrationPlugin)
public class LocalStorageMigrationPlugin: CAPPlugin {
    private let implementation = LocalStorageMigration()
    
    @objc func migrateData(_ call: CAPPluginCall) {
        // Implement iOS WebView localStorage migration
        let success = implementation.migrateData()
        call.resolve([
            "success": success
        ])
    }
}
