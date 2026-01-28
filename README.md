# Vending Android SDK

Native Android SDK for vending machine integration via Bluetooth Low Energy. This SDK provides a complete solution for integrating vending machine functionality into Android applications.

## Features

- 🔐 **Secure Authentication**: Token storage using Jetpack Security (EncryptedSharedPreferences)
- 📡 **BLE Communication**: Full Bluetooth Low Energy support for vending machine communication
- 💳 **Transaction Processing**: Complete transaction flow with backend integration
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
    implementation("com.mycompanee:vending-sdk:1.0.2")
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
- `statusCallback: (String) -> Unit` - Callback for status messages
- `transactionCallback: (VendingTransactionResult) -> Unit` - Callback for transaction results

#### `abortVending()`

Abort the current vending session and disconnect from the BLE device.

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
