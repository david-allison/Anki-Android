// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.ichi2.anki.common.destinations.NoteEditorDestination

/** App navigation and Note Editor integration used by the standalone browser screen. */
interface BrowserActivityHost {
    val activity: AppCompatActivity
    val lastDeckIdRepository: LastDeckIdRepository

    fun setBrowserContent(binding: ViewBinding)

    fun initBrowserNavigation(view: View)

    fun startLoadingBrowserCollection()

    fun registerForgetHandler(cardIds: suspend () -> List<Long>)

    fun restoreBrowserDrawerIcon()

    fun showBrowserBackIcon()

    fun onDrawerMenuItemSelected(item: MenuItem): Boolean

    fun loadNoteEditor(destination: NoteEditorDestination)

    fun hideNoteEditorActions(menu: Menu)

    fun isNoteEditor(handler: Any?): Boolean

    fun hideBrowserProgressBar()
}
