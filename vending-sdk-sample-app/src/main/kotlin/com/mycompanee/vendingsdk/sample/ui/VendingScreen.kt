package com.mycompanee.vendingsdk.sample.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.mycompanee.vendingsdk.VendingSdk
import com.mycompanee.vendingsdk.model.VendingTransactionResult
import com.mycompanee.vendingsdk.sample.util.EnvReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendingScreen() {
    val context = LocalContext.current
    val sdk = remember { VendingSdk.getInstance(context) }

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

                    isVendingActive = true
                    statusMessages = emptyList()

                    sdk.startVending(
                        authKey = authKey,
                        apiKey = apiKey,
                        vendingMachineNumber = machineNum,
                        connectionTimeout = timeout.toLong(),
                        statusCallback = { status ->
                            statusMessages = statusMessages + status
                        },
                        transactionCallback = { result ->
                            transactionResult = result
                            showTransactionDialog = true
                            isVendingActive = false
                        }
                    )
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
}
