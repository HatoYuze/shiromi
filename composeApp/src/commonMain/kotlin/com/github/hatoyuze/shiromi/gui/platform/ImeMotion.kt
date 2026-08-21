// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * IME 运动引擎 —— 纯状态机（commonMain，无平台依赖，可单测）。
 *
 * ## 核心结论（两轮 logcat + 两轮录屏，不再回退）
 *
 * 1. **raw 不是可见键盘顶的通用代理**。
 *    - dock 态 logcat：raw 平滑单调（46→155→…→982）；HEAD 版裸 `imePadding()`
 *      已实证展开无冲高，因此 dock 直接跟随 raw。
 *    - float 态录屏：raw 在 ≤41ms 内先冲到高位并平台约 500ms，而可见键盘是 1090→812
 *      的平滑减速上升（约 540ms）。直接跟 raw 必然「先冲高再吸附」。
 *
 * 2. 因此升起方向改为**状态感知**：
 *    - DOCK：`emitted = min(raw, dockTarget)`（跟随 + 封顶，展开轨迹与 HEAD 裸 imePadding 一致）；
 *    - FLOAT：`emitted = floatRiseCurve(elapsed) * floatTarget`（完全忽略 raw 的 spike/plateau）；
 *    - UNKNOWN：`min(raw, target)` 保守兜底（标准设备行为零回归）。
 *
 * 3. 收起方向**保持现状**：`emitted = raw`（真机 logcat 已实证逐帧贴住）。
 *
 * [dockTargetPx]/[floatTargetPx] 分 profile 持久化并自校准：稳定检测学习确认后的 raw 高度
 * （raw 恒定期间由帧时钟持续补帧，见 [ImeMotionInsetObserver]）；换输入法/横竖屏后下一次
 * 呼出即用正确高度，不再被另一 profile 的陈旧缓存污染。
 */
internal enum class ImeMotionMode { UNKNOWN, DOCK, FLOAT }

internal enum class ImeMotionPhase { HIDDEN, RISING, SETTLED, FALLING }

internal const val IME_SETTLE_EPS = 2
internal const val IME_SETTLE_FRAMES = 12
internal const val IME_TARGET_SMOOTH_TAU_MS = 100L

/** dock 首帧比例上限：首帧 raw 超过 dockTarget 该比例即判定为 float 的 spike（本机实测 0.047 vs ~1.0）。 */
internal const val IME_FLOAT_FIRST_FRAME_RATIO = 0.40f

/** 首帧 raw 低于 floatTarget/dockTarget 该比例时判定为 dock（本机 dock 首帧 46/982 ≈ 0.047）。 */
internal const val IME_DOCK_FIRST_FRAME_RATIO = 0.25f

/** 种子：dock ≈ 0.40×屏高（沿用历史值）；float ≈ 0.20×屏高（本机实测 569/2772 ≈ 0.205）。 */
internal const val IME_DOCK_SEED_FRACTION = 0.40f
internal const val IME_FLOAT_SEED_FRACTION = 0.20f

/** 高度分类阈值：稳定高度 ≤ 0.30×屏高视为 float（dock 982/2772=0.354，float 569/2772=0.205）。 */
internal const val IME_FLOAT_MAX_FRACTION = 0.30f

/** float 可见键盘升起时长（ms）——旧录屏逐帧实测：键盘顶 1090→812 历时约 539ms。 */
internal const val IME_FLOAT_RISE_MS = 540L

/**
 * float 可见键盘轨迹（归一化，per-mille）。
 * 来源：`.scratch/run.mp4` 逐帧像素测量（非视觉模型）：相对时间 s 与归一化位移 v。
 */
internal val IME_FLOAT_CURVE_S = intArrayOf(0, 232, 308, 386, 463, 541, 617, 694, 768, 846, 1000)
internal val IME_FLOAT_CURVE_V = intArrayOf(0, 475, 630, 784, 838, 896, 914, 921, 928, 950, 1000)

/**
 * 引擎的纯状态。
 *
 * @param mode 当前键盘形态（UNKNOWN 为标准设备保守模式）。
 * @param targetPx 当前 mode 的封顶/终点目标（px，恒 > 0）。
 * @param dockTargetPx dock profile 的自校准目标。
 * @param floatTargetPx float profile 的自校准目标。
 * @param floatMaxPx 高度分类阈值：≤ 该值视为 float（px；引擎按屏高换算）。
 * @param learnedPx 本会话已确认的稳定 raw 高度（0 = 尚未学习到）。
 * @param emittedPx 本帧应输出的校正值（px）——引擎直接写入 Compose。
 * @param riseStartMs 当前升起动画起点（float 曲线时钟）。
 * @param riseBasePx 当前升起动画起点输出（re-rise 时不跳变）。
 */
internal data class ImeMotionState(
    val phase: ImeMotionPhase = ImeMotionPhase.HIDDEN,
    val mode: ImeMotionMode = ImeMotionMode.UNKNOWN,
    val targetPx: Int,
    val dockTargetPx: Int = targetPx,
    val floatTargetPx: Int = targetPx,
    val floatMaxPx: Int = Int.MAX_VALUE,
    val learnedPx: Int = 0,
    val emittedPx: Int = 0,
    val lastRawPx: Int = 0,
    val stableCount: Int = 0,
    val riseStartMs: Long = 0L,
    val riseBasePx: Int = 0,
    val lastStepMs: Long = 0L,
    val sawHiddenPx: Boolean = false,
) {
    companion object {
        fun initial(
            mode: ImeMotionMode = ImeMotionMode.UNKNOWN,
            dockTargetPx: Int,
            floatTargetPx: Int = dockTargetPx,
            floatMaxPx: Int = Int.MAX_VALUE,
        ): ImeMotionState = ImeMotionState(
            mode = mode,
            targetPx = when (mode) {
                ImeMotionMode.FLOAT -> floatTargetPx
                ImeMotionMode.DOCK, ImeMotionMode.UNKNOWN -> dockTargetPx
            },
            dockTargetPx = dockTargetPx,
            floatTargetPx = floatTargetPx,
            floatMaxPx = floatMaxPx,
        )
    }
}

/** 稳定高度分类：≤ [floatMaxPx] 为 float，否则 dock。 */
internal fun imeMotionClassifyHeight(heightPx: Int, floatMaxPx: Int): ImeMotionMode = when {
    heightPx <= 0 -> ImeMotionMode.UNKNOWN
    heightPx <= floatMaxPx -> ImeMotionMode.FLOAT
    else -> ImeMotionMode.DOCK
}

/**
 * 升起首帧分类。只依赖首帧与持久化 profile，保证 spike 在进布局之前就被识别：
 * - 首帧 raw ≥ 0.40×dockTarget → float spike（dock 首帧实测仅 0.047×target）；
 * - 首帧 raw 同时 ≤ 0.25×floatTarget 且 ≤ 0.25×dockTarget → dock；
 * - 否则沿用上次 mode；UNKNOWN 首帧不大时按 dock 保守跟随。
 */
internal fun imeMotionResolveRiseMode(
    previousMode: ImeMotionMode,
    rawPx: Int,
    dockTargetPx: Int,
    floatTargetPx: Int,
    sawHiddenBeforeRise: Boolean,
): ImeMotionMode {
    val dock = max(1, dockTargetPx)
    val float = max(1, floatTargetPx)
    // 组合启动时键盘已处于 dock 稳定态：没有「raw=0 → 首帧 spike」的完整升起过程，
    // 首帧≈dockTarget 应沿用 dock，而不是被 spike 规则误判为 float。
    if (!sawHiddenBeforeRise && previousMode != ImeMotionMode.FLOAT &&
        abs(rawPx.toLong() - dock.toLong()) <= dock * 0.12f
    ) {
        return ImeMotionMode.DOCK
    }
    if (rawPx >= (dock * IME_FLOAT_FIRST_FRAME_RATIO).toInt()) return ImeMotionMode.FLOAT
    if (rawPx <= (float * IME_DOCK_FIRST_FRAME_RATIO).toInt() &&
        rawPx <= (dock * IME_DOCK_FIRST_FRAME_RATIO).toInt()
    ) {
        return ImeMotionMode.DOCK
    }
    return when (previousMode) {
        ImeMotionMode.UNKNOWN -> ImeMotionMode.DOCK
        ImeMotionMode.DOCK, ImeMotionMode.FLOAT -> previousMode
    }
}

/** dock 输出：跟随 raw、封顶 target（与 HEAD 裸 imePadding 展开轨迹一致）。 */
internal fun imeMotionEmitDockPx(rawPx: Int, targetPx: Int): Int =
    if (rawPx <= 0) 0 else minOf(rawPx, targetPx)

/** 通用封顶输出：`min(raw, target)`。 */
internal fun imeMotionEmitPx(rawPx: Int, targetPx: Int): Int =
    if (rawPx <= 0) 0 else minOf(rawPx, targetPx)

/** float 归一化曲线查值（s 为 per-mille 时间进度，v 为 per-mille 位移）。 */
internal fun imeFloatCurveValue(posPermille: Int): Int {
    if (posPermille <= 0) return 0
    if (posPermille >= 1000) return 1000
    var i = 1
    while (i < IME_FLOAT_CURVE_S.size && posPermille > IME_FLOAT_CURVE_S[i]) i++
    val s0 = IME_FLOAT_CURVE_S[i - 1]
    val s1 = IME_FLOAT_CURVE_S[i]
    val v0 = IME_FLOAT_CURVE_V[i - 1]
    val v1 = IME_FLOAT_CURVE_V[i]
    val t = (posPermille - s0).toFloat() / (s1 - s0)
    return (v0 + (v1 - v0) * t).roundToInt()
}

/** float 升起输出：从 [basePx] 沿实测曲线到 [targetPx]（单调、终点精确）。 */
internal fun imeRiseEmitPx(basePx: Int, targetPx: Int, elapsedMs: Long): Int {
    if (elapsedMs <= 0L) return basePx.coerceAtLeast(0)
    if (elapsedMs >= IME_FLOAT_RISE_MS) return targetPx
    val pos = (elapsedMs * 1000L / IME_FLOAT_RISE_MS).toInt().coerceIn(0, 1000)
    val numerator = (targetPx - basePx).toLong() * imeFloatCurveValue(pos)
    val value = basePx + (numerator / 1000.0).roundToInt()
    return value.coerceIn(0, max(basePx, targetPx))
}

/** target 向 learned 指数平滑（resize/换输入法时移动而非跳变），收敛到足够近时直接对齐。 */
internal fun imeSmoothTargetPx(targetPx: Int, learnedPx: Int, dtMs: Long): Int {
    if (targetPx <= 0) return learnedPx.coerceAtLeast(1)
    val alpha = 1.0 - exp(-dtMs.toDouble() / IME_TARGET_SMOOTH_TAU_MS)
    val smoothed = (targetPx + (learnedPx - targetPx) * alpha).roundToInt().coerceAtLeast(1)
    return if (smoothed == targetPx || abs(learnedPx - smoothed) <= IME_SETTLE_EPS) {
        learnedPx
    } else {
        smoothed
    }
}

/**
 * 每帧推进状态机（纯函数），返回含 [ImeMotionState.emittedPx] 的新状态。
 *
 * 帧时钟由组合内 [ImeMotionInsetObserver] 保证：raw 恒定时也持续推入同值，
 * 因此平台停发帧不再冻结 [ImeMotionState.stableCount]。
 */
internal fun imeMotionStep(state: ImeMotionState, rawPx: Int, nowMs: Long): ImeMotionState {
    if (rawPx <= 0) {
        return state.copy(
            phase = ImeMotionPhase.HIDDEN,
            learnedPx = 0, // 每次呼出重新学习；跨会话高度由 dock/float target 保留。
            emittedPx = 0,
            lastRawPx = rawPx,
            stableCount = 0,
            riseStartMs = 0L,
            riseBasePx = 0,
            sawHiddenPx = true,
            lastStepMs = nowMs,
        )
    }

    val dtMs = if (state.lastStepMs > 0L) (nowMs - state.lastStepMs).coerceIn(1L, 100L) else 16L
    val lastRaw = state.lastRawPx

    // 收起过程中同一 raw 重复到达（帧时钟/平台重复派发）：保持上一帧输出，
    // 不要按 SETTLED 的 dock 规则重算输出，否则会出现 raw 与重算值交替的
    // 高频跳动（logcat 实证：旧 bias 版 raw=96 时 emitted 96/44 交替）。
    if (state.phase == ImeMotionPhase.FALLING && rawPx == lastRaw && rawPx < state.targetPx) {
        return state.copy(stableCount = 0, lastStepMs = nowMs)
    }

    val falling = when {
        state.phase == ImeMotionPhase.FALLING -> rawPx < lastRaw
        // float 升起期 raw 从 spike 平台回落到最终高度是预期形态，不是收起。
        state.phase == ImeMotionPhase.RISING && state.mode == ImeMotionMode.FLOAT -> false
        // float 曲线刚结束时 spike 回落到 plausible 最终高度，也不是收起。
        state.phase == ImeMotionPhase.SETTLED && state.mode == ImeMotionMode.FLOAT &&
            lastRaw > state.floatMaxPx && rawPx <= state.floatMaxPx -> false
        state.phase != ImeMotionPhase.HIDDEN -> rawPx < lastRaw
        else -> false
    }
    val enterRise = state.phase == ImeMotionPhase.HIDDEN ||
        (state.phase == ImeMotionPhase.FALLING && rawPx > lastRaw)

    val riseMode = if (enterRise) {
        imeMotionResolveRiseMode(
            state.mode, rawPx, state.dockTargetPx, state.floatTargetPx, state.sawHiddenPx,
        )
    } else {
        state.mode
    }
    // 组合启动时键盘可能已处于稳定态（旋转/进程重建）：此时不是「升起动画」，
    // 直接进入 SETTLED 并立即学习，避免 FLOAT profile 把已就位的键盘重新动画一遍。
    val riseTarget = when (riseMode) {
        ImeMotionMode.FLOAT -> state.floatTargetPx
        ImeMotionMode.DOCK, ImeMotionMode.UNKNOWN -> state.dockTargetPx
    }
    val alreadyOpen = enterRise && !state.sawHiddenPx && riseTarget > 0 &&
        abs(rawPx.toLong() - riseTarget.toLong()) <= riseTarget * 0.12f
    val effectiveRise = enterRise && !alreadyOpen
    val riseStart = if (effectiveRise) nowMs else if (alreadyOpen) 0L else state.riseStartMs
    val riseBase = if (effectiveRise) state.emittedPx else state.riseBasePx

    // 稳定检测：float 的 spike plateau 不可信；只统计当前 mode 认为 plausible 的高度。
    val stable = abs(rawPx.toLong() - lastRaw.toLong()) <= IME_SETTLE_EPS
    val plausible = when (riseMode) {
        ImeMotionMode.FLOAT -> rawPx <= state.floatMaxPx
        ImeMotionMode.DOCK -> rawPx > state.floatMaxPx
        ImeMotionMode.UNKNOWN -> true
    }
    val count = if (!falling && stable && plausible) state.stableCount + 1 else 0
    val learned = if (!falling && plausible && count >= IME_SETTLE_FRAMES) rawPx else state.learnedPx

    // 学习确认后按高度重分类（防首帧分类失误），并只更新对应 profile 的 target。
    var mode = riseMode
    var dockTarget = state.dockTargetPx
    var floatTarget = state.floatTargetPx
    if (learned > 0) {
        mode = imeMotionClassifyHeight(learned, state.floatMaxPx)
        when (mode) {
            ImeMotionMode.DOCK -> dockTarget = imeSmoothTargetPx(dockTarget, learned, dtMs)
            ImeMotionMode.FLOAT -> floatTarget = imeSmoothTargetPx(floatTarget, learned, dtMs)
            ImeMotionMode.UNKNOWN -> Unit
        }
    }

    val target = when (mode) {
        ImeMotionMode.FLOAT -> floatTarget
        ImeMotionMode.DOCK, ImeMotionMode.UNKNOWN -> dockTarget
    }
    val phaseBefore = when {
        alreadyOpen -> ImeMotionPhase.SETTLED
        enterRise -> ImeMotionPhase.RISING
        falling -> ImeMotionPhase.FALLING
        else -> state.phase
    }
    val floatDone = !falling && !alreadyOpen && phaseBefore == ImeMotionPhase.RISING &&
        mode == ImeMotionMode.FLOAT && (nowMs - riseStart) >= IME_FLOAT_RISE_MS
    val dockSettled = !falling && !alreadyOpen && phaseBefore == ImeMotionPhase.RISING &&
        mode != ImeMotionMode.FLOAT && (count >= IME_SETTLE_FRAMES || rawPx >= target - IME_SETTLE_EPS)
    val phase = when {
        alreadyOpen -> ImeMotionPhase.SETTLED
        enterRise -> ImeMotionPhase.RISING
        falling -> ImeMotionPhase.FALLING
        floatDone || dockSettled || state.phase == ImeMotionPhase.SETTLED ||
            (state.phase == ImeMotionPhase.FALLING && count >= IME_SETTLE_FRAMES) -> ImeMotionPhase.SETTLED
        else -> state.phase
    }

    val emitted = when {
        falling -> when {
            rawPx < target -> rawPx // 收起保持现状：贴住 raw。
            mode == ImeMotionMode.DOCK -> imeMotionEmitDockPx(rawPx, dockTarget)
            else -> imeMotionEmitPx(rawPx, target)
        }
        phase == ImeMotionPhase.RISING && mode == ImeMotionMode.FLOAT ->
            imeRiseEmitPx(riseBase, floatTarget, nowMs - riseStart)
        phase == ImeMotionPhase.RISING && mode == ImeMotionMode.DOCK ->
            imeMotionEmitDockPx(rawPx, dockTarget)
        phase == ImeMotionPhase.RISING ->
            imeMotionEmitPx(rawPx, target)
        mode == ImeMotionMode.DOCK ->
            imeMotionEmitDockPx(rawPx, dockTarget)
        else ->
            imeMotionEmitPx(rawPx, target)
    }

    return state.copy(
        phase = phase,
        mode = mode,
        targetPx = target,
        dockTargetPx = dockTarget,
        floatTargetPx = floatTarget,
        learnedPx = learned,
        emittedPx = emitted,
        lastRawPx = rawPx,
        stableCount = count,
        riseStartMs = riseStart,
        riseBasePx = riseBase,
        lastStepMs = nowMs,
    )
}

/**
 * 当前输入区应应用的 IME 底部偏移（px）的 snapshot state。
 *
 * 设计为 `State<Int>` 而非 `@Composable` 函数：消费方（`smoothImePadding`）在**布局阶段**
 * 读取 `.value`——键盘动画期间值逐帧变化时只触发重排（relayout），不重组整个输入区。
 *
 * Android：由 [ImeMotionEngine]（androidMain）输出。桌面/iOS：恒 0。
 */
internal expect val imeMotionPxState: State<Int>

/** 逐帧把组合内读到的 animated `WindowInsets.ime`（底部 px）推给引擎状态机。 */
internal expect fun pushImeBottom(bottomPx: Int)

/**
 * 键盘动画逐帧驱动器：在组合阶段读取 animated inset 并推给引擎状态机。
 *
 * - 独立小组合（无子 UI、无布局）：insets 逐帧变化只重组本观察者自身；
 * - 帧时钟兜底：raw 到达稳定值后平台会停发帧（本机 logcat 实证），用
 *   [withFrameNanos] 持续重推最后观察值，让稳定检测学习在约 200ms 内完成。
 */
@Composable
internal fun ImeMotionInsetObserver() {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val lastBottom = remember { mutableIntStateOf(imeBottom) }
    SideEffect {
        lastBottom.intValue = imeBottom
        // 仅非 active 时由 SideEffect 推 0；active 期间统一由帧时钟每帧推一次，
        // 避免 SideEffect + withFrameNanos 双路推送造成同一 raw 被处理两次。
        if (imeBottom <= 0) pushImeBottom(0)
    }
    val active = imeBottom > 0
    LaunchedEffect(active) {
        while (active) {
            withFrameNanos { }
            pushImeBottom(lastBottom.intValue)
        }
    }
}
