package com.github.hatoyuze.luogu.gui.platform

import okio.FileSystem

/** iOS: okio's POSIX filesystem. */
actual val systemFileSystem: FileSystem = FileSystem.SYSTEM
