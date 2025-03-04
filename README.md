# capacitor-localstorage-migration

Migration of legacy localStorage data from legacy web views on Android and iOS.

When migrating old iOS apps from UIWebView to WKWebView, localStorage data is stored in different locations, which can cause users to lose their data during the transition. This plugin handles the migration of localStorage data from the UIWebView storage location to the WKWebView storage location.

## Install

```bash
npm install capacitor-localstorage-migration
npx cap sync
```

## API

<docgen-index>

* [`echo(...)`](#echo)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### echo(...)

```typescript
echo(options: { value: string; }) => Promise<{ value: string; }>
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ value: string; }</code> |

**Returns:** <code>Promise&lt;{ value: string; }&gt;</code>

--------------------

</docgen-api>
