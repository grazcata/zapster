package com.graz.zapster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.graz.zapster.ui.RemoteScreen
import com.graz.zapster.ui.SettingsScreen
import com.graz.zapster.ui.TvRemoteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TvRemoteTheme {
                TvRemoteApp()
            }
        }
    }
}

@Composable
private fun TvRemoteApp(viewModel: RemoteViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface transport failures without stealing focus from the keypad.
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (!state.settingsLoaded) return@Scaffold

        if (state.showSettings) {
            SettingsScreen(
                settings = state.settings,
                canCancel = state.settings.isConfigured,
                scanning = state.scanning,
                scanCompleted = state.scanCompleted,
                discovered = state.discovered,
                pairing = state.pairing,
                onScan = viewModel::scan,
                onPair = viewModel::startPairing,
                onSubmitCode = viewModel::submitPairingCode,
                onCancelPairing = viewModel::cancelPairing,
                onCancel = viewModel::closeSettings,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            RemoteScreen(
                state = state,
                onPress = viewModel::press,
                onLaunchApp = viewModel::launchApp,
                onPower = viewModel::setPower,
                onOpenSettings = viewModel::openSettings,
                onReconnect = viewModel::connect,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}
