package org.openscreentime.ui.usageaccess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openscreentime.R
import org.openscreentime.ui.components.FootnoteText
import org.openscreentime.ui.components.SettingsCard
import org.openscreentime.ui.components.SettingsScaffold
import org.openscreentime.ui.theme.OpenScreenTimeTheme

/**
 * Explains why usage access is needed and sends the user to Settings to
 * turn it on. The app moves on by itself once access is granted.
 */
@Composable
fun UsageAccessScreen(
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(R.string.usage_access_title),
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.usage_access_open_settings))
                }
            }
        },
    ) {
        SettingsCard {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.usage_access_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
                listOf(
                    R.string.usage_access_step_1,
                    R.string.usage_access_step_2,
                    R.string.usage_access_step_3,
                ).forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(text = stringResource(step), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        FootnoteText(stringResource(R.string.usage_access_privacy))
    }
}

@Preview
@Composable
private fun UsageAccessScreenPreview() {
    OpenScreenTimeTheme(dynamicColor = false) { UsageAccessScreen(onOpenSettings = {}, onBack = {}) }
}
