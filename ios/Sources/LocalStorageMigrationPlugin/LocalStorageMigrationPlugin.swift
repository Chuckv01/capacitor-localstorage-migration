import Foundation
import Capacitor

@objc(LocalStorageMigrationPlugin)
public class LocalStorageMigrationPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "LocalStorageMigrationPlugin"
    public let jsName = "LocalStorageMigration"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "getLegacyData", returnType: CAPPluginReturnPromise)
    ]
    
    private let implementation = LocalStorageMigration()
    
    @objc func getLegacyData(_ call: CAPPluginCall) {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else {
                call.reject("Plugin instance is gone")
                return
            }
            
            let data = self.implementation.getLegacyData()
            DispatchQueue.main.async {
                call.resolve(["data": data])
            }
        }
    }
}
