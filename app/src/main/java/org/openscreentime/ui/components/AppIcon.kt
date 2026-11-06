package org.openscreentime.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openscreentime.usage.LocalAppIcons

/** An app's icon, or a circle with its first letter if there is no icon. */
@Composable
fun AppIcon(packageName: String, label: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val icons = LocalAppIcons.current
    val icon = remember(packageName, icons) { icons.iconFor(packageName) }
    if (icon != null) {
        // The app's name is read out next to the icon, so the icon itself is decorative.
        Image(bitmap = icon, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
