import Foundation
import SQLite3

enum MigrationError: Error {
    case sqliteError(message: String)
    case dataDecodingError
    
    var localizedDescription: String {
        switch self {
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
        NSLog("%@ getLegacyData() called", TAG)
        
        NSLog("%@ Searching for legacy storage file...", TAG)
        guard let legacyPath = findLegacyLocalStorageFile() else {
            NSLog("%@ No legacy storage found, returning empty dictionary", TAG)
            return [:]
        }
        
        NSLog("%@ Found legacy storage at: %@", TAG, legacyPath)
        NSLog("%@ About to read from SQLite...", TAG)
        guard let data = readFromSQLite(path: legacyPath) else {
            NSLog("%@ readFromSQLite returned nil, throwing error", TAG)
            throw MigrationError.dataDecodingError
        }
        
        NSLog("%@ getLegacyData() completed with %d keys", TAG, data.count)
        return data
    }
    
    private func findLegacyLocalStorageFile() -> String? {
        NSLog("%@ findLegacyLocalStorageFile() called", TAG)
        let fileManager = FileManager.default
        let libraryPath = NSSearchPathForDirectoriesInDomains(.libraryDirectory, .userDomainMask, true)[0]
        NSLog("%@ Library path: %@", TAG, libraryPath)
        
        let paths = [
            "\(libraryPath)/WebKit/LocalStorage/file__0.localstorage",
            "\(libraryPath)/Webkit/WebsiteData/LocalStorage/file__0.localstorage"
        ]
        
        for path in paths {
            let exists = fileManager.fileExists(atPath: path)
            let readable = fileManager.isReadableFile(atPath: path)
            NSLog("%@ Checking path: %@ (exists: %@, readable: %@)", TAG, path, exists ? "YES" : "NO", readable ? "YES" : "NO")
            if exists {
                return path
            }
        }
        
        NSLog("%@ No legacy file found at any path", TAG)
        return nil
    }
    
    private func readFromSQLite(path: String) -> [String: String]? {
        NSLog("%@ readFromSQLite() called for: %@", TAG, path)
        var db: OpaquePointer?
        var resultData: [String: String] = [:]
        
        // Use autoreleasepool for better memory management with large data
        autoreleasepool {
            NSLog("%@ About to call sqlite3_open_v2 (READONLY | NOMUTEX)...", TAG)
            // Use SQLITE_OPEN_READONLY to avoid lock conflicts with WebKit
            guard sqlite3_open_v2(path, &db, SQLITE_OPEN_READONLY | SQLITE_OPEN_NOMUTEX, nil) == SQLITE_OK else {
                NSLog("%@ FAILED to open database: %@", TAG, path)
                return
            }
            NSLog("%@ sqlite3_open_v2 SUCCESS", TAG)
            
            defer {
                NSLog("%@ Closing SQLite database", TAG)
                sqlite3_close(db)
            }
            
            let queryString = "SELECT key, value FROM ItemTable"
            var statement: OpaquePointer?
            
            NSLog("%@ About to call sqlite3_prepare_v2...", TAG)
            guard sqlite3_prepare_v2(db, queryString, -1, &statement, nil) == SQLITE_OK else {
                NSLog("%@ Failed to prepare statement", TAG)
                return
            }
            NSLog("%@ sqlite3_prepare_v2 SUCCESS", TAG)
            
            defer {
                sqlite3_finalize(statement)
            }
            
            var itemCount = 0
            NSLog("%@ About to call sqlite3_step (first row)...", TAG)
            while sqlite3_step(statement) == SQLITE_ROW {
                NSLog("%@ sqlite3_step returned SQLITE_ROW", TAG)
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