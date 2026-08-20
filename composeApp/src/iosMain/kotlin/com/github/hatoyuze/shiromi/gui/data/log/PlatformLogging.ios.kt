// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.data.log

import com.github.hatoyuze.shiromi.gui.data.Logger
import com.github.hatoyuze.shiromi.gui.platform.dataPath

/** iOS：日志位于 Application Support/shiromi/logs。 */
actual fun platformLogsDirectory(): String = (dataPath / "logs").toString()

/** iOS：使用默认值。 */
actual fun platformLogTunables(): LogTunables = LogTunables(
    maxBytes = FileLogSink.DEFAULT_MAX_BYTES,
    captureAssistantMessages = true,
    maxBodyBytes = Logger.DEFAULT_MAX_BODY_BYTES,
)
