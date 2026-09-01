package com.mycompanee.vendingsdk.sample.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mycompanee.vendingsdk.VendingSdk
import com.mycompanee.vendingsdk.model.CostCenter
import com.mycompanee.vendingsdk.model.VendingArticle
import com.mycompanee.vendingsdk.model.VendingBaseDataResponse
import com.mycompanee.vendingsdk.model.VendingTrainingProduct
import com.mycompanee.vendingsdk.model.VendingTransactionResult
import com.mycompanee.vendingsdk.sample.util.EnvReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendingScreen() {
    val context = LocalContext.current
    val sdk = remember { VendingSdk.getInstance(context) }
    val scope = rememberCoroutineScope()

    // Load values from .env file in debug builds
    var authKey by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var machineNumber by remember { mutableStateOf("") }

    // Load .env values on composition
    LaunchedEffect(Unit) {
        authKey = EnvReader.getValue(context, "AUTH_KEY", "")
        apiKey = EnvReader.getValue(context, "API_KEY", "")
        machineNumber = EnvReader.getValue(context, "VENDING_MACHINE_NUMBER", "")
    }
    var connectionTimeout by remember { mutableStateOf("30") }
    var statusMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    var isVendingActive by remember { mutableStateOf(false) }
    var transactionResult by remember { mutableStateOf<VendingTransactionResult?>(null) }
    var showTransactionDialog by remember { mutableStateOf(false) }
    var pendingDialogDismiss by remember { mutableStateOf(false) }

    // Cost center / training flow state
    var availableCostCenters by remember { mutableStateOf<List<CostCenter>>(emptyList()) }
    var showCostCenterDialog by remember { mutableStateOf(false) }
    var showTrainingDialog by remember { mutableStateOf(false) }
    var trainingProduct by remember { mutableStateOf<VendingTrainingProduct?>(null) }

    // Snapshot of the inputs, used to continue after the dialogs were shown
    var sessionAuthKey by remember { mutableStateOf("") }
    var sessionApiKey by remember { mutableStateOf("") }
    var sessionMachineNumber by remember { mutableStateOf(0) }
    var sessionTimeout by remember { mutableStateOf(30000L) }

    // Pending start request waiting for BLE runtime permissions
    var pendingPermissionStart by remember { mutableStateOf(false) }

    // Runtime permission handling: BLUETOOTH_SCAN/BLUETOOTH_CONNECT (Android 12+)
    // or ACCESS_FINE_LOCATION (older versions) are required for BLE scanning
    fun hasRequiredPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Start the vending workflow with the given cost center / training mode.
     */
    fun beginVending(costCenterId: Long?, trainingMode: Boolean) {
        if (costCenterId != null) {
            statusMessages = statusMessages + "Cost center selected (ID: $costCenterId)"
        } else {
            statusMessages = statusMessages + "Using purse"
        }
        if (trainingMode) {
            statusMessages = statusMessages + "Training mode active"
        }

        sdk.startVending(
            authKey = sessionAuthKey,
            apiKey = sessionApiKey,
            vendingMachineNumber = sessionMachineNumber,
            connectionTimeout = sessionTimeout,
            costCenterId = costCenterId,
            trainingMode = trainingMode,
            statusCallback = { status ->
                statusMessages = statusMessages + status
            },
            transactionCallback = { result ->
                transactionResult = result
                showTransactionDialog = true
                isVendingActive = false
            },
            trainingProductCallback = { product ->
                statusMessages = statusMessages + "Produkt entnommen - Wahl: ${product.selection}, Preis: ${product.amount} Cent"
                // Present the article assignment UI (port of legacy VendingArticleTraining)
                trainingProduct = product
            }
        )
    }

    /**
     * Check for available cost centers and let the user choose,
     * or start directly with the purse when none are linked.
     */
    fun presentCostCenterSelectionIfNeeded() {
        scope.launch {
            val costCentersResult = sdk.getAvailableCostCenters(sessionAuthKey, sessionApiKey)
            val costCenters = costCentersResult.getOrNull()
            if (costCenters == null) {
                statusMessages = statusMessages + "Failed to load cost centers: ${costCentersResult.exceptionOrNull()?.message}"
                isVendingActive = false
                return@launch
            }
            if (costCenters.isEmpty()) {
                beginVending(null, false)
            } else {
                availableCostCenters = costCenters
                showCostCenterDialog = true
            }
        }
    }

    /**
     * Check whether the purse is a training purse and ask the user how to proceed,
     * otherwise load the available cost centers and let the user choose.
     */
    fun checkPurseAndBegin(authKey: String, apiKey: String, machineNumber: Int, timeout: Long) {
        sessionAuthKey = authKey
        sessionApiKey = apiKey
        sessionMachineNumber = machineNumber
        sessionTimeout = timeout

        scope.launch {
            val purseInfoResult = sdk.getPurseInfo(authKey, apiKey)
            val purse = purseInfoResult.getOrNull()
            if (purse == null) {
                statusMessages = statusMessages + "Failed to load purse info: ${purseInfoResult.exceptionOrNull()?.message}"
                isVendingActive = false
                return@launch
            }

            if (VendingSdk.isTrainingPurse(purse)) {
                // Training purse: ask the user whether to start in training mode
                showTrainingDialog = true
            } else {
                // Normal flow: check for available cost centers
                presentCostCenterSelectionIfNeeded()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            // Permissions granted - continue the pending start request
            if (pendingPermissionStart) {
                pendingPermissionStart = false
                isVendingActive = true
                statusMessages = emptyList()
                checkPurseAndBegin(authKey, apiKey, machineNumber.toIntOrNull() ?: 0, sessionTimeout)
            }
        } else {
            pendingPermissionStart = false
            isVendingActive = false
            statusMessages = statusMessages + "Bluetooth-Berechtigungen wurden abgelehnt - bitte in den App-Einstellungen aktivieren"
        }
    }

    fun ensurePermissions(): Boolean {
        if (hasRequiredPermissions()) return true
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(permissions)
        return false
    }
    
    // Workaround for Compose hover event bug: delay dialog dismissal to avoid crash
    LaunchedEffect(pendingDialogDismiss) {
        if (pendingDialogDismiss) {
            delay(50) // Small delay to allow hover events to clear
            showTransactionDialog = false
            pendingDialogDismiss = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Vending SDK Sample",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = authKey,
            onValueChange = { authKey = it },
            label = { Text("Auth Key") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isVendingActive,
            singleLine = true
        )

        OutlinedTextField(
            value = machineNumber,
            onValueChange = { machineNumber = it },
            label = { Text("Vending Machine Number") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isVendingActive,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isVendingActive,
            singleLine = true
        )

        OutlinedTextField(
            value = connectionTimeout,
            onValueChange = { connectionTimeout = it },
            label = { Text("Connection Timeout (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isVendingActive,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (authKey.isEmpty() || apiKey.isEmpty() || machineNumber.isEmpty()) {
                        return@Button
                    }

                    val machineNum = machineNumber.toIntOrNull() ?: return@Button
                    val timeout = (connectionTimeout.toDoubleOrNull() ?: 30.0) * 1000

                    // Request BLE runtime permissions before starting the workflow;
                    // the start continues automatically once they are granted
                    if (!ensurePermissions()) {
                        sessionTimeout = timeout.toLong()
                        pendingPermissionStart = true
                        statusMessages = listOf("Bluetooth-Berechtigungen werden angefragt...")
                        return@Button
                    }

                    isVendingActive = true
                    statusMessages = emptyList()

                    checkPurseAndBegin(authKey, apiKey, machineNum, timeout.toLong())
                },
                modifier = Modifier.weight(1f),
                enabled = !isVendingActive && authKey.isNotEmpty() && apiKey.isNotEmpty() && machineNumber.isNotEmpty()
            ) {
                Text("Start Vending")
            }

            Button(
                onClick = {
                    sdk.abortVending()
                    isVendingActive = false
                },
                modifier = Modifier.weight(1f),
                enabled = isVendingActive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Abort")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Status:",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                statusMessages.forEach { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Transaction result dialog
    if (showTransactionDialog && transactionResult != null) {
        AlertDialog(
            onDismissRequest = { pendingDialogDismiss = true },
            title = { Text("Transaction Completed") },
            text = {
                val result = transactionResult!!
                Column {
                    Text("Selection: ${result.selection}")
                    Text("Amount: ${result.amount}")
                    Text("Fingerprint: ${result.fingerprint}")
                    result.article?.let {
                        Text("Article: ${it.name} (PLU: ${it.plu})")
                    }
                    result.transactionResponse?.let { response ->
                        Text("Balance Old: ${response.balanceOld}")
                        Text("Balance New: ${response.balanceNew}")
                        Text("Total Gross: ${response.totalGross}")
                        response.invoiceNumber?.let {
                            Text("Invoice Number: $it")
                        }
                        response.costCenter?.let { costCenter ->
                            Text("Cost Center: ${costCenter.identifier ?: ""} - ${costCenter.name ?: ""}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingDialogDismiss = true }) {
                    Text("OK")
                }
            }
        )
    }

    // Training mode confirmation dialog ("Im Trainingsmodus starten?")
    if (showTrainingDialog) {
        AlertDialog(
            onDismissRequest = {
                showTrainingDialog = false
                presentCostCenterSelectionIfNeeded()
            },
            title = { Text("Trainingsmodus") },
            text = { Text("Diese B\u00f6rse ist eine Trainingsb\u00f6rse. Im Trainingsmodus starten? Es werden keine echten Transaktionen gebucht.") },
            confirmButton = {
                TextButton(onClick = {
                    showTrainingDialog = false
                    // Training mode: skip the cost center selection (no real transactions are booked)
                    beginVending(null, true)
                }) {
                    Text("Ja")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTrainingDialog = false
                    // No training: continue with the normal cost center flow
                    presentCostCenterSelectionIfNeeded()
                }) {
                    Text("Nein")
                }
            }
        )
    }

    // Cost center selection dialog ("Bitte Kostenstelle ausw\u00e4hlen")
    if (showCostCenterDialog) {
        AlertDialog(
            onDismissRequest = {
                showCostCenterDialog = false
                isVendingActive = false
            },
            title = { Text("Bitte Kostenstelle ausw\u00e4hlen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableCostCenters.forEach { costCenter ->
                        TextButton(
                            onClick = {
                                showCostCenterDialog = false
                                beginVending(costCenter.id, false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("[${costCenter.identifier ?: ""}] - ${costCenter.name ?: ""}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showCostCenterDialog = false
                    beginVending(null, false) // "B\u00f6rse nutzen"
                }) {
                    Text("B\u00f6rse nutzen")
                }
            }
        )
    }

    // Training article assignment dialog (port of legacy VendingArticleTraining)
    trainingProduct?.let { product ->
        TrainingArticleDialog(
            sdk = sdk,
            scope = scope,
            authKey = sessionAuthKey,
            apiKey = sessionApiKey,
            machineNumber = sessionMachineNumber,
            product = product,
            onDismiss = { trainingProduct = null },
            onStatus = { statusMessages = statusMessages + it }
        )
    }
}

/**
 * Article assignment UI for training mode (port of legacy VendingArticleTraining page).
 * Shows all vending articles with a PLU/name filter, allows creating a new article
 * (format "[PLU]:[Name]") and assigns the selected article to the dispensed selection.
 */
@Composable
fun TrainingArticleDialog(
    sdk: VendingSdk,
    scope: CoroutineScope,
    authKey: String,
    apiKey: String,
    machineNumber: Int,
    product: VendingTrainingProduct,
    onDismiss: () -> Unit,
    onStatus: (String) -> Unit
) {
    var baseData by remember { mutableStateOf<VendingBaseDataResponse?>(null) }
    var filter by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createInput by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var confirmArticle by remember { mutableStateOf<VendingArticle?>(null) }

    // Load the vending base data when the dialog opens
    LaunchedEffect(Unit) {
        val result = sdk.reloadVendingBaseData(authKey)
        result.fold(
            onSuccess = { baseData = it },
            onFailure = { onStatus("Failed to load vending data: ${it.message}") }
        )
    }

    val machine = baseData?.vendingMachines?.firstOrNull { it.machineNumber == machineNumber }
    val allArticles = baseData?.vendingArticles ?: emptyList()
    val filteredArticles = allArticles.filter {
        filter.isBlank() || it.plu.contains(filter, ignoreCase = true) || it.name.contains(filter, ignoreCase = true)
    }
    val currentMapping = baseData?.vendingMachineArticleMappings?.firstOrNull {
        it.vendingMachineId == machine?.id && it.selection == product.selection
    }
    val assignedArticle = allArticles.firstOrNull { it.id == currentMapping?.vendingArticleId }

    fun assignArticle(article: VendingArticle) {
        if (isWorking) return
        isWorking = true
        scope.launch {
            val result = sdk.assignVendingArticle(
                authKey = authKey,
                vendingMachineNumber = machineNumber,
                selection = product.selection,
                vendingArticleId = article.id
            )
            isWorking = false
            result.fold(
                onSuccess = { refreshed ->
                    baseData = refreshed
                    resultMessage = "Artikelzuordnung gespeichert: ${article.plu} - ${article.name} \u2192 Wahl ${product.selection}"
                },
                onFailure = { resultMessage = "Da ist was Schief gelaufen! ${it.message}" }
            )
        }
    }

    fun createArticle() {
        val input = createInput
        if (!input.contains(":")) {
            resultMessage = "Bitte Format [PLU]:[Name] verwenden"
            return
        }
        val parts = input.split(":")
        if (parts.size != 2) {
            resultMessage = "Bitte Format [PLU]:[Name] verwenden"
            return
        }
        val plu = parts[0].trim()
        val name = parts[1].trim()
        if (plu.isEmpty() || name.isEmpty()) {
            resultMessage = "PLU und Name d\u00fcrfen nicht leer sein"
            return
        }

        isWorking = true
        showCreateDialog = false
        scope.launch {
            val result = sdk.createVendingArticle(authKey, plu, name)
            isWorking = false
            result.fold(
                onSuccess = { article ->
                    // Reload base data and filter to the new article (matches legacy behavior)
                    val reloadResult = sdk.reloadVendingBaseData(authKey)
                    reloadResult.fold(
                        onSuccess = { data ->
                            baseData = data
                            filter = plu
                        },
                        onFailure = { onStatus("Failed to reload vending data: ${it.message}") }
                    )
                    resultMessage = "Artikel erstellt: $plu - $name"
                },
                onFailure = { resultMessage = "Existiert bereits / Fehler: ${it.message}" }
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text("Artikel zuordnen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Wahl ${product.selection} - Preis ${product.amount} Cent${if (product.isLoading) " (Ladevorgang)" else ""}",
                    fontWeight = FontWeight.SemiBold
                )
                machine?.let {
                    Text(
                        text = "${it.machineNumber} (${it.building ?: "-"} - ${it.machineName ?: "-"})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                assignedArticle?.let {
                    Text(
                        text = "Aktuell zugeordnet: ${it.plu} - ${it.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("Filter (PLU / Name)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isWorking
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    items(filteredArticles) { article ->
                        Text(
                            text = "${article.plu} - ${article.name}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isWorking) { confirmArticle = article }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        createInput = ""
                        showCreateDialog = true
                    },
                    enabled = !isWorking
                ) {
                    Text("Artikel erstellen")
                }
                TextButton(
                    onClick = { if (!isWorking) onDismiss() },
                    enabled = !isWorking
                ) {
                    Text("Abbrechen")
                }
            }
        }
    )

    // Create article input dialog ("[PLU]:[Name]")
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Artikel erstellen") },
            text = {
                OutlinedTextField(
                    value = createInput,
                    onValueChange = { createInput = it },
                    label = { Text("Format [PLU]:[Name]") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { createArticle() }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Abbruch")
                }
            }
        )
    }

    // Confirm assignment dialog
    confirmArticle?.let { article ->
        AlertDialog(
            onDismissRequest = { confirmArticle = null },
            title = { Text("Artikel zuordnen") },
            text = { Text("${article.plu} - ${article.name} \u2192 Wahl ${product.selection}?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmArticle = null
                    assignArticle(article)
                }) {
                    Text("Zuordnen")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmArticle = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Result message dialog
    resultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            title = { Text("Training") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { resultMessage = null }) {
                    Text("Habe verstanden!")
                }
            }
        )
    }
}
