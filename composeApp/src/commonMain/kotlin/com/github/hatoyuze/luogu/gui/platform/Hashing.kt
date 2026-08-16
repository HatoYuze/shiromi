package com.github.hatoyuze.luogu.gui.platform

/** Returns the SHA-256 hex digest of [input]. Used for image cache keys. */
expect fun sha256Hex(input: String): String
