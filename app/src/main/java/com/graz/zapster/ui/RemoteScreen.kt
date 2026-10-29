package com.graz.zapster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.graz.zapster.ConnectionState
import com.graz.zapster.UiState
import com.graz.zapster.data.atv.AtvKey

/** HDMI inputs, as Android TV input keycodes. */
private val hdmiInputs = listOf(
    "HDMI 1" to AtvKey.TV_INPUT_HDMI_1,
    "HDMI 2" to AtvKey.TV_INPUT_HDMI_2,
    "HDMI 3" to AtvKey.TV_INPUT_HDMI_3,
    "HDMI 4" to AtvKey.TV_INPUT_HDMI_4,
)

/**
 * Deep links opened through RemoteAppLinkLaunchRequest. The TV resolves these to
 * the installed app, so they launch it directly rather than going via the
 * home screen.
 */
private const val NETFLIX_LINK = "https://www.netflix.com/title"
private const val YOUTUBE_LINK = "https://www.youtube.com"

private val netflixRed = Color(0xFFE50914)
private val youtubeRed = Color(0xFFFF0033)

@Composable
fun RemoteScreen(
    state: UiState,
    onPress: (Int) -> Unit,
    onSendText: (String, Boolean) -> Unit,
    onLaunchApp: (String) -> Unit,
    onPower: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showKeyboardDialog by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StatusHeader(
            state = state,
            onOpenSettings = onOpenSettings,
            onReconnect = onReconnect,
        )

        KeyRow {
            KeyButton(
                icon = Icons.Rounded.PowerSettingsNew,
                description = "Power off",
                onClick = { onPower(false) },
                container = MaterialTheme.colorScheme.error,
                tint = MaterialTheme.colorScheme.onError,
            )
            TextKey(label = "ON", onClick = { onPower(true) })
            KeyButton(
                icon = Icons.Rounded.VolumeOff,
                description = "Mute",
                onClick = { onPress(AtvKey.VOLUME_MUTE) },
            )
            KeyButton(
                icon = Icons.Rounded.MoreHoriz,
                description = "Menu",
                onClick = { onPress(AtvKey.MENU) },
            )
        }

        DPad(
            onUp = { onPress(AtvKey.DPAD_UP) },
            onDown = { onPress(AtvKey.DPAD_DOWN) },
            onLeft = { onPress(AtvKey.DPAD_LEFT) },
            onRight = { onPress(AtvKey.DPAD_RIGHT) },
            onOk = { onPress(AtvKey.DPAD_CENTER) },
            upIcon = Icons.Rounded.KeyboardArrowUp,
            downIcon = Icons.Rounded.KeyboardArrowDown,
            leftIcon = Icons.Rounded.KeyboardArrowLeft,
            rightIcon = Icons.Rounded.KeyboardArrowRight,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        KeyRow {
            KeyButton(
                icon = Icons.Rounded.ArrowBack,
                description = "Back",
                onClick = { onPress(AtvKey.BACK) },
            )
            KeyButton(
                icon = Icons.Rounded.Home,
                description = "Home",
                onClick = { onPress(AtvKey.HOME) },
                container = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            KeyButton(
                icon = Icons.Rounded.Keyboard,
                description = "Clavier / Saisie de texte",
                onClick = { showKeyboardDialog = true },
            )
            TextKey(label = "APPS", onClick = { onPress(AtvKey.ALL_APPS) })
        }

        // Kept above the fold: these are the two most-used destinations, and
        // they were easy to miss at the bottom of the screen.
        KeyRow(arrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                label = "NETFLIX",
                onClick = { onLaunchApp(NETFLIX_LINK) },
                modifier = Modifier.weight(1f),
                container = netflixRed,
                tint = Color.White,
            )
            PillButton(
                label = "YouTube",
                onClick = { onLaunchApp(YOUTUBE_LINK) },
                modifier = Modifier.weight(1f),
                container = youtubeRed,
                tint = Color.White,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Rocker(
                upIcon = Icons.Rounded.VolumeUp,
                downIcon = Icons.Rounded.VolumeDown,
                label = "VOL",
                onUp = { onPress(AtvKey.VOLUME_UP) },
                onDown = { onPress(AtvKey.VOLUME_DOWN) },
            )
            Rocker(
                upIcon = Icons.Rounded.KeyboardArrowUp,
                downIcon = Icons.Rounded.KeyboardArrowDown,
                label = "CH",
                onUp = { onPress(AtvKey.CHANNEL_UP) },
                onDown = { onPress(AtvKey.CHANNEL_DOWN) },
            )
        }

        SectionLabel("PLAYBACK")
        KeyRow {
            KeyButton(
                icon = Icons.Rounded.FastRewind,
                description = "Rewind",
                onClick = { onPress(AtvKey.MEDIA_REWIND) },
                size = 56.dp,
            )
            KeyButton(
                icon = Icons.Rounded.PlayArrow,
                description = "Play",
                onClick = { onPress(AtvKey.MEDIA_PLAY) },
                size = 56.dp,
            )
            KeyButton(
                icon = Icons.Rounded.Pause,
                description = "Pause",
                onClick = { onPress(AtvKey.MEDIA_PAUSE) },
                size = 56.dp,
            )
            KeyButton(
                icon = Icons.Rounded.Stop,
                description = "Stop",
                onClick = { onPress(AtvKey.MEDIA_STOP) },
                size = 56.dp,
            )
            KeyButton(
                icon = Icons.Rounded.FastForward,
                description = "Fast forward",
                onClick = { onPress(AtvKey.MEDIA_FAST_FORWARD) },
                size = 56.dp,
            )
        }

        SectionLabel("INPUTS")
        KeyRow(arrangement = Arrangement.spacedBy(10.dp)) {
            hdmiInputs.forEach { (label, key) ->
                PillButton(
                    label = label,
                    onClick = { onPress(key) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        KeyRow(arrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                label = "Input",
                onClick = { onPress(AtvKey.TV_INPUT) },
                modifier = Modifier.weight(1f),
            )
            PillButton(
                label = "Info",
                onClick = { onPress(AtvKey.INFO) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showKeyboardDialog) {
        KeyboardInputDialog(
            onDismiss = { showKeyboardDialog = false },
            onSend = { text, enter ->
                onSendText(text, enter)
                showKeyboardDialog = false
            },
        )
    }
}

@Composable
private fun StatusHeader(
    state: UiState,
    onOpenSettings: () -> Unit,
    onReconnect: () -> Unit,
) {
    val (dotColour, statusText) = when (state.connection) {
        ConnectionState.Connected -> MaterialTheme.colorScheme.primary to
            state.settings.name.ifBlank { "Connected" }

        ConnectionState.Connecting -> MaterialTheme.colorScheme.secondary to "Connecting…"
        ConnectionState.Error -> MaterialTheme.colorScheme.error to
            (state.message ?: "Not connected")

        ConnectionState.Unknown -> MaterialTheme.colorScheme.error to
            if (state.settings.isConfigured) "Not connected" else "Not paired — tap the gear"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColour)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (state.connection == ConnectionState.Connected &&
                    state.settings.host.isNotBlank()
                ) {
                    Text(
                        text = state.settings.host,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.connection == ConnectionState.Error) {
                TextKey(label = "Retry", onClick = onReconnect, size = 48.dp)
            }
            KeyButton(
                icon = Icons.Rounded.Settings,
                description = "Settings",
                onClick = onOpenSettings,
                size = 48.dp,
                container = Color.Transparent,
            )
        }
    }
}

@Composable
private fun KeyboardInputDialog(
    onDismiss: () -> Unit,
    onSend: (String, Boolean) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saisie de texte sur la TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Tapez ou collez votre texte (recherche, mot de passe, URL...). Il sera inséré dans le champ actif de la TV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Votre texte ici...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSend(text, false) },
                    enabled = text.isNotEmpty(),
                ) {
                    Text("Envoyer")
                }
                Button(
                    onClick = { onSend(text, true) },
                    enabled = text.isNotEmpty(),
                ) {
                    Text("Envoyer ↵")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
