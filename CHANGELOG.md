# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-10-27

### Fixed
- **"Loading vending data" no longer blocks on `GetLocationsWithPurse`** (fixed an extreme slowdown on large tenants, e.g. tenant 2035)
  - The SDK previously called the parameterless `GET /MobileApplication/GetLocationsWithPurse` on every session start, which returns **every purse of the tenant** (4,300+ purses on tenant 2035, serialized via an N+1 per-purse location query) — while the result was **never used** by the SDK at all
  - The call has been removed entirely; the vending base data (`getVendingBaseData`) is the only request in this step now, and the purse continues to be resolved via the third-party API. The legacy app was never affected because it only queries the user's own purses (`GetLocationsWithPurse/{userId}`, indexed)
  - `ClientServiceAPI.getLocationsWithPurse` remains available for future use
- **Price override rules from the backend (`VendingMachinePriceMappings`) are now applied** (fixes a parity gap to the legacy app and the backend OnlineVendingService)
  - Previously the machine-reported sale price was forwarded to `SendVendingTransaction` unchanged; the backend books the client-reported amount 1:1, so configured per-user-group / sub-user-group / price-list overrides (e.g. subsidized prices) were silently ignored and users were overcharged
  - The resolved article mapping (including the standard-article fallback on selection -1) is now the base for the price override lookup, exactly like the legacy `Ble_SaleComplete`
  - Match rules (last one wins): user group only, sub user group only, price list only, user group + sub user group, user group + sub user group + price list
  - Sales only: loading events (negative amounts) are never overridden; negative override prices are passed through and booked as a credit, matching the legacy behavior
  - When an override changes the price, a status update `Price override applied: <machine> -> <override> cents` is emitted
  - The overridden amount is used for the backend transaction and reported in `VendingTransactionResult.amount`

### Added
- `Purse` model extended with `subuserGroupId` (decoded from `subUserGroupId`, provided by `GetPurseByUUID`; required for the sub-user-group price override rules)
- Unit tests for the price override rule matching (`PriceOverrideTest`)

### Changed
- Updated SDK version to 1.2.0

## [1.1.0] - 2026-08-31

### Added
- **Cost center support for vending transactions** (ported from the legacy myCompanee app)
  - If an RFID card linked to the user's purse has a cost center assigned, the transaction can be charged to that cost center instead of the user's purse
  - New API method `getAvailableCostCenters(authKey, apiKey)` — returns the cost centers available to the current purse via `GET /MobileApplication/GetExternalCardsWithCostCenter?purseUUID=`
  - New optional parameter `costCenterId` on `startVending(...)` (default `null` = charge purse as before)
  - When a cost center is selected, a fixed dummy balance of 8888 cents is sent to the vending machine via BLE (`overrideBalance`), matching the legacy app behavior — the machine releases the sale and the server charges the cost center
  - The `costCenterId` is passed through to `SendVendingTransaction`, so the backend books the transaction on the cost center
- **Training mode for vending machines** (ported from the legacy myCompanee app)
  - Maintenance/setup mode for service technicians: no real transactions are sent; each dispensed product is reported for article assignment
  - New API method `getPurseInfo(authKey, apiKey)` and `VendingSdk.isTrainingPurse(purse)` — a purse named "TRAININGMODE" enables the training flow; the host app asks the user (like the legacy "Im Trainingsmodus starten?" dialog)
  - New optional parameters `trainingMode` and `trainingProductCallback` on `startVending(...)`
  - BLE differences (match legacy BLE.cs): fixed dummy balance of 9999 cents (takes precedence over the cost center override), sale acknowledged with 0x0c instead of 0x0a, no local balance deduction, idle timeout disabled (connection stays open), app data re-armed after 2 seconds so consecutive assignments work without reconnecting, status messages suffixed with "(TRAINING)"
  - Journal entries are sent via `POST /crud/JournalEntries` (fire-and-forget, legacy event types and message formats, user id instead of user name): START_TRAINING before connecting, PRODUCT_SELECT per dispense, FINISH_TRAINING on disconnect/abort
  - New APIs for the article assignment flow: `createVendingArticle(...)` (POST /crud/VendingArticles with case-insensitive PLU duplicate check), `assignVendingArticle(...)` (creates or overwrites VendingMachineArticleMappings incl. ARTICLE_ASSIGN / OVERWRITE_ASSIGNMENT journal entries), `reloadVendingBaseData(...)`
  - New models: `JournalEntry`, `JournalEventType`, `VendingTrainingProduct`, `CardsWithCostCenter`, `PurseCardsWithCostCenter`; `Purse` extended with `name`, `PurseExtCardNumber` extended with `costCenterId`
  - Sample app: training confirmation dialog, cost center selection dialog ("Bitte Kostenstelle auswählen" / "Börse nutzen") and article assignment UI ("Artikel zuordnen" with filter, article creation via `[PLU]:[Name]`), booked cost center shown in the transaction result

### Changed
- Status poll (0x55) now answers with "app data available" (0x06) only while app data has not been consumed yet, matching the iOS SDK fix
- `abortVending()` now disconnects first and then clears the callbacks, so the FINISH_TRAINING journal entry is still sent in training mode
- Updated SDK version to 1.1.0

## [1.0.2] - 2026-01-28

### Added
- Initial public release of Vending Android SDK
- BLE communication with vending machines
- Authentication and transaction processing
- Sample application with Jetpack Compose
- Support for API 23 (Android 6.0) through API 35 (Android 15)
- Comprehensive API documentation
- Proprietary License (myCompanee GmbH)

### Features
- Secure authentication with backend services
- Bluetooth Low Energy (BLE) communication
- Transaction lifecycle management
- Real-time status updates
- Thread-safe API design
- Singleton pattern matching iOS SDK

### Dependencies
- Kotlin 1.9.24
- Android Core KTX 1.12.0
- Retrofit 2.11.0
- OkHttp 4.12.0
- AndroidX Security Crypto 1.1.0-alpha06
- Coroutines 1.8.0
