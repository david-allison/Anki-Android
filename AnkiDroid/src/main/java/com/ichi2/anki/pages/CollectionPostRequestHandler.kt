// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 voczi <dev@voczi.com>

package com.ichi2.anki.pages

import com.ichi2.anki.CollectionManager.withCol

/** Runs collection web requests on the application's collection queue. */
suspend fun handleCollectionPostRequest(
    methodName: String,
    bytes: ByteArray,
): ByteArray? = collectionMethods[methodName]?.let { method -> withCol { method.invoke(this, bytes) } }
