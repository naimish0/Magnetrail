package com.rameshta.magnetrail.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.data.PlayerSettings
import com.rameshta.magnetrail.data.SettingKey
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailSpacing
import com.rameshta.magnetrail.ui.theme.MagnetrailMuted

@Composable
fun SettingsScreen(
    settings: PlayerSettings,
    onBack: () -> Unit,
    onSettingChanged: (SettingKey, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalMagnetrailSpacing.current
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm)) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                        .semantics { contentDescription = "Close settings" },
                ) { Text("Back") }
                Text(
                    "Settings",
                    modifier = Modifier.align(Alignment.Center).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
            ) {
                SettingToggle(
                    title = "Sound",
                    detail = "Short offline game effects",
                    checked = settings.soundEnabled,
                    onCheckedChange = { onSettingChanged(SettingKey.SOUND, it) },
                )
                HorizontalDivider()
                SettingToggle(
                    title = "Haptics",
                    detail = "Restrained touch confirmations",
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { onSettingChanged(SettingKey.HAPTICS, it) },
                )
                HorizontalDivider()
                SettingToggle(
                    title = "Reduced motion",
                    detail = "Short fades and direct movement",
                    checked = settings.reducedMotion,
                    onCheckedChange = { onSettingChanged(SettingKey.REDUCED_MOTION, it) },
                )
                HorizontalDivider()
                SettingToggle(
                    title = "High-contrast fields",
                    detail = "Stronger field outlines and direction cues",
                    checked = settings.highContrastFields,
                    onCheckedChange = { onSettingChanged(SettingKey.HIGH_CONTRAST_FIELDS, it) },
                )
                HorizontalDivider()
                SettingToggle(
                    title = "Path-preview assistance",
                    detail = "Show the first engine-derived hint segment",
                    checked = settings.pathPreviewAssistance,
                    onCheckedChange = { onSettingChanged(SettingKey.PATH_PREVIEW_ASSISTANCE, it) },
                )
                Text(
                    text = "Progress and settings stay on this device. Music is not included in this slice.",
                    modifier = Modifier.padding(top = spacing.lg, bottom = spacing.screenBottom),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MagnetrailMuted,
                )
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val spacing = LocalMagnetrailSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics {
                contentDescription = title
                stateDescription = if (checked) "On" else "Off"
            }
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = spacing.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                modifier = Modifier.padding(top = spacing.xxs),
                style = MaterialTheme.typography.bodyMedium,
                color = MagnetrailMuted,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
