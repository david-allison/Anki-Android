// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.view.View
import android.view.ViewGroup
import androidx.core.view.children

/**
 * Removes all children which satisfy [predicate].
 * Errors or runtime exceptions thrown during iteration or by [predicate] are relayed to the caller.

 * @param predicate a predicate which returns `true` for children to be removed.
 */
fun ViewGroup.removeChildren(predicate: (View) -> Boolean) {
    children.filter(predicate).toList().forEach {
        removeView(it)
    }
}
