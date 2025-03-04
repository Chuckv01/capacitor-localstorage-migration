import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(LocalStorageMigrationPlugin)
public class LocalStorageMigrationPlugin: CAPPlugin {
    private let implementation = LocalStorageMigration()
    
    @objc func getLegacyData(_ call: CAPPluginCall) {
        let data = implementation.getLegacyData()
        call.resolve(data)
    }
}
