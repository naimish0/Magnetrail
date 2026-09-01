package com.rameshta.magnetrail.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.rameshta.magnetrail.R
import com.rameshta.magnetrail.ui.theme.LocalMagnetrailSpacing
import com.rameshta.magnetrail.ui.theme.MagnetrailMuted

object LocalPrivacyPolicy {
    const val lastUpdated = "24 August 2026"
    const val externalConfigurationNotice =
        "Developer and publisher: Naimish Gupta. Privacy contact: naimish.app@gmail.com. " +
            "Postal address: Sandi, Hardoi, 241403, Uttar Pradesh. " +
            "Public policy: https://naimish0.github.io/Magnetrail/."

    val sections: List<Pair<String, String>> = listOf(
        "Overview" to
            "Magnetrail is an offline-first puzzle game and does not require an account. Game boards, settings, " +
            "numbered-campaign progress, and Auto Journey history are stored on this device.",
        "Advertising and consent" to
            "The app includes Google Mobile Ads and Google's User Messaging Platform. When advertising is enabled " +
            "and consent permits a request, Google and its advertising partners may receive device or advertising " +
            "identifiers, IP-derived approximate location, app interactions, diagnostics, and other data described " +
            "by Google's policies to deliver, limit, measure, and prevent fraud in ads. If consent is denied, " +
            "unavailable, or unresolved, gameplay continues without waiting for an ad. Rewarded ads are optional, " +
            "have no app daily limit, and grant a hint or eligible level skip only after reward completion. Campaign " +
            "Levels 1–10 do not count toward interstitials; Level 11 creates the first Campaign opportunity, followed " +
            "by each five first-time completions shared across Campaign, normal Infinite, and Auto Journey. Eligible " +
            "interstitials are evaluated after a solved board is recorded and may appear over the Celebration screen. " +
            "There is no daily interstitial limit or general cooldown, but an interstitial is suppressed for 60 seconds " +
            "after a rewarded ad is completed. An App Open ad may appear when Magnetrail returns to the foreground, " +
            "but no more than once per hour and never within 60 seconds of another full-screen dismissal. An unavailable " +
            "or ineligible App Open ad is skipped and never blocks startup or resume.",
        "Local storage and deletion" to
            "Progress, records, settings, ad cadence, consent-managed state, and Auto Journey boards are kept in app " +
            "storage. Cloud backup and device transfer are disabled. Clearing app storage or uninstalling removes " +
            "this local data and resets local Auto Journey uniqueness history. Google and other providers apply " +
            "their own retention and deletion policies to data they process.",
        "Sharing and security" to
            "Magnetrail does not sell an account profile because it has no account system. Data may be processed by " +
            "Google through Mobile Ads and UMP. The Celebration Share button captures the visible game screen in " +
            "temporary app cache and " +
            "opens the Android Sharesheet; Magnetrail does not choose a recipient or upload it. Network cleartext " +
            "traffic is disabled, release configuration fails closed when required production values are absent, " +
            "and stored progress is excluded from Android backup and device transfer.",
        "Children and audience" to
            "The repository expects an owner-reviewed general-audience declaration before a production release. It " +
            "does not currently contain a verified Play Console target-audience declaration. Distribution must not " +
            "begin until the owner configures and verifies that declaration and any age-treatment requirements.",
        "Your choices" to
            "Where required, the privacy form lets you review consent choices. You can choose whether and where to " +
            "share a Celebration screenshot and remove " +
            "local gameplay data by clearing app storage or uninstalling.",
        "Publisher and contact" to externalConfigurationNotice,
    )
}

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalMagnetrailSpacing.current
    val closeDescription = stringResource(R.string.close_privacy_policy)
    val sections = listOf(
        R.string.privacy_overview_heading to R.string.privacy_overview_body,
        R.string.privacy_ads_heading to R.string.privacy_ads_body,
        R.string.privacy_storage_heading to R.string.privacy_storage_body,
        R.string.privacy_sharing_heading to R.string.privacy_sharing_body,
        R.string.privacy_children_heading to R.string.privacy_children_body,
        R.string.privacy_choices_heading to R.string.privacy_choices_body,
        R.string.privacy_contact_heading to R.string.privacy_contact_body,
    )
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenHorizontal),
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = closeDescription },
            ) { Text(stringResource(R.string.back)) }
            Text(
                stringResource(R.string.privacy_policy),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.privacy_last_updated),
                modifier = Modifier.padding(top = spacing.xs),
                style = MaterialTheme.typography.bodyMedium,
                color = MagnetrailMuted,
            )
            sections.forEach { (heading, body) ->
                Text(
                    stringResource(heading),
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.lg).semantics { this.heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(body),
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.xs),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.privacy_provider_info),
                modifier = Modifier.padding(top = spacing.lg, bottom = spacing.screenBottom),
                style = MaterialTheme.typography.bodySmall,
                color = MagnetrailMuted,
            )
        }
    }
}
