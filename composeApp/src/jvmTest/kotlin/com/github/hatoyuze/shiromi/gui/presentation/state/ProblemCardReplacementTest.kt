// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.state

import com.github.hatoyuze.shiromi.gui.domain.model.MessageSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [replaceLoadingProblemCard] — the in-place ProblemCard replacement
 * that prevents the "card stuck loading forever" bug (fetch result overwritten by the
 * streaming coroutine's local segments snapshot).
 */
class ProblemCardReplacementTest {

    private fun loadingCard(pid: String) = MessageSegment.ProblemCard(pid = pid, loading = true)

    private fun loadedCard(pid: String) = MessageSegment.ProblemCard(
        pid = pid,
        loading = false,
        data = null,
        error = null,
    )

    @Test
    fun replace_shouldSwapLoadingCardAndKeepOthers() {
        val segments = mutableListOf<MessageSegment>(
            MessageSegment.Text(com.github.hatoyuze.shiromi.gui.domain.model.TextType.CONTENT, "前言"),
            loadingCard("P1001"),
            MessageSegment.Text(com.github.hatoyuze.shiromi.gui.domain.model.TextType.CONTENT, "正文"),
        )
        val updated = loadedCard("P1001")

        assertTrue(replaceLoadingProblemCard(segments, "P1001", updated))

        assertEquals(3, segments.size)
        assertIs<MessageSegment.Text>(segments[0])
        assertEquals(updated, segments[1])
        assertIs<MessageSegment.Text>(segments[2])
    }

    @Test
    fun replace_whenCardAlreadyLoaded_shouldReturnFalse() {
        val segments = mutableListOf<MessageSegment>(
            loadedCard("P1001"),
            loadingCard("P1002"),
        )
        assertFalse(replaceLoadingProblemCard(segments, "P1001", loadedCard("P1001")))
        // The loading P1002 card must remain untouched.
        assertTrue((segments[1] as MessageSegment.ProblemCard).loading)
    }

    @Test
    fun replace_whenPidMissing_shouldReturnFalse() {
        val segments = mutableListOf<MessageSegment>(loadingCard("P1001"))
        assertFalse(replaceLoadingProblemCard(segments, "P9999", loadedCard("P9999")))
        assertEquals(1, segments.size)
        assertTrue((segments[0] as MessageSegment.ProblemCard).loading)
    }

    @Test
    fun replace_shouldMatchFirstMatchingLoadingCardOnly() {
        val segments = mutableListOf<MessageSegment>(
            loadingCard("P1001"),
            loadingCard("P1001"),
        )
        assertTrue(replaceLoadingProblemCard(segments, "P1001", loadedCard("P1001")))
        assertFalse((segments[0] as MessageSegment.ProblemCard).loading)
        assertTrue((segments[1] as MessageSegment.ProblemCard).loading)
    }

    /**
     * Regression test for the original "card stuck loading forever" bug: the streaming
     * coroutine used a LOCAL segments snapshot that overwrote the fetch's state update.
     * With the fix, the stream and the fetch share [ChatJobManager.JobState.segments], so
     * a later stream snapshot must still contain the already-loaded card.
     */
    @Test
    fun streamSnapshotAfterFetchReplace_shouldKeepLoadedCard() {
        val jobState = com.github.hatoyuze.shiromi.gui.presentation.utils.ChatJobManager.JobState()
        // The streaming coroutine appends to jobState.segments (shared list).
        val streamSegments = jobState.segments
        streamSegments.add(
            MessageSegment.Text(com.github.hatoyuze.shiromi.gui.domain.model.TextType.CONTENT, "前言"),
        )
        streamSegments.add(loadingCard("P1001"))

        // fetch completes: in-place replacement on the same shared list.
        assertTrue(replaceLoadingProblemCard(jobState.segments, "P1001", loadedCard("P1001")))

        // Stream continues and takes a full snapshot (as it does on every event).
        streamSegments.add(MessageSegment.Text(com.github.hatoyuze.shiromi.gui.domain.model.TextType.CONTENT, "正文"))
        val snapshot = jobState.segments.toList()

        assertEquals(3, snapshot.size)
        val card = assertIs<MessageSegment.ProblemCard>(snapshot[1])
        assertFalse(card.loading, "fetch result must survive subsequent stream snapshots")
    }
}
