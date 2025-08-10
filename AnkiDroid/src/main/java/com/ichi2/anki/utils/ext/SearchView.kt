/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.utils.ext

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.google.android.material.search.SearchView

/**
 * Performs an action when 'enter' is pressed on a keyboard
 *
 * @param block The action to invoke
 */
fun SearchView.onSubmit(block: TextView.OnEditorActionListener) {
    block
    editText.setOnEditorActionListener { v, actionId, event ->
        if (actionId != EditorInfo.IME_ACTION_SEARCH &&
            actionId != EditorInfo.IME_ACTION_DONE &&
            !(event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
        ) {
            return@setOnEditorActionListener false
        }
        return@setOnEditorActionListener block.onEditorAction(v, actionId, event)
    }
}
