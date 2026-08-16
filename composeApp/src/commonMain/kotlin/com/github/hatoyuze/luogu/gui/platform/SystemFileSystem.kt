package com.github.hatoyuze.luogu.gui.platform

import okio.FileSystem

/**
 * The platform's default [FileSystem].
 *
 * `okio.FileSystem.SYSTEM` is only declared in okio's platform source sets
 * (jvm/android + native), not in its common metadata, so it cannot be referenced
 * from commonMain directly. This expect/actual exposes it to shared code.
 */
expect val systemFileSystem: FileSystem
