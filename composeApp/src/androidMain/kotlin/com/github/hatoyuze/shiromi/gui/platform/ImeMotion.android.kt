// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.roundToInt

/**
 * IME 运动引擎（Android）——由 Compose 组合内的 [ImeMotionInsetObserver] 逐帧喂入
 * animated inset（[onInsetFrame]），经 [imeMotionStep] 状态机输出校正值供 Compose 消费。
 *
 * 信号源选型（真机 logcat 实证）：decor 级 `OnApplyWindowInsetsListener` 只收稳定跳变
 * （0→982→0），逐帧动画值只在 Compose 组合内可读。因此引擎**不注册视图监听器**。
 *
 * 状态机（见 ImeMotion.kt）：
 * - DOCK：`min(raw, dockTarget)`（raw 可信时逐帧跟随，轨迹与 HEAD 裸 imePadding 一致）；
 * - FLOAT：沿实测可见键盘曲线独立升起，忽略 raw spike/plateau；
 * - 收起：raw 直随（保持已实证行为）。
 * - 学习：观察者帧时钟在 raw 恒定期间继续补帧，稳定计数不再冻结；
 *   学习高度按 dock/float 分别持久化，换输入法/形态切换不会读到错误 profile 的缓存。
 *
 * 线程模型：所有读写都在主线程（Compose 重组 + snapshot 写入均为主线程）。
 * 约束：单窗口（单 Activity）应用；若未来支持分屏多窗口，需按窗口隔离 engine 状态。
 */
internal object ImeMotionEngine {
    private const val PREFS = "ime_target"
    private const val KEY_DOCK = "keyboard_height_dock_px"
    private const val KEY_FLOAT = "keyboard_height_float_px"
    private const val KEY_MODE = "keyboard_mode"
    private const val KEY_LEGACY = "keyboard_height_px"
    private const val TAG = "ImeTarget"

    private var machine = ImeMotionState.initial(dockTargetPx = 0)

    /** 输出给 Compose 的校正值（px），snapshot state（顶层 actual 读取）。 */
    internal val emittedState = mutableIntStateOf(0)

    /** 本机屏高（px），用于钳制原始 inset 与校验持久化目标。 */
    private var maxRawPx = Int.MAX_VALUE

    /** 高度分类阈值（px）：≤ 该值视为 float。 */
    private var floatMaxPx = Int.MAX_VALUE

    /** 校正后的输入区底部偏移（px），Compose 端读取。 */
    val emittedPx: Int get() = emittedState.intValue

    /** 当前封顶目标（px），诊断/测试用。 */
    val targetPx: Int get() = machine.targetPx

    private var lastPersistedDockPx = 0
    private var lastPersistedFloatPx = 0
    private var lastPersistedMode: ImeMotionMode? = null
    private var lastLogKey = ""

    /**
     * 初始化引擎状态（幂等；Activity 重建时再次调用以复位）。
     * 逐帧输入由 [ImeMotionInsetObserver] 提供。
     *
     * @param decor 仅用于读取屏幕尺寸/密度与种子计算。
     */
    fun install(decor: View) {
        val metrics = decor.resources.displayMetrics
        val screenPx = metrics.heightPixels
        val prefs = AppContextHolder.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val legacy = prefs.getInt(KEY_LEGACY, 0).takeIf { it in 1 until screenPx } ?: 0
        val dock = prefs.getInt(KEY_DOCK, 0).takeIf { it in 1 until screenPx } ?: legacy
        val float = prefs.getInt(KEY_FLOAT, 0).takeIf { it in 1 until screenPx } ?: 0
        val mode = runCatching {
            ImeMotionMode.valueOf(prefs.getString(KEY_MODE, null) ?: "")
        }.getOrNull() ?: ImeMotionMode.UNKNOWN

        val dockTarget = if (dock > 0) dock else seedDockPx(screenPx)
        val floatTarget = if (float > 0) float else seedFloatPx(screenPx)
        maxRawPx = screenPx
        floatMaxPx = (screenPx * IME_FLOAT_MAX_FRACTION).roundToInt()

        machine = ImeMotionState.initial(
            mode = mode,
            dockTargetPx = dockTarget,
            floatTargetPx = floatTarget,
            floatMaxPx = floatMaxPx,
        )
        lastPersistedDockPx = dock
        lastPersistedFloatPx = float
        lastPersistedMode = mode.takeIf { it != ImeMotionMode.UNKNOWN }
        emittedState.intValue = 0
        lastLogKey = ""
    }

