// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 voczi <dev@voczi.com>

package com.ichi2.anki.pages

interface PostRequestHandler {
    suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray
}

@JvmInline
value class PostRequestUri(
    val uri: String,
) {
    val ankidroidMethodName: String?
        get() =
            if (uri.startsWith(ANKIDROID_PREFIX)) {
                uri.substring(ANKIDROID_PREFIX.length)
            } else {
                null
            }

    val backendMethodName: String?
        get() =
            if (uri.startsWith(ANKI_PREFIX)) {
                uri.substring(ANKI_PREFIX.length)
            } else {
                null
            }

    val jsApiMethodName: String?
        get() =
            if (uri.startsWith(ANKIDROID_JS_PREFIX)) {
                uri.substring(ANKIDROID_JS_PREFIX.length)
            } else {
                null
            }

    override fun toString() = uri

    companion object {
        /** Common prefix used on Anki requests */
        const val ANKI_PREFIX = "/_anki/"
        const val ANKIDROID_PREFIX = "/ankidroid/"
        const val ANKIDROID_JS_PREFIX = "/jsapi/"
    }
}
