# Vending Android SDK

Native Android SDK for vending machine integration via Bluetooth Low Energy. This SDK provides a complete solution for integrating vending machine functionality into Android applications.

## Features

- 🔐 **Secure Authentication**: Token storage using Jetpack Security (EncryptedSharedPreferences)
- 📡 **BLE Communication**: Full Bluetooth Low Energy support for vending machine communication
- 💳 **Transaction Processing**: Complete transaction flow with backend integration
- 🏢 **Cost Centers**: Charge vending transactions to a cost center instead of the user's purse
- 🛠️ **Training Mode**: Maintenance mode for article assignment without real transactions
- 🎯 **Simple API**: Easy-to-use singleton pattern matching iOS SDK interface
- 📱 **Android 6.0+**: Compatible with API 23 and later

## Requirements

- **Minimum SDK**: API 23 (Android 6.0)
- **Target SDK**: API 35 (Android 15)
- **Kotlin**: 1.9.24+
- **Java**: 8+

## Installation

### Using AAR File (Recommended)

1. Download `vending-sdk-release.aar` from the [Releases](../../releases) page
2. Copy it to your app's `libs` folder
3. Add to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/vending-sdk-release.aar"))
    
    // Required dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}
```

### Using Maven (Coming Soon)

```kotlin
dependencies {
    implementation("com.mycompanee:vending-sdk:1.1.0")
}
```

## Quick Start

### 1. Add Permissions

Add to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

### 2. Request Runtime Permissions (Android 12+)

```kotlin
private val bluetoothPermissionsLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    if (permissions.entries.all { it.value }) {
        startVending()
    }
}

fun checkPermissions() {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
    }
    if (permissions.isNotEmpty()) {
        bluetoothPermissionsLauncher.launch(permissions.toTypedArray())
    }
}
```

### 3. Start Vending

```kotlin
import com.mycompanee.vendingsdk.VendingSdk
import com.mycompanee.vendingsdk.model.VendingTransactionResult

