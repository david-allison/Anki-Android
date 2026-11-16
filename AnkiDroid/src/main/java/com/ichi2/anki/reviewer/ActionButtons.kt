// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import android.view.Menu
import androidx.annotation.IdRes
import androidx.appcompat.view.menu.MenuItemImpl
import com.ichi2.anki.browse.R as BrowseR

class ActionButtons {
    // DEFECT: This should be private - it breaks the law of demeter, but it'll be a large refactoring to get
    // to this point
    val status: ActionButtonStatus = ActionButtonStatus()
    private var menu: Menu? = null

    fun setup(preferences: SharedPreferences) {
        status.setup(preferences)
    }

    /** Sets the order of the Action Buttons in the action bar  */
    fun setCustomButtonsStatus(menu: Menu) {
        status.setCustomButtons(menu)
        this.menu = menu
    }

    fun findMenuItem(
        @IdRes resId: Int,
    ) = menu?.findItem(resId) as? MenuItemImpl

    companion object {
        @IdRes
        val RES_FLAG = BrowseR.id.action_flag

        @IdRes
        val RES_MARK = BrowseR.id.action_mark_card
    }
}
