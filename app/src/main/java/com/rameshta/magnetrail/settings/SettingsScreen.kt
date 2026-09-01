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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.rameshta.magnetrail.R
import com.rameshta.magnetrail.data.PlayerSettings
import com.rameshta.magnetrail.data.SettingKey
import com.rameshta.magnetrail.localization.AppLanguageManager
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailSpacing
import com.rameshta.magnetrail.ui.theme.MagnetrailMuted

@Composable
fun SettingsScreen(
    settings: PlayerSettings,
    onBack: () -> Unit,
    onSettingChanged: (SettingKey, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    privacyOptionsRequired: Boolean = false,
    privacyPolicyUrl: String? = null,
    showPrivacyPolicyPlaceholder: Boolean = false,
    onPrivacyOptions: () -> Unit = {},
    onPrivacyPolicy: () -> Unit = {},
    selectedLanguageTag: String = "",
    onLanguageSelected: (String) -> Unit = {},
) {
    val spacing = LocalMagnetrailSpacing.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    val closeSettingsDescription = stringResource(R.string.close_settings)
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
                        .semantics { contentDescription = closeSettingsDescription },
                ) { Text(stringResource(R.string.back)) }
                Text(
                    stringResource(R.string.settings),
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
                    title = stringResource(R.string.settings_sound),
                    detail = stringResource(R.string.settings_sound_detail),
                    checked = settings.soundEnabled,
                    onCheckedChange = { onSettingChanged(SettingKey.SOUND, it) },
                )
                HorizontalDivider()
                SettingsAction(
                    title = stringResource(R.string.settings_language),
                    detail = AppLanguageManager.supportedLanguages
                        .firstOrNull { it.languageTag == selectedLanguageTag }
                        ?.nativeName
                        ?: stringResource(R.string.language_system_default),
                    onClick = { showLanguageDialog = true },
                )
                if (privacyOptionsRequired) {
                    HorizontalDivider()
                    SettingsAction(
                        title = stringResource(R.string.settings_privacy_options),
                        detail = stringResource(R.string.settings_privacy_options_detail),
                        onClick = onPrivacyOptions,
                    )
                }
                HorizontalDivider()
                SettingsAction(
                    title = stringResource(R.string.settings_privacy_policy),
                    detail = if (privacyPolicyUrl != null) {
                        stringResource(R.string.settings_privacy_policy_public_detail)
                    } else {
                        stringResource(R.string.settings_privacy_policy_local_detail)
                    },
                    onClick = onPrivacyPolicy,
                )
                HorizontalDivider()
                SettingToggle(
                    title = stringResource(R.string.settings_haptics),
                    detail = stringResource(R.string.settings_haptics_detail),
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { onSettingChanged(SettingKey.HAPTICS, it) },
                )
                HorizontalDivider()
                SettingToggle(
                    title = stringResource(R.string.settings_reduced_motion),
                    detail = stringResource(R.string.settings_reduced_motion_detail),
                    checked = settings.reducedMotion,
                    onCheckedChange = { onSettingChanged(SettingKey.REDUCED_MOTION, it) },
                )
                HorizontalDivider()
                SettingToggle(
                    title = stringResource(R.string.settings_high_contrast),
                    detail = stringResource(R.string.settings_high_contrast_detail),
                    checked = settings.highContrastFields,
                    onCheckedChange = { onSettingChanged(SettingKey.HIGH_CONTRAST_FIELDS, it) },
                )
                HorizontalDivider()
                SettingToggle(
                    title = stringResource(R.string.settings_path_preview),
                    detail = stringResource(R.string.settings_path_preview_detail),
                    checked = settings.pathPreviewAssistance,
                    onCheckedChange = { onSettingChanged(SettingKey.PATH_PREVIEW_ASSISTANCE, it) },
                )
                Text(
                    text = stringResource(R.string.settings_storage_note),
                    modifier = Modifier.padding(top = spacing.lg, bottom = spacing.screenBottom),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MagnetrailMuted,
                )
            }
        }
    }
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language_choose)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LanguageOption(
                        name = stringResource(R.string.language_system_default),
                        selected = selectedLanguageTag.isBlank(),
                        onClick = {
                            showLanguageDialog = false
                            onLanguageSelected("")
                        },
                    )
                    AppLanguageManager.supportedLanguages.forEach { language ->
                        LanguageOption(
                            name = language.nativeName,
                            selected = selectedLanguageTag == language.languageTag,
                            onClick = {
                                showLanguageDialog = false
                                onLanguageSelected(language.languageTag)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LanguageOption(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = selected, role = Role.RadioButton, onValueChange = { onClick() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SettingsAction(
    title: String,
    detail: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MagnetrailMuted)
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
    val stateOn = stringResource(R.string.on)
    val stateOff = stringResource(R.string.off)
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
                stateDescription = if (checked) stateOn else stateOff
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
