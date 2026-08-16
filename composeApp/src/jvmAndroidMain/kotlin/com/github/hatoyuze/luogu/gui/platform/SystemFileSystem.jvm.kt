package com.github.hatoyuze.luogu.gui.platform

import okio.FileSystem

/** JVM/Android: okio's default host filesystem (java.io-backed). */
actual val systemFileSystem: FileSystem = FileSystem.SYSTEM
