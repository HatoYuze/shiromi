package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

/**
 * Shared design tokens for Home page card components.
 *
 * All cards use the same shape, border, and spacing to maintain
 * visual consistency across CalendarPanel, DateDisplayBlock,
 * DailyProblemCard, and ExtensionSlot.
 */
object HomeDesignTokens {
    /** Standard card corner radius — matches DateDisplayBlock */
    val CardShape = RoundedCornerShape(16.dp)

    /** Flat border — no shadows, subtle outline separation */
    val CardBorder
        @androidx.compose.runtime.Composable
        get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

    /** Inner padding for card content */
    val CardContentPadding = 20.dp

    /** Horizontal gap between cards in a row */
    val RowSpacing = 16.dp

    /** Vertical gap between major sections */
    val SectionSpacing = 20.dp

    /** Maximum content width for the main scrollable area */
    val ContentMaxWidth = 800.dp

    /** Calendar panel max width — 7 columns × ~54dp each */
    val CalendarMaxWidth = 380.dp

    /** Date display max width — 96sp number + padding */
    val DateDisplayMaxWidth = 280.dp

    /** Search bar max width — slightly narrower than calendar row total */
    val SearchBarMaxWidth = 500.dp

    /** Daily problem card max width */
    val DailyProblemMaxWidth = 350.dp

    /** Extension slot max width — symmetric with DailyProblemCard */
    val ExtensionMaxWidth = 350.dp

    /** Bottom bar horizontal padding */
    val BottomBarHPadding = 48.dp

    /** Bottom bar vertical padding */
    val BottomBarVPadding = 16.dp
}
