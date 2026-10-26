package com.graz.zapster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.graz.zapster.PairingStage
import com.graz.zapster.data.DiscoveredTv
import com.graz.zapster.data.TvSettings

@Composable
fun SettingsScreen(
    settings: TvSettings,
    canCancel: Boolean,
    scanning: Boolean,
    scanCompleted: Boolean,
    discovered: List<DiscoveredTv>,
    pairing: PairingStage,
    onScan: () -> Unit,
    onPair: (host: String, name: String) -> Unit,
    onSubmitCode: (String) -> Unit,
    onCancelPairing: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var host by remember(settings.host) { mutableStateOf(settings.host) }
    var name by remember(settings.name) { mutableStateOf(settings.name) }
    var code by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (settings.host.isBlank()) onScan()
    }

    LaunchedEffect(discovered) {
        if (discovered.size == 1 && host.isBlank()) {
            host = discovered.first().host
            name = discovered.first().label
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (pairing == PairingStage.Idle) "Connect to your TV" else "Pairing",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (pairing == PairingStage.AwaitingCode || pairing == PairingStage.Completing) {
            PairingCodeStep(
                code = code,
                onCodeChange = { code = it.uppercase().take(6) },
                submitting = pairing == PairingStage.Completing,
                onSubmit = { onSubmitCode(code) },
                onCancel = {
                    code = ""
                    onCancelPairing()
                },
            )
            return@Column
        }

        Text(
            text = "Your TV will show a six-digit code to confirm the connection. " +
                "Nothing needs to be changed in the TV's settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DiscoverySection(
            scanning = scanning,
            scanCompleted = scanCompleted,
            discovered = discovered,
            selectedHost = host,
            onScan = onScan,
            onSelect = {
                host = it.host
                name = it.label
            },
        )

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("TV IP address") },
            placeholder = { Text("192.168.1.50") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (canCancel) {
                PillButton(
                    label = "Cancel",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
            }
            PillButton(
                label = if (pairing == PairingStage.Requesting) "Connecting…" else "Pair with TV",
                onClick = {
                    if (pairing == PairingStage.Idle) onPair(host, name)
                },
                modifier = Modifier.weight(1f),
                container = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PairingCodeStep(
    code: String,
    onCodeChange: (String) -> Unit,
    submitting: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = "Look at your TV. Type the six-digit code it is showing.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )

    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text("Code from the TV") },
        placeholder = { Text("A1B2C3") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PillButton(
            label = "Cancel",
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        PillButton(
            label = if (submitting) "Pairing…" else "Confirm",
            onClick = { if (!submitting) onSubmit() },
            modifier = Modifier.weight(1f),
            container = MaterialTheme.colorScheme.primaryContainer,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun DiscoverySection(
    scanning: Boolean,
    scanCompleted: Boolean,
    discovered: List<DiscoveredTv>,
    selectedHost: String,
    onScan: () -> Unit,
    onSelect: (DiscoveredTv) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(
                label = if (scanning) "Scanning…" else "Scan for TVs",
                onClick = { if (!scanning) onScan() },
                modifier = Modifier.weight(1f),
            )
            if (scanning) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        when {
            scanning -> Text(
                text = "Looking for TVs on this Wi-Fi…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            discovered.isNotEmpty() -> discovered.forEach { tv ->
                DiscoveredTvRow(
                    tv = tv,
                    selected = tv.host == selectedHost,
                    onClick = { onSelect(tv) },
                )
            }

            scanCompleted -> Text(
                text = "No TVs found. Check the phone is on the same Wi-Fi as the TV, " +
                    "then enter the address by hand.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiscoveredTvRow(
    tv: DiscoveredTv,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = tv.label, style = MaterialTheme.typography.bodyMedium, color = content)
        Text(
            text = listOfNotNull(tv.host, tv.model).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}
