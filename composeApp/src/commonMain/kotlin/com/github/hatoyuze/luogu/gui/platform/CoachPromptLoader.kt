// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.platform

import shiromi.composeapp.generated.resources.Res
import kotlinx.coroutines.runBlocking

/**
 * Reads the bundled coach agent system prompt from Compose resources.
 *
 * This is the sole source of truth for the coach prompt in the GUI app.
 * Uses [runBlocking] because [Res.readBytes] is a suspend function and
 * this is called during synchronous app initialization.
 */
fun loadBundledCoachPrompt(): String = runBlocking {
    try {
        Res.readBytes("files/luogu_agent_prompt.txt").decodeToString()
    } catch (e: Exception) {
        // Should never happen — resource is bundled in the app
        ""
    }
}