    /** 逐帧输入：由组合内 [ImeMotionInsetObserver] 调用（主线程）。 */
    internal fun onInsetFrame(rawPx: Int) {
        val clamped = rawPx.coerceIn(0, maxRawPx)
        // uptimeMillis：单调时钟，避免系统时间跳变污染曲线/平滑时间常数
        machine = imeMotionStep(machine, clamped, SystemClock.uptimeMillis())
        emittedState.intValue = machine.emittedPx
        persistIfNeeded()
        logIfEnabled(clamped)
    }

    /** 学习确认后按 profile 持久化：dock/float 各自独立，互不污染。 */
    private fun persistIfNeeded() {
        val learned = machine.learnedPx
        if (learned <= 0) return
        val prefs = AppContextHolder.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (machine.mode) {
            ImeMotionMode.DOCK -> {
                if (learned != lastPersistedDockPx) {
                    lastPersistedDockPx = learned
                    prefs.edit().putInt(KEY_DOCK, learned).apply()
                }
                persistMode(prefs, ImeMotionMode.DOCK)
            }
            ImeMotionMode.FLOAT -> {
                if (learned != lastPersistedFloatPx) {
                    lastPersistedFloatPx = learned
                    prefs.edit().putInt(KEY_FLOAT, learned).apply()
                }
                persistMode(prefs, ImeMotionMode.FLOAT)
            }
            ImeMotionMode.UNKNOWN -> Unit
        }
    }

    private fun persistMode(prefs: android.content.SharedPreferences, mode: ImeMotionMode) {
        if (lastPersistedMode != mode) {
            lastPersistedMode = mode
            prefs.edit().putString(KEY_MODE, mode.name).apply()
        }
    }

    private fun logIfEnabled(rawPx: Int) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        val key = "${machine.phase} ${machine.mode} $rawPx ${machine.targetPx} " +
            "${machine.learnedPx} ${emittedState.intValue}"
        if (key == lastLogKey) return
        lastLogKey = key
        Log.d(
            TAG,
            "ph=${machine.phase} mode=${machine.mode} raw=$rawPx " +
                "target=${machine.targetPx} dock=${machine.dockTargetPx} " +
                "float=${machine.floatTargetPx} learned=${machine.learnedPx} " +
                "emitted= stable=",
        )
    }

    /** 默认种子 ≈ 0.40 × 屏高：仅覆盖「全新安装首场 dock 呼出」窗口。 */
    private fun seedDockPx(screenPx: Int): Int =
        (screenPx * IME_DOCK_SEED_FRACTION).roundToInt().coerceAtLeast(1)

    /** float 种子 ≈ 0.20 × 屏高（本机实测 569/2772）。 */
    private fun seedFloatPx(screenPx: Int): Int =
        (screenPx * IME_FLOAT_SEED_FRACTION).roundToInt().coerceAtLeast(1)

    /**
     * 窗口失焦（回桌面/切后台/下拉通知栏）：键盘常以无动画方式收起、raw=0 帧可能缺失，
     * 直接清空输出防输入区悬空；若键盘实际仍在（分屏），下一帧 inset 派发会立即恢复。
     */
    fun onWindowFocusLost() {
        emittedState.intValue = 0
    }
}

/** Compose 端读取引擎输出的校正值（px）。 */
internal actual val imeMotionPxState: State<Int> get() = ImeMotionEngine.emittedState

/** 逐帧喂入组合内读到的 animated inset（px）。 */
internal actual fun pushImeBottom(bottomPx: Int) = ImeMotionEngine.onInsetFrame(bottomPx)
