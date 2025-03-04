import Foundation
import SQLite3

@objc public class LocalStorageMigration: NSObject {
    private let TAG = "LocalStorageMigration"
    
    override init() {
        super.init()
    }
    
    @objc public func getLegacyData() -> [String: String] {
        var data: [String: String] = [:]
        
        if let legacyPath = findLegacyLocalStorageFile() {
            NSLog("%@ Found legacy storage at: %@", TAG, legacyPath)
            data = readFromSQLite(path: legacyPath) ?? [:]
        } else {
            NSLog("%@ No legacy storage file found", TAG)
        }
        
        return data
    }
    
    private func findLegacyLocalStorageFile() -> String? {
        let fileManager = FileManager.default
        let libraryPath = NSSearchPathForDirectoriesInDomains(.libraryDirectory, .userDomainMask, true)[0]
        
        // Common WebKit localStorage paths
        let paths = [
            "\(libraryPath)/WebKit/LocalStorage/file__0.localstorage",
            "\(libraryPath)/Webkit/WebsiteData/LocalStorage/file__0.localstorage"
        ]
        
        for path in paths {
            if fileManager.fileExists(atPath: path) {
                return path
            }
        }
        
        return nil
    }
    
    private func readFromSQLite(path: String) -> [String: String]? {
        var db: OpaquePointer?
        var data: [String: String] = [:]
        
        if sqlite3_open(path, &db) == SQLITE_OK {
            let queryString = "SELECT key, value FROM ItemTable"
            var statement: OpaquePointer?
            
            if sqlite3_prepare_v2(db, queryString, -1, &statement, nil) == SQLITE_OK {
                var itemCount = 0
                
                while sqlite3_step(statement) == SQLITE_ROW {
                    if let key = sqlite3_column_text(statement, 0),
                       let valueData = sqlite3_column_blob(statement, 1),
                       let valueLength = sqlite3_column_bytes(statement, 1) {
                        
                        let keyString = String(cString: key)
                        let valueString = String(
                            bytes: Data(bytes: valueData, count: Int(valueLength)),
                            encoding: .utf16LittleEndian
                        )?.replacingOccurrences(of: "\0", with: "") ?? ""
                        
                        data[keyString] = valueString
                        itemCount += 1
                        
                        NSLog("%@ Read item %d: %@ (length: %d)", TAG, itemCount, keyString, valueLength)
                    }
                }
                
                NSLog("%@ Successfully read %d items from legacy storage", TAG, itemCount)
                sqlite3_finalize(statement)
            }
            
            sqlite3_close(db)
        }
        
        return data
    }
}