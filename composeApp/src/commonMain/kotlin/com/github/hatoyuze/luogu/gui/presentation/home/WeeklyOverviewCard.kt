package com.github.hatoyuze.luogu.gui.presentation.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.presentation.components.home.HomeCard
import com.github.hatoyuze.luogu.gui.presentation.components.home.HomeSectionHeader
import com.github.hatoyuze.luogu.gui.presentation.components.icons.AppIcons
import com.github.hatoyuze.luogu.gui.presentation.state.HomeViewModel
import com.github.hatoyuze.luogu.gui.platform.currentTimeMillis

/** 一周毫秒数（本周概览的统计窗口）。 */
private val WeekMillis = 7L * 24 * 60 * 60 * 1000

/** 本周概览卡：近 7 天创建的待办完成率（对齐设计稿 本周概览 卡）。 */
@Composable
internal fun WeeklyOverviewCard(
    state: HomeViewModel.HomeUiState,
    modifier: Modifier = Modifier,
) {
    // 仅统计近 7 天创建的待办，与卡片标题「本周」保持一致
    val weekTodos = remember(state.todos) {
        val weekAgo = currentTimeMillis() - WeekMillis
        state.todos.filter { it.createdAt >= weekAgo }
    }
    val total = weekTodos.size
    val done = weekTodos.count { it.completed }
    val progress = if (total > 0) done.toFloat() / total else 0f

    HomeCard(modifier = modifier) {
        HomeSectionHeader(title = "本周概览", icon = AppIcons.CalendarIcon)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "本周待办完成率",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "$done / $total",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(7.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (total == 0) "本周暂无待办"
            else if (done >= total) "全部完成，太棒了！"
            else "距离目标还差 ${total - done} 项",
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}
