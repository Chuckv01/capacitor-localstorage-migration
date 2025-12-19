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
    
    private let TAG = "LocalStorageMigrationPlugin"
    
    @objc func getLegacyData(_ call: CAPPluginCall) {
        NSLog("%@ getLegacyData() plugin method called", TAG)
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            NSLog("%@ Executing on background thread", self?.TAG ?? "LocalStorageMigrationPlugin")
            guard let self = self else {
                NSLog("LocalStorageMigrationPlugin Plugin instance is gone")
                call.reject("Plugin instance is gone")
                return
            }
            
            do {
                NSLog("%@ Calling implementation.getLegacyData()...", self.TAG)
                let startTime = CFAbsoluteTimeGetCurrent()
                let data = try self.implementation.getLegacyData()
                let elapsed = CFAbsoluteTimeGetCurrent() - startTime
                NSLog("%@ implementation.getLegacyData() completed in %.3f seconds with %d keys", self.TAG, elapsed, data.count)
                DispatchQueue.main.async {
                    NSLog("%@ Resolving on main thread", self.TAG)
                    call.resolve(data)
                }
            } catch let error as MigrationError {
                NSLog("%@ MigrationError: %@", self.TAG, error.localizedDescription)
                DispatchQueue.main.async {
                    call.reject(error.localizedDescription)
                }
            } catch {
                NSLog("%@ Unexpected error: %@", self.TAG, error.localizedDescription)
                DispatchQueue.main.async {
                    call.reject("Unexpected error during migration")
                }
            }
        }
    }
}
