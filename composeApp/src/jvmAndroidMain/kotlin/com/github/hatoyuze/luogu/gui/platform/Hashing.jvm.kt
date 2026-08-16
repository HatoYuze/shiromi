package com.github.hatoyuze.luogu.gui.platform

import java.security.MessageDigest

actual fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(input.encodeToByteArray())
    return hash.joinToString("") { "%02x".format(it) }
}
