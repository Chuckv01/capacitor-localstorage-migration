# Capacitor LocalStorage Migration Plugin

A Capacitor plugin to assist with retrieving legacy UIWebView/Crosswalk localStorage on Android and iOS.

After retrieving old localStorage data, all legacy localStorage database files are left in place.

If something goes wrong with the migration - we want another chance to get it right and not lose user data.

## Installation

```bash
npm install capacitor-localstorage-migration
npx cap sync
```

## Usage

```typescript
import { LocalStorageMigration } from 'capacitor-localstorage-migration';

async function migrateLocalStorage() {
  try {
    // Check if migration was already completed
    if (localStorage.getItem('migrationCompleted')) {
      console.log('Migration was already completed');
      return true;
    }

    const { data } = await LocalStorageMigration.getLegacyData();
    
    if (data && Object.keys(data).length > 0) {
      // Do something with the legacy data!
      console.log(data);
      // Mark migration as completed
      localStorage.setItem('migrationCompleted', 'true');
      return true;
    } else {
      // If no legacy data found, mark migration completed to avoid future runs
      localStorage.setItem('migrationCompleted', 'true');
      console.log('No legacy data found');
      return false;
    }
  } catch (err) {
    console.error('Error during localStorage migration:', err);
    return false;
  }
}
```

## API

<docgen-index>

* [`getLegacyData()`](#getlegacydata)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### getLegacyData()

```typescript
getLegacyData() => Promise<{ [key: string]: string; }>
```

**Returns:** <code>Promise&lt;{ [key: string]: string; }&gt;</code>

--------------------

</docgen-api>

## Supported Platforms

- Android
  - Retrieves data from Crosswalk WebView localStorage
  - Path: `/data/data/[package-name]/app_xwalkcore/Default/Local Storage/file__0.localstorage`
  - Handles UTF-16LE encoding
  
- iOS
  - Retrieves data from UIWebView localStorage
  - Path: `[Library]/WebKit/LocalStorage/file__0.localstorage`

## Error Handling

The plugin includes comprehensive error handling for common scenarios:

- Database not found
- SQLite reading errors
- Data decoding issues
- Memory constraints

Errors are returned as rejected promises with descriptive messages.

## Requirements

- Capacitor 5.0.0 or higher
- iOS 13.0 or higher
- Android API 21 or higher

## Development

### Building

```bash
npm run build
```

### Running Tests

```bash
npm test
```

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.