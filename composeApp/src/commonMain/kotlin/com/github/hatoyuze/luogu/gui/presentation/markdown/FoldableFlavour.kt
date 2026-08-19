// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.markdown

import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor
import org.intellij.markdown.flavours.gfm.GFMConstraints
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.table.GitHubTableMarkerProvider
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider

class FoldableMarkerProcessor(
    productionHolder: ProductionHolder, constraints: GFMConstraints
) : CommonMarkMarkerProcessor(productionHolder, constraints) {
    private val allProviders: List<MarkerBlockProvider<MarkerProcessor.StateInfo>> =
        super.getMarkerBlockProviders() + listOf(
            GitHubTableMarkerProvider(),
            FoldableMarkerBlockProvider(),
            CuteTableMarkerBlockProvider(),
        )
    override fun getMarkerBlockProviders() = allProviders
    companion object Factory : MarkerProcessorFactory {
        override fun createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor<*> =
            FoldableMarkerProcessor(productionHolder, GFMConstraints.BASE)
    }
}

class FoldableFlavourDescriptor : GFMFlavourDescriptor(useSafeLinks = true, absolutizeAnchorLinks = false) {
    override val markerProcessorFactory: MarkerProcessorFactory get() = FoldableMarkerProcessor.Factory
}
