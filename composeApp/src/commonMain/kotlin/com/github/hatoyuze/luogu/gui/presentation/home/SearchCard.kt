// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.hatoyuze.luogu.gui.presentation.components.home.HomeCard
import com.github.hatoyuze.luogu.gui.presentation.components.home.SearchInputBar
import com.github.hatoyuze.luogu.gui.presentation.utils.normalizeProblemId
import kotlinx.coroutines.delay

/**
 * 搜索卡（对齐设计稿 inputbar 卡）：圆角输入条 + 圆形主色发送钮，
 * Enter 或按钮提交题目编号；非法输入显示错误提示（15s 自动清除）。
 */
@Composable
internal fun SearchCard(
    onSearchProblem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf(false) }
    // 错误生成序号：连续非法提交也能重启 15s 自动清除计时
    var searchErrorTick by remember { mutableStateOf(0) }

    fun submit() {
        val pid = normalizeProblemId(searchQuery)
        if (pid == null) {
            searchError = true
            searchErrorTick++
        } else {
            onSearchProblem(pid)
            searchQuery = ""
        }
    }

    HomeCard(modifier = modifier, contentPadding = 10.dp) {
        SearchInputBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it; searchError = false },
            onSubmit = ::submit,
            isError = searchError,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    LaunchedEffect(searchErrorTick) {
        if (searchError) {
            delay(15_000)
            searchError = false
        }
    }
}
