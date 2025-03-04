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
            if let sqliteData = readFromSQLite(path: legacyPath) {
                data = sqliteData
            }
        } else {
            NSLog("%@ No legacy storage file found", TAG)
        }
        
        return data
    }
    
    private func findLegacyLocalStorageFile() -> String? {
        let fileManager = FileManager.default
        let libraryPath = NSSearchPathForDirectoriesInDomains(.libraryDirectory, .userDomainMask, true)[0]
        
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
        var resultData: [String: String] = [:]
        
        guard sqlite3_open(path, &db) == SQLITE_OK else {
            NSLog("%@ Failed to open database: %@", TAG, path)
            return nil
        }
        
        defer {
            sqlite3_close(db)
        }
        
        let queryString = "SELECT key, value FROM ItemTable"
        var statement: OpaquePointer?
        
        guard sqlite3_prepare_v2(db, queryString, -1, &statement, nil) == SQLITE_OK else {
            NSLog("%@ Failed to prepare statement", TAG)
            return nil
        }
        
        defer {
            sqlite3_finalize(statement)
        }
        
        var itemCount = 0
        while sqlite3_step(statement) == SQLITE_ROW {
            if let keyData = sqlite3_column_text(statement, 0) {
                let keyString = String(cString: keyData)
                
                if let valueData = sqlite3_column_blob(statement, 1) {
                    let valueLength = Int(sqlite3_column_bytes(statement, 1))
                    let data = Data(bytes: valueData, count: valueLength)
                    
                    if let valueString = String(
                        data: data,
                        encoding: .utf16LittleEndian
                    )?.replacingOccurrences(of: "\0", with: "") {
                        resultData[keyString] = valueString
                        itemCount += 1
                        NSLog("%@ Read item %d: %@ (length: %d)", TAG, itemCount, keyString, valueLength)
                    }
                }
            }
        }
        
        NSLog("%@ Successfully read %d items from legacy storage", TAG, itemCount)
        return resultData
    }
}