// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * IME 运动引擎状态机测试（[imeMotionStep] 及输出规则）。
 *
 * 数据来源：
 * - dock raw 序列：本机 logcat（46→155→…→982 平滑单调）；
 * - float 可见键盘轨迹：`.scratch/run.mp4` 逐帧像素测量（键盘顶 1090→812，约 540ms）；
 * - float raw 形态：同录屏输入条（旧版跟随 raw）在 ≤41ms 内跳到高位、平台约 500ms 后回落。
 */
class ImeMotionTest {

    private fun state(
        mode: ImeMotionMode,
        dockTarget: Int = 982,
        floatTarget: Int = 569,
        floatMax: Int = 831,
    ): ImeMotionState = ImeMotionState.initial(
        mode = mode,
        dockTargetPx = dockTarget,
        floatTargetPx = floatTarget,
        floatMaxPx = floatMax,
    )

    private fun runSequence(
        initial: ImeMotionState,
        rawSeq: List<Int>,
        frameMs: Long = 16L,
    ): Pair<List<Int>, ImeMotionState> {
        var s = initial
        var t = 0L
        val emitted = mutableListOf<Int>()
        for (raw in rawSeq) {
            t += frameMs
            s = imeMotionStep(s, raw, t)
            emitted += s.emittedPx
        }
        return emitted to s
    }

    private val dockRawLog = listOf(
        46, 155, 284, 409, 521, 616, 695, 758, 809, 848, 879, 903,
        922, 936, 947, 955, 961, 966, 970, 973, 975, 976, 978, 979, 980, 982,
    )

    private val hideRawLog = listOf(
        929, 809, 671, 541, 428, 334, 258, 199, 152, 116, 88, 67, 50, 38, 29, 22,
        16, 12, 9, 7, 5, 4, 3, 2, 1, 0,
    )

    // ── DOCK：跟随 raw、封顶（与 HEAD 裸 imePadding 展开轨迹一致） ──

    @Test
    fun dockRise_followsRawExactly() {
        val (emitted, final) = runSequence(state(ImeMotionMode.DOCK), dockRawLog)
        assertEquals(dockRawLog, emitted, "dock rise must track raw exactly: ")
        assertEquals(982, emitted.last())
        assertEquals(ImeMotionPhase.SETTLED, final.phase)
    }

    @Test
    fun dockOvershoot_peakIsCappedAtTarget() {
        val rawSeq = listOf(46, 800, 1200, 1428, 1300, 1150, 1050, 982) + List(16) { 982 }
        val (emitted, final) = runSequence(state(ImeMotionMode.DOCK), rawSeq)
        assertTrue(1428 !in emitted, "raw overshoot must never reach layout: $emitted")
        assertTrue(emitted.all { it in 0..982 }, "dock emitted must stay <= target: ")
        assertEquals(982, emitted.last())
        assertEquals(ImeMotionPhase.SETTLED, final.phase)
    }

    // ── FLOAT：忽略 raw spike/plateau，沿实测可见键盘曲线升起 ──

    @Test
    fun floatRise_ignoresRawPlateau_andUsesMeasuredCurve() {
        val rawSeq = List(35) { 980 } // 首帧即 spike，随后平台约 544ms
        val (emitted, final) = runSequence(state(ImeMotionMode.FLOAT), rawSeq)
        assertTrue(emitted.first() < 60, "spike must not enter layout on first frame: ${emitted.first()}")
        assertTrue(emitted.all { it <= 569 }, "float rise must never exceed target: $emitted")
        assertEquals(569, emitted.last(), "curve must land exactly on target at 540ms")
        assertEquals(ImeMotionPhase.SETTLED, final.phase)
        assertEquals(ImeMotionMode.FLOAT, final.mode)
        assertTrue(emitted.zipWithNext().all { (a, b) -> b >= a }, "float rise must be monotonic: $emitted")
    }

    @Test
    fun firstFrameSpike_switchesDockToFloatBeforeLayout() {
        val initial = state(ImeMotionMode.DOCK).copy(sawHiddenPx = true)
        val (emitted, final) = runSequence(initial, listOf(980))
        assertEquals(ImeMotionMode.FLOAT, final.mode, "first-frame spike must classify as float")
        assertTrue(emitted.single() < 60, "spike must be ignored before layout: $emitted")
    }

    @Test
    fun firstFrameSmall_switchesFloatToDock() {
        val (_, final) = runSequence(state(ImeMotionMode.FLOAT), listOf(46))
        assertEquals(ImeMotionMode.DOCK, final.mode, "small monotonic first frame must classify as dock")
    }

    @Test
    fun floatSettled_learnsPlausibleFinalHeight() {
        val spike = List(35) { 980 }
        val settled = List(20) { 569 }
        val (emitted, final) = runSequence(state(ImeMotionMode.FLOAT), spike + settled)
        assertEquals(569, final.learnedPx, "stable float height must be learned (frame clock keeps feeding)")
        assertEquals(569, final.floatTargetPx)
        assertEquals(ImeMotionMode.FLOAT, final.mode)
        assertTrue(emitted.last() == 569)
    }

    // ── 学习停滞修复：raw 恒定期间由帧时钟补帧 ──

    @Test
    fun constantRaw_completesLearningWhenPlatformStopsSendingFrames() {
        var s = state(ImeMotionMode.DOCK).copy(
            phase = ImeMotionPhase.SETTLED,
            lastRawPx = 982,
            stableCount = 6, // 本机实测冻结点：平台停发帧时计数停在 6/12
        )
        var t = 1_000L
        repeat(6) {
            t += 16L
            s = imeMotionStep(s, 982, t)
        }
        assertEquals(12, s.stableCount)
        assertEquals(982, s.learnedPx, "constant raw must finish learning without platform frames")
    }

    @Test
    fun stableDockHeight_convergesTarget() {
        val rawSeq = dockRawLog + List(30) { 982 }
        val (_, final) = runSequence(state(ImeMotionMode.DOCK, dockTarget = 900), rawSeq)
        assertEquals(982, final.learnedPx)
        assertEquals(982, final.dockTargetPx, "target must converge to learned value")
    }

    // ── 收起：保持现状，逐帧贴住 raw ──

    @Test
    fun hide_gluesToRaw() {
        val settle = List(14) { 982 }
        val (emitted, final) = runSequence(state(ImeMotionMode.DOCK), dockRawLog + settle + hideRawLog)
        val fallStart = emitted.size - hideRawLog.size
        assertEquals(hideRawLog, emitted.drop(fallStart), "hide must follow raw exactly: ")
        assertEquals(ImeMotionPhase.HIDDEN, final.phase)
        assertEquals(0, final.emittedPx)
        assertEquals(0, final.stableCount)
    }

    
    @Test
    fun fallingDuplicateRaw_keepsPreviousOutput_noBiasSawtooth() {
        var s = state(ImeMotionMode.DOCK).copy(
            phase = ImeMotionPhase.FALLING,
            emittedPx = 96,
            lastRawPx = 96,
            learnedPx = 982,
        )
        s = imeMotionStep(s, rawPx = 96, nowMs = 1000L)
        assertEquals(96, s.emittedPx, "duplicate raw during hide must keep previous output")
        assertEquals(ImeMotionPhase.FALLING, s.phase)
        assertEquals(0, s.stableCount)
    }

    // ── UNKNOWN：标准设备保守跟随 ──

    @Test
    fun unknownSmallFirstFrame_followsRawConservatively() {
        val (emitted, final) = runSequence(state(ImeMotionMode.UNKNOWN), listOf(46, 155, 284))
        assertEquals(listOf(46, 155, 284), emitted)
        assertEquals(ImeMotionMode.DOCK, final.mode)
    }

    // ── resize / 极端输入 ──

    @Test
    fun resize_largerDock_relearnsAndGlides() {
        val rise = dockRawLog
        val settle = List(20) { 982 }
        val grow = listOf(1000, 1050, 1100, 1150, 1200) + List(40) { 1200 }
        val (emitted, final) = runSequence(state(ImeMotionMode.DOCK), rise + settle + grow)
        assertEquals(1200, final.learnedPx)
        assertTrue(emitted.last() <= 1200)
        val growEmitted = emitted.drop(emitted.size - grow.size)
        val jumps = growEmitted.zipWithNext().filter { (a, b) -> b - a > 40 }
        assertTrue(jumps.isEmpty(), "resize must glide, not jump: $growEmitted")
    }

    @Test
    fun extremeRawValues_doNotCorruptMachine() {
        var s = state(ImeMotionMode.DOCK)
        s = imeMotionStep(s, rawPx = -1, nowMs = 16L)
        s = imeMotionStep(s, rawPx = Int.MAX_VALUE, nowMs = 32L)
        s = imeMotionStep(s, rawPx = Int.MAX_VALUE, nowMs = 48L)
        s = imeMotionStep(s, rawPx = Int.MIN_VALUE, nowMs = 64L)
        assertEquals(0, s.learnedPx, "extreme inputs must not be learned")
        assertEquals(982, s.dockTargetPx, "dock target must not be corrupted")
        assertEquals(0, s.emittedPx, "non-positive raw must emit 0")
    }

    @Test
    fun initialState_emitsZero() {
        val s = state(ImeMotionMode.DOCK)
        assertEquals(ImeMotionPhase.HIDDEN, s.phase)
        assertEquals(0, s.emittedPx)
        assertEquals(0, s.learnedPx)
    }

    @Test
    fun floatCurve_isMonotonicAndExactAtEndpoints() {
        assertEquals(0, imeFloatCurveValue(0))
        assertEquals(1000, imeFloatCurveValue(1000))
        val samples = (0..1000 step 20).map { imeFloatCurveValue(it) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a }, "curve must be monotonic: $samples")
    }

    @Test
    fun learnedResetsOnHide_soNextRiseClassificationIsFresh() {
        var s = state(ImeMotionMode.DOCK)
        var t = 0L
        for (raw in dockRawLog + List(20) { 982 } + listOf(0)) {
            t += 16L
            s = imeMotionStep(s, raw, t)
        }
        assertEquals(0, s.learnedPx, "hide must clear the per-session learned value")
        // 第二场呼出：float 首帧 spike 必须仍判 float，旧 learned 不得把 mode 拉回 dock。
        t += 100L
        s = imeMotionStep(s, 980, t)
        assertEquals(ImeMotionMode.FLOAT, s.mode)
        assertTrue(s.emittedPx < 60, "spike must not enter layout: ${s.emittedPx}")
    }

    @Test
    fun firstFrameNearDockTarget_withoutHiddenFrame_staysDockForAlreadyOpenKeyboard() {
        // 组合启动时键盘已在 dock 稳定态（如旋转/进程重建）：首帧≈dockTarget 不是 float spike。
        val (_, final) = runSequence(state(ImeMotionMode.DOCK), listOf(982))
        assertEquals(ImeMotionMode.DOCK, final.mode)
        assertEquals(ImeMotionPhase.SETTLED, final.phase)
    }


    @Test
    fun firstFrameNearFloatTarget_withoutHiddenFrame_skipsFloatAnimation() {
        val (emitted, final) = runSequence(state(ImeMotionMode.FLOAT), listOf(569, 569))
        assertEquals(ImeMotionMode.FLOAT, final.mode)
        assertEquals(ImeMotionPhase.SETTLED, final.phase)
        assertEquals(569, emitted.first(), "already-open float keyboard must not re-animate from 0")
    }
}
