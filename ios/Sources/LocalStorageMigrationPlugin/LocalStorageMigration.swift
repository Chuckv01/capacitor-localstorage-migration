import Foundation
import SQLite3

enum MigrationError: Error {
    case databaseNotFound
    case sqliteError(message: String)
    case dataDecodingError
    
    var localizedDescription: String {
        switch self {
        case .databaseNotFound:
            return "Legacy database file not found"
        case .sqliteError(let message):
            return "SQLite error: \(message)"
        case .dataDecodingError:
            return "Failed to decode legacy data"
        }
    }
}

@objc public class LocalStorageMigration: NSObject {
    private let TAG = "LocalStorageMigration"
    
    override init() {
        super.init()
    }
    
    @objc public func getLegacyData() throws -> [String: String] {
        guard let legacyPath = findLegacyLocalStorageFile() else {
            throw MigrationError.databaseNotFound
        }
        
        NSLog("%@ Found legacy storage at: %@", TAG, legacyPath)
        guard let data = readFromSQLite(path: legacyPath) else {
            throw MigrationError.dataDecodingError
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
        
        // Use autoreleasepool for better memory management with large data
        autoreleasepool {
            guard sqlite3_open(path, &db) == SQLITE_OK else {
                NSLog("%@ Failed to open database: %@", TAG, path)
                return
            }
            
            defer {
                sqlite3_close(db)
            }
            
            let queryString = "SELECT key, value FROM ItemTable"
            var statement: OpaquePointer?
            
            guard sqlite3_prepare_v2(db, queryString, -1, &statement, nil) == SQLITE_OK else {
                NSLog("%@ Failed to prepare statement", TAG)
                return
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
        }
        
        return resultData
    }
}