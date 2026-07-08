package br.com.morefocus

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import br.com.morefocus.accessibility.MoreFocusAccessibilityService
import br.com.morefocus.settings.MoreFocusPreferences
import br.com.morefocus.ui.theme.MoreFocusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MoreFocusTheme {
                MoreFocusApp()
            }
        }
    }
}

@Composable
private fun MoreFocusApp() {
    val context = LocalContext.current
    val preferences = remember { MoreFocusPreferences(context) }
    var serviceEnabled by remember { mutableStateOf(context.isMoreFocusAccessibilityServiceEnabled()) }
    var focusEnabled by remember { mutableStateOf(preferences.isFocusEnabled()) }
    var instagramReelsEnabled by remember { mutableStateOf(preferences.isInstagramReelsEnabled()) }
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }

    DisposableEffect(lifecycleOwner, context) {
        if (lifecycleOwner == null) {
            return@DisposableEffect onDispose {}
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = context.isMoreFocusAccessibilityServiceEnabled()
                focusEnabled = preferences.isFocusEnabled()
                instagramReelsEnabled = preferences.isInstagramReelsEnabled()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MoreFocusScreen(
        serviceEnabled = serviceEnabled,
        focusEnabled = focusEnabled,
        instagramReelsEnabled = instagramReelsEnabled,
        onFocusEnabledChange = { enabled ->
            focusEnabled = enabled
            preferences.setFocusEnabled(enabled)
        },
        onInstagramReelsEnabledChange = { enabled ->
            instagramReelsEnabled = enabled
            preferences.setInstagramReelsEnabled(enabled)
        },
        onOpenAccessibilitySettings = {
            context.openAccessibilitySettings()
        }
    )
}

@Composable
private fun MoreFocusScreen(
    serviceEnabled: Boolean,
    focusEnabled: Boolean,
    instagramReelsEnabled: Boolean,
    onFocusEnabledChange: (Boolean) -> Unit,
    onInstagramReelsEnabledChange: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Header()

                ServiceStatusCard(
                    serviceEnabled = serviceEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )

                DisclosureCard()

                PreferenceCard(
                    focusEnabled = focusEnabled,
                    instagramReelsEnabled = instagramReelsEnabled,
                    onFocusEnabledChange = onFocusEnabledChange,
                    onInstagramReelsEnabledChange = onInstagramReelsEnabledChange
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "MoreFocus",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Bloqueio local de Instagram Reels para proteger seu foco.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ServiceStatusCard(
    serviceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (serviceEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (serviceEnabled) "Acessibilidade ativa" else "Acessibilidade desativada",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (serviceEnabled) {
                    "O MoreFocus já pode detectar Instagram Reels."
                } else {
                    "Ative o serviço MoreFocus nas configurações do Android."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onOpenAccessibilitySettings) {
                Text(text = "Abrir configurações")
            }
        }
    }
}

@Composable
private fun DisclosureCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Uso da acessibilidade",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "O MoreFocus lê textos, descrições e identificadores da tela do Instagram apenas para detectar Reels. Ele não captura imagens, não grava vídeos e não envia dados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreferenceCard(
    focusEnabled: Boolean,
    instagramReelsEnabled: Boolean,
    onFocusEnabledChange: (Boolean) -> Unit,
    onInstagramReelsEnabledChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Preferências",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            ToggleRow(
                title = "MoreFocus ativo",
                description = "Permite aplicar as regras selecionadas.",
                checked = focusEnabled,
                onCheckedChange = onFocusEnabledChange
            )
            ToggleRow(
                title = "Bloquear Instagram Reels",
                description = "Mostra um aviso ao detectar Reels.",
                checked = instagramReelsEnabled,
                onCheckedChange = onInstagramReelsEnabledChange
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun Context.openAccessibilitySettings() {
    runCatching {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var currentContext: Context? = this
    while (currentContext is ContextWrapper) {
        if (currentContext is LifecycleOwner) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

private fun Context.isMoreFocusAccessibilityServiceEnabled(): Boolean {
    val expectedComponent = ComponentName(this, MoreFocusAccessibilityService::class.java)
    val expectedNames = setOf(
        expectedComponent.flattenToString(),
        expectedComponent.flattenToShortString()
    )
    val enabledServices = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    return enabledServices
        .split(':')
        .any { enabledService ->
            expectedNames.any { expectedName ->
                enabledService.equals(expectedName, ignoreCase = true)
            }
        }
}

@Preview(showBackground = true)
@Composable
private fun MoreFocusScreenPreview() {
    MoreFocusTheme {
        MoreFocusScreen(
            serviceEnabled = false,
            focusEnabled = true,
            instagramReelsEnabled = true,
            onFocusEnabledChange = {},
            onInstagramReelsEnabledChange = {},
            onOpenAccessibilitySettings = {}
        )
    }
}
