// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki

import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import anki.collection.OpChanges
import com.ichi2.anki.browser.BrowserActivityHost
import com.ichi2.anki.browser.CardBrowserScreen
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.LastDeckIdRepository
import com.ichi2.anki.browser.SharedPreferencesLastDeckIdRepository
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.scheduling.registerOnForgetHandler
import com.ichi2.anki.browse.R as BrowseR

/** Application host for the browser's navigation drawer and embedded Note Editor. */
open class CardBrowser :
    NavigationDrawerActivity(),
    BrowserActivityHost,
    ChangeManager.Subscriber,
    MenuHost {
    private val screen by lazy { CardBrowserScreen(this) }
    override val activity get() = this
    override val lastDeckIdRepository: LastDeckIdRepository get() = AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository
    val viewModel get() = screen.viewModel
    val cardBrowserFragment get() = screen.cardBrowserFragment
    val useSearchView get() = screen.useSearchView
    val menuHost get() = screen.menuHost

    fun onDeckSelected(deck: SelectableDeck?) = screen.onDeckSelected(deck)

    override var fragmented: Boolean
        get() = screen.fragmented
        set(_) {
            throw UnsupportedOperationException()
        }

    init {
        ChangeManager.subscribe(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) return
        super.onCreate(savedInstanceState)
        screen.onCreate(savedInstanceState)
    }

    override fun setBrowserContent(binding: ViewBinding) = setViewBinding(binding)

    override fun initBrowserNavigation(view: View) = initNavigationDrawer(view)

    override fun startLoadingBrowserCollection() = startLoadingCollection()

    override fun registerForgetHandler(cardIds: suspend () -> List<Long>) {
        registerOnForgetHandler(cardIds)
    }

    override fun onDrawerMenuItemSelected(item: MenuItem) = drawerToggle.onOptionsItemSelected(item)

    override fun isNoteEditor(handler: Any?) = handler is NoteEditorFragment

    override fun restoreBrowserDrawerIcon() = restoreDrawerIcon()

    override fun showBrowserBackIcon() = showBackIcon()

    override fun hideBrowserProgressBar() = hideProgressBar()

    override fun hideNoteEditorActions(menu: Menu) {
        menu.findItem(R.id.action_save)?.isVisible = false
        menu.findItem(R.id.action_preview)?.isVisible = false
    }

    override fun loadNoteEditor(destination: NoteEditorDestination) {
        val editor = fragment
        when {
            editor != null && editor.isEditingSameCards(destination) ->
                if (!editor.hasUnsavedChanges()) editor.reloadNoteFromCollection()
            editor?.hasUnsavedChanges() == true -> showSaveChangesDialog(destination)
            else -> loadNoteEditorFragment(destination)
        }
    }

    override fun setupBackPressedCallbacks() {
        screen.setupBackPressedCallbacks()
        super.setupBackPressedCallbacks()
    }

    override fun fitsSystemWindows() = false

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        registerReceiver()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ) = screen.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)

    override fun onPause() {
        super.onPause()
        screen.onPause()
    }

    override fun onResume() {
        super.onResume()
        selectNavigationItem(R.id.nav_browser)
        screen.onResume()
    }

    override fun onNavigationPressed() {
        if (viewModel.isInMultiSelectMode) {
            viewModel.endMultiSelectMode(CardBrowserViewModel.ChangeMultiSelectMode.SingleSelectCause.NavigateBack)
        } else {
            super.onNavigationPressed()
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        screen.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        screen.onRestoreInstanceState(savedInstanceState)
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) = screen.opExecuted(changes, handler)

    override val shortcuts get() = screen.shortcuts

    private fun showSaveChangesDialog(destination: NoteEditorDestination) {
        DiscardChangesDialog.showDialog(
            context = this,
            positiveButtonText = this.getString(CommonString.save),
            negativeButtonText = this.getString(CommonString.discard),
            // The neutral button allows the user to back out of the action,
            // e.g., if they accidentally triggered a navigation or card selection.
            neutralButtonText = this.getString(CommonString.dialog_cancel),
            message = this.getString(CommonString.save_changes_message),
            positiveMethod = {
                launchCatchingTask {
                    fragment?.saveNote()
                    loadNoteEditorFragment(destination)
                }
            },
            negativeMethod = {
                loadNoteEditorFragment(destination)
            },
            neutralMethod = {},
        )
    }

    private fun loadNoteEditorFragment(destination: NoteEditorDestination) {
        supportFragmentManager.commit {
            replace(BrowseR.id.note_editor_frame, NoteEditorFragment.newInstance(destination))
        }
        // Invalidate options menu so that note editor menu will show
        invalidateOptionsMenu()
    }

    /**
     * Retrieves the `NoteEditor` fragment if it is present in the fragment container
     */
    val fragment: NoteEditorFragment?
        get() = supportFragmentManager.findFragmentById(BrowseR.id.note_editor_frame) as? NoteEditorFragment

    // region MenuHost delegation

    // This only supports delegation of menus defined using MenuProvider, not `onCreateOptionsMenu`
    //
    // When delegating a MenuHost to a fragment, the fragment is attached after `super.onCreate`
    // of the activity.
    //
    // As the activity calls `addMenuProvider` inside `super.onCreate()`, the fragment would not be
    //  initialized
    //
    // Calls to activity.addMenuProvider are done after the fragment is initialized
    // so this delegation works as long as the activity is using `onCreateOptionsMenu`

    override fun addMenuProvider(provider: MenuProvider) {
        menuHost?.addMenuProvider(provider) ?: super.addMenuProvider(provider)
    }

    override fun addMenuProvider(
        provider: MenuProvider,
        owner: LifecycleOwner,
    ) {
        menuHost?.addMenuProvider(provider, owner) ?: super.addMenuProvider(provider, owner)
    }

    override fun addMenuProvider(
        provider: MenuProvider,
        owner: LifecycleOwner,
        state: Lifecycle.State,
    ) {
        menuHost?.addMenuProvider(provider, owner, state) ?: super.addMenuProvider(provider, owner, state)
    }

    override fun removeMenuProvider(provider: MenuProvider) {
        menuHost?.removeMenuProvider(provider) ?: super.removeMenuProvider(provider)
    }

    override fun invalidateMenu() {
        menuHost?.invalidateMenu() ?: super.invalidateMenu()
    }

    override fun invalidateOptionsMenu() {
        super.invalidateOptionsMenu()
        menuHost?.invalidateMenu()
    }

    // endregion

    companion object {
        fun clearLastDeckId() = SharedPreferencesLastDeckIdRepository.clearLastDeckId()
    }
}
