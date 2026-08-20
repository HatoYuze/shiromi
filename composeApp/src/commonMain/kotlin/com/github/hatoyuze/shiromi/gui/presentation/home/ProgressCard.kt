// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.hatoyuze.shiromi.gui.presentation.components.home.HomeCard
import com.github.hatoyuze.shiromi.gui.presentation.components.home.HomeSectionHeader
import com.github.hatoyuze.shiromi.gui.presentation.components.home.StreakRow
import com.github.hatoyuze.shiromi.gui.presentation.components.home.TopicProgressBar
import com.github.hatoyuze.shiromi.gui.presentation.components.icons.AppIcons
import com.github.hatoyuze.shiromi.gui.presentation.state.HomeViewModel

/** 学习进度卡：连续打卡 + 本周专题进度条（对齐设计稿 学习进度 卡）。 */
@Composable
internal fun ProgressCard(
    state: HomeViewModel.HomeUiState,
    onUpdateTopic: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeCard(modifier = modifier) {
        HomeSectionHeader(title = "学习进度", icon = AppIcons.ChartBar)
        Spacer(Modifier.height(14.dp))
        StreakRow(streakDays = state.streakDays)
        Spacer(Modifier.height(14.dp))
        TopicProgressBar(topic = state.studyTopic, onUpdateTopic = onUpdateTopic)
    }
}
