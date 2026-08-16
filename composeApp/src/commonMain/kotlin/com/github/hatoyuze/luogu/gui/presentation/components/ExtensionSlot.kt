package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Layout

/**
 * Placeholder card for future Home page features.
 *
 * Uses [HomeDesignTokens] for consistent styling — no magic numbers.
 * Replace this composable when a new feature module is ready.
 */
@Composable
fun ExtensionSlot(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = HomeDesignTokens.ExtensionMaxWidth),
        shape = HomeDesignTokens.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = HomeDesignTokens.CardBorder,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(HomeDesignTokens.CardContentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    FeatherIcons.Layout,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "更多功能即将推出",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}
