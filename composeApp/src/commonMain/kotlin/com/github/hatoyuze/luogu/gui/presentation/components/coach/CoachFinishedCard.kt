// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.components.coach

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.MessageSegment

// ═══════════════════════════════════════════════════════════
// CoachFinishedCard — session wrap-up summary card
// ═══════════════════════════════════════════════════════════
//
// Shows ONLY student-facing content:
//  - `difficultySummary`: agent-written difficulty summary (new protocol field)
//  - recommend pills (clickable → opens the problem detail page)
//  - `content`: farewell message
//
// The memory-system `summary` (scores, stuck-point postmortems, level history)
// is intentionally NOT rendered — it is agent-internal background.

@Composable
fun CoachFinishedCard(
    card: MessageSegment.CoachFinished,
    onOpenProblem: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val pad = if (compact) 12.dp else 16.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        color = colorScheme.primary.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.2f)),
    ) {
        Column(modifier = Modifier.padding(pad)) {
            // ── Header ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🎉 本次辅导总结",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = if (compact) 14.sp else 15.sp),
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                )
            }

            // ── 难点总结 (student-facing, agent-written) ──
            if (card.difficultySummary.isNotBlank()) {
                Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                SectionLabel("难点总结", compact)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = card.difficultySummary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (compact) 13.sp else 14.sp),
                    lineHeight = if (compact) 20.sp else 22.sp,
                    color = colorScheme.onSurface,
                )
            }

            // ── 推荐巩固练习 (clickable pills) ──
            if (card.recommend.isNotEmpty()) {
                Spacer(Modifier.height(if (compact) 10.dp else 12.dp))
                SectionLabel("推荐巩固练习", compact)
                Spacer(Modifier.height(6.dp))
                RecommendPills(card.recommend, onOpenProblem, compact)
            }

            // ── 告别语 ──
            if (card.content.isNotBlank()) {
                Spacer(Modifier.height(if (compact) 10.dp else 12.dp))
                HorizontalDivider(
                    color = colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.padding(vertical = if (compact) 4.dp else 6.dp),
                )
                Text(
                    text = card.content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (compact) 13.sp else 14.sp),
                    lineHeight = if (compact) 20.sp else 22.sp,
                    color = colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, compact: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        fontSize = if (compact) 12.sp else 13.sp,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendPills(
    pids: List<String>,
    onOpenProblem: ((String) -> Unit)?,
    compact: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        pids.forEach { pid ->
            Surface(
                shape = RoundedCornerShape(if (compact) 8.dp else 10.dp),
                color = colorScheme.primary.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.clickable(enabled = onOpenProblem != null) {
                    onOpenProblem?.invoke(pid)
                },
            ) {
                Text(
                    text = pid,
                    modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 4.dp else 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    fontSize = if (compact) 12.sp else 13.sp,
                )
            }
        }
    }
}