class MainActivity : AppCompatActivity() {
    private lateinit var sdk: VendingSdk
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sdk = VendingSdk.getInstance(applicationContext)
    }
    
    fun startVending() {
        sdk.startVending(
            authKey = "your-auth-key",
            apiKey = "your-api-key",
            vendingMachineNumber = 123,
            connectionTimeout = 30000L,
            statusCallback = { status ->
                Log.d("VendingSDK", status)
            },
            transactionCallback = { result ->
                handleTransaction(result)
            }
        )
    }
    
    private fun handleTransaction(result: VendingTransactionResult) {
        val message = """
            ✅ Transaction Successful!
            
            Selection: ${result.selection}
            Price: ${result.amount / 100}.${"%02d".format(result.amount % 100)} €
            Article: ${result.article?.name ?: "Unknown"}
        """.trimIndent()
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
```

### Cost Centers

If an RFID card linked to the user's purse has a cost center assigned, the transaction can be charged to that cost center instead of the user's purse. Query the available cost centers before starting a vending session:

```kotlin
val costCentersResult = sdk.getAvailableCostCenters(authKey, apiKey)
costCentersResult.fold(
    onSuccess = { costCenters ->
        if (costCenters.isEmpty()) {
            // No cost centers - start normally with the purse
            sdk.startVending(authKey, apiKey, vendingMachineNumber = 123, /* ... */)
        } else {
            // Let the user pick a cost center, then:
            // sdk.startVending(..., costCenterId = costCenters[0].id, /* ... */)
        }
    },
    onFailure = { error -> println("Failed to load cost centers: ${error.message}") }
)
```

When a cost center is selected, the SDK sends a fixed dummy balance of 8888 cents to the machine (`overrideBalance`); the machine releases the sale and the backend books the transaction on the cost center. The booked cost center is returned in `VendingSendTransactionResponse.costCenter`.

### Training Mode

The training mode is a maintenance mode for service technicians: no real transactions are booked. Instead, every product dispensed at the machine is reported to the app, which can then assign an article to the selected slot (selection).

**Trigger:** The mode is enabled when the purse is named `TRAININGMODE`. The SDK exposes this check; the host app asks the user how to proceed:

```kotlin
val purseInfoResult = sdk.getPurseInfo(authKey, apiKey)
purseInfoResult.onSuccess { purse ->
    if (VendingSdk.isTrainingPurse(purse)) {
        // Ask the user: "Im Trainingsmodus starten?" (training) or normal vending
    }
}
```

**Start with the `trainingMode` parameter:**

```kotlin
sdk.startVending(
    authKey = authKey,
    apiKey = apiKey,
    vendingMachineNumber = 123,
    trainingMode = true,
    statusCallback = { status -> println(status) },
    transactionCallback = { /* not called in training mode */ },
    trainingProductCallback = { product ->
        // product.selection, product.amount (cents), product.fingerprint, product.isLoading
        // Show your article assignment UI here
    }
)
```

**Article assignment APIs** (used by your assignment UI):

```kotlin
// Create a new article (case-insensitive PLU duplicate check)
sdk.createVendingArticle(authKey, plu = "4711", name = "Water 0.5l")

// Assign an article to a selection on a machine (creates or overwrites the mapping)
sdk.assignVendingArticle(authKey, vendingMachineNumber = 123, selection = 3, vendingArticleId = article.id)

// Refresh base data (articles, mappings, machines, cost centers)
sdk.reloadVendingBaseData(authKey)
```

Journal entries for training events (start, sale, finish, article create/assign/overwrite) are sent to the backend automatically (fire-and-forget).

## API Reference

### VendingSdk

Main entry point for the SDK.

#### `getInstance(context: Context): VendingSdk`

Get the singleton instance of the SDK.

#### `getSdkVersion(): String`

Get the current SDK version string.

#### `startVending(...)`

Start the vending workflow.

**Parameters:**
- `authKey: String` - Authentication key
- `apiKey: String` - API key for third-party API access
- `vendingMachineNumber: Int` - Machine number to connect to
- `connectionTimeout: Long` - Timeout in milliseconds (default: 30000)
- `costCenterId: Long?` - Optional cost center ID to charge the transaction to (default: `null` = charge purse)
- `trainingMode: Boolean` - When true, no real transactions are sent (default: `false`)
- `statusCallback: (String) -> Unit` - Callback for status messages
- `transactionCallback: (VendingTransactionResult) -> Unit` - Callback for transaction results (not called in training mode)
- `trainingProductCallback: ((VendingTrainingProduct) -> Unit)?` - Called for every product dispensed in training mode

#### `abortVending()`

Abort the current vending session and disconnect from the BLE device. In training mode this also writes the FINISH_TRAINING journal entry.

#### `getPurseInfo(authKey: String, apiKey: String): Result<Purse>` (suspend)

Load the full purse (including its name) for the given authKey. Use with `isTrainingPurse` to detect a training purse.

#### `isTrainingPurse(purse: Purse): Boolean` (companion)

Returns true when the purse is named "TRAININGMODE".

#### `getAvailableCostCenters(authKey: String, apiKey: String): Result<List<CostCenter>>` (suspend)

Cost centers available to the current purse (empty if none). A cost center is available if an RFID card linked to the purse has a cost center assigned.

#### `createVendingArticle(authKey: String, plu: String, name: String): Result<VendingArticle>` (suspend)

Create a new vending article (training mode). Fails when an article with the same PLU already exists (case-insensitive).

#### `assignVendingArticle(authKey: String, vendingMachineNumber: Int, selection: Int, vendingArticleId: Long): Result<VendingBaseDataResponse>` (suspend)

Assign (or overwrite) an article to a selection on a vending machine; returns the refreshed vending base data.

#### `reloadVendingBaseData(authKey: String): Result<VendingBaseDataResponse>` (suspend)

Reload the vending base data (articles, mappings, machines, cost centers).

### VendingTransactionResult

Transaction result containing:
- `selection: Int` - Selected item number
- `amount: Int` - Transaction amount in cents
- `fingerprint: UInt` - Transaction fingerprint
- `article: VendingArticle?` - Article information with `id`, `plu`, and `name`
- `transactionResponse: VendingSendTransactionResponse?` - Backend response

**Convenience properties:**
- `terminalName: String?` - Terminal name (from transactionResponse)
- `storeName: String?` - Store name (from transactionResponse)
- `transactionUUID: String?` - Transaction UUID (from transactionResponse)

### VendingSendTransactionResponse

Backend response containing:
- `balanceOld: Int` - Previous balance in cents
- `balanceNew: Int` - New balance after transaction in cents
- `totalGross: Int` - Total gross amount in cents
- `invoiceNumber: String?` - Invoice number
- `terminalName: String?` - Terminal name
- `storeName: String?` - Store name
- `transactionUUID: String?` - Transaction UUID

## Status Messages

The SDK provides status updates through the callback:

| Status | Description |
|--------|-------------|
| "SDK Version: 1.0.x" | SDK initialized |
| "Authenticating..." | Authentication in progress |
| "Loading vending data..." | Fetching machine data |
| "Loading purse information..." | Loading user wallet |
| "Connecting..." | BLE connection in progress |
| "Connected" | Successfully connected |
| "Transaction completed" | Success |

## Migration from iOS SDK

The Android SDK maintains functional parity with the iOS SDK:

| iOS | Android |
|-----|---------|
| `VendingSDK.shared` | `VendingSdk.getInstance(context)` |
| `VendingSDK.getSDKVersion()` | `VendingSdk.getSdkVersion()` |
| `startVending(authKey:apiKey:vendingMachineNumber:connectionTimeout:statusCallback:transactionCallback:)` | `startVending(authKey, apiKey, vendingMachineNumber, connectionTimeout, statusCallback, transactionCallback)` |
| `startVending(...:costCenterId:trainingMode:trainingProductCallback:)` | `startVending(..., costCenterId, trainingMode, statusCallback, transactionCallback, trainingProductCallback)` |
| `getPurseInfo(authKey:apiKey:completion:)` | `getPurseInfo(authKey, apiKey)` (suspend) |
| `isTrainingPurse(_:)` | `isTrainingPurse(purse)` |
| `getAvailableCostCenters(authKey:apiKey:completion:)` | `getAvailableCostCenters(authKey, apiKey)` (suspend) |
| `abortVending()` | `abortVending()` |

## Example App

See the `vending-sdk-sample-app` directory for a complete example implementation using Jetpack Compose.

## Thread Safety

The SDK is thread-safe and can be called from any thread. All callbacks are delivered on the main thread.

## License

This project is proprietary software owned by myCompanee GmbH. All rights reserved.

See the [LICENSE](LICENSE) file for full license details. For licensing inquiries, please contact support@mycompanee.com

## Support

For support, please contact:
- Email: support@mycompanee.com
- Issues: [GitHub Issues](../../issues)

## Version History

See [CHANGELOG.md](CHANGELOG.md) for detailed version history.

---

Made with ❤️ by myCompanee GmbH
