// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.browser

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.MainThread
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import anki.collection.OpChanges
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.browse.databinding.ActivityCardBrowserBinding
import com.ichi2.anki.browse.databinding.ActivityCardBrowserSearchviewBinding
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.SingleSelectCause
import com.ichi2.anki.browser.CardBrowserViewModel.NoteEditorCommand
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Initializing
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Searching
import com.ichi2.anki.common.ALL_DECKS_ID
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.common.utils.ext.getLongExtra
import com.ichi2.anki.common.utils.ext.onPermanentDismissal
import com.ichi2.anki.dialogs.SavedBrowserSearchesDialogFragment
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.registerSaveSearchHandler
import com.ichi2.anki.dialogs.registerSavedSearchActionHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.formatCardCount
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.settings.uiConfigPrefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.startup.ensureStorageIsReady
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.addPrepareMenuProvider
import com.ichi2.anki.utils.ext.collectInLifecycle
import com.ichi2.anki.utils.ext.onAllFragmentsLoaded
import com.ichi2.anki.utils.hideKeyboard
import com.ichi2.ui.CardBrowserSearchView
import kotlinx.coroutines.flow.filterNotNull
import timber.log.Timber
import com.ichi2.anki.browse.R as BrowseR

/** Browser UI hosted by the app's navigation activity. */
class CardBrowserScreen(
    private val host: BrowserActivityHost,
) : ChangeManager.Subscriber {
    fun onDeckSelected(deck: SelectableDeck?) {
        with(activity) {
            deck?.let { deck -> viewModel.setSelectedDeck(deck) }
        }
    }

    private val activity get() = host.activity
    val fragmented get() = viewModel.isFragmented

    lateinit var viewModel: CardBrowserViewModel

    private var binding: ActivityCardBrowserBinding? = null

    lateinit var cardBrowserFragment: CardBrowserFragment

    private var actionBarTitle: TextView? = null

    private val searchView: CardBrowserSearchView?
        get() = cardBrowserFragment.legacySearchView

    private val searchItem: MenuItem? get() = cardBrowserFragment.searchItem
    private val mySearchesItem: MenuItem? get() = cardBrowserFragment.mySearchesItem

    // Dev option for Issue 18709
    val useSearchView: Boolean
        get() = BrowserPreferences.devUsingCardBrowserSearchView

    // delegate the menu to the SearchBar in the fragment

    val menuHost: MenuHost?
        get() =
            if (this::cardBrowserFragment.isInitialized && useSearchView) {
                cardBrowserFragment
            } else {
                null
            }

    /**
     * Updates the browser's ui after a user search was saved based on the result of the operation.
     * @param saveSearchResult the result of the save search operation
     * @see registerSaveSearchHandler
     */
    fun updateAfterUserSearchIsSaved(saveSearchResult: SaveSearchResult) {
        with(activity) {
            when (saveSearchResult) {
                SaveSearchResult.ALREADY_EXISTS ->
                    showSnackbar(
                        CommonString.card_browser_list_my_searches_new_search_error_dup,
                        Snackbar.LENGTH_SHORT,
                    )
                SaveSearchResult.SUCCESS -> {
                    searchView!!.setQuery("", false)
                    mySearchesItem!!.isVisible = true
                    showSnackbar(
                        CommonString.card_browser_list_my_searches_successful_save,
                        Snackbar.LENGTH_SHORT,
                    )
                }
            }
        }
    }

    private val multiSelectOnBackPressedCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                Timber.i("back pressed - exiting multiselect")
                viewModel.endMultiSelectMode(SingleSelectCause.NavigateBack)
            }
        }

    @MainThread
    @NeedsTest("search bar is set after selecting a saved search as first action")
    private fun searchForQuery(query: String) {
        with(activity) {
            // setQuery before expand does not set the view's value
            searchItem?.expandActionView()
            searchView?.setQuery(query, submit = true)
        }
    }

    fun onCreate(savedInstanceState: Bundle?) {
        with(activity) {
            enableEdgeToEdge(
                statusBarStyle =
                    if (useSearchView) {
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { BrowserIntegration.current.isNightTheme }
                    } else {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    },
            )
            val contentBinding =
                if (useSearchView) {
                    ActivityCardBrowserSearchviewBinding.inflate(layoutInflater)
                } else {
                    ActivityCardBrowserBinding.inflate(layoutInflater).also { binding = it }
                }
            if (!ensureStorageIsReady()) {
                return
            }

            // Set a simple RESULT_OK. The Reviewer or calling activity will now
            // rely on ChangeManager/OpChanges to detect if a refresh is needed.
            setResult(android.app.Activity.RESULT_OK)

            val launchOptions = intent?.toCardBrowserLaunchOptions() // must be called after super.onCreate()

            host.setBrowserContent(contentBinding)
            host.initBrowserNavigation(findViewById(android.R.id.content))
            applyToolbarInsets()

            /**
             * Check if noteEditorFrame is not null and if its visibility is set to VISIBLE.
             * If both conditions are true, assign true to the variable [fragmented], otherwise assign false.
             * [fragmented] will be true if the view size is large otherwise false
             */
            // TODO: Consider refactoring by storing noteEditorFrame and similar views in a sealed class (e.g., FragmentAccessor).
            val fragmented =
                BrowserPreferences.devIsCardBrowserFragmented &&
                    !useSearchView &&
                    binding?.noteEditorFrame?.visibility == View.VISIBLE
            Timber.i("Using split Browser: %b", fragmented)

            if (fragmented) {
                ResizablePaneManager(
                    parentLayout = requireNotNull(binding?.cardBrowserXlView) { "cardBrowserXlView" },
                    divider = requireNotNull(binding?.cardBrowserResizingDivider) { "cardBrowserResizingDivider" },
                    startPane = requireNotNull(binding?.cardBrowserFrame) { "cardBrowserFrame" },
                    endPane = requireNotNull(binding?.noteEditorFrame) { "noteEditorFrame" },
                    sharedPrefs = uiConfigPrefs(),
                    startPaneWeightKey = PREF_CARD_BROWSER_PANE_WEIGHT,
                    endPaneWeightKey = PREF_NOTE_EDITOR_PANE_WEIGHT,
                )
            } else {
                binding?.noteEditorFrame?.isVisible = false
                binding?.cardBrowserResizingDivider?.isVisible = false
            }

            // must be called once we have an accessible collection
            viewModel = createViewModel(launchOptions, fragmented)
            onPermanentDismissal { viewModel.deleteSavedSelectionFile() }

            cardBrowserFragment = supportFragmentManager.findFragmentById(BrowseR.id.card_browser_frame) as? CardBrowserFragment
                ?: CardBrowserFragment().also { fragment ->
                    supportFragmentManager.commit {
                        replace(BrowseR.id.card_browser_frame, fragment)
                    }
                }

            if (!useSearchView) {
                // initialize the lateinit variables
                // Load reference to action bar title
                actionBarTitle = findViewById(BrowseR.id.toolbar_title)
                // new deck selection is only available when the new search view is not used
                findViewById<LinearLayout>(BrowseR.id.toolbar_content).setOnClickListener { startDeckSelection(skipEmptyDefault = true) }
            }

            host.startLoadingBrowserCollection()

            setupFlows()
            host.registerForgetHandler { viewModel.queryAllSelectedCardIds() }
            registerSaveSearchHandler(viewModel, ::updateAfterUserSearchIsSaved)
            registerDeckSelectedHandler(action = ::onDeckSelected)
            registerFindReplaceHandler { result ->
                launchCatchingTask {
                    val count = viewModel.findAndReplace(result).await()
                    showSnackbar(TR.browsingNotesUpdated(count))
                }
            }
            registerSavedSearchActionHandler { type, searchName ->
                if (searchName == null) return@registerSavedSearchActionHandler
                when (type) {
                    SavedBrowserSearchesDialogFragment.TYPE_SEARCH_SELECTED -> {
                        Timber.d("Selecting saved search selection named: %s", searchName)
                        launchCatchingTask {
                            val search = viewModel.savedSearches().find { it.name == searchName } ?: return@launchCatchingTask
                            Timber.d("OnSelection using search terms: %s", search.query)
                            if (useSearchView) {
                                cardBrowserFragment.searchViewModel.submitSavedSearch(search)
                            } else {
                                searchForQuery(search.query)
                            }
                        }
                    }
                    SavedBrowserSearchesDialogFragment.TYPE_SEARCH_REMOVED -> {
                        Timber.d("Removing saved search named: %s", searchName)
                        launchCatchingTask {
                            val (_, updatedFilters) = viewModel.removeSavedSearch(searchName)
                            if (updatedFilters.isEmpty()) {
                                mySearchesItem!!.isVisible = false
                            }
                        }
                    }
                    else -> error("Unexpected saved search action: $type")
                }
            }
            setupMenuProvider()
        }
    }

    fun setupMenuProvider() {
        with(activity) {
            // the drawerToggle has priority over other menu items
            addMenuProvider(
                object : MenuProvider {
                    override fun onCreateMenu(
                        menu: Menu,
                        menuInflater: MenuInflater,
                    ) {
                        if (!viewModel.isInMultiSelectMode) {
                            host.restoreBrowserDrawerIcon()
                        } else {
                            host.showBrowserBackIcon()
                        }
                    }

                    override fun onMenuItemSelected(menuItem: MenuItem) = host.onDrawerMenuItemSelected(menuItem)
                },
            )

            // add a MenuProvider for multi-select
            addMenuProvider(
                object : MenuProvider {
                    override fun onCreateMenu(
                        menu: Menu,
                        menuInflater: MenuInflater,
                    ) {
                    }

                    override fun onPrepareMenu(menu: Menu) {
                        if (!viewModel.isInMultiSelectMode) return

                        // set the number of selected rows
                        actionBarTitle?.text =
                            String.format(
                                androidx.core.os.ConfigurationCompat
                                    .getLocales(resources.configuration)[0],
                                "%d",
                                viewModel.selectedRowCount(),
                            )
                        findViewById<TextView>(BrowseR.id.deck_name)?.isVisible =
                            !viewModel.hasSelectedAnyRows() && !viewModel.isInMultiSelectMode
                        findViewById<TextView>(BrowseR.id.subtitle)?.isVisible =
                            !viewModel.hasSelectedAnyRows() && !viewModel.isInMultiSelectMode
                    }

                    override fun onMenuItemSelected(menuItem: MenuItem) = false
                },
            )

            // Update the menu for a fragmented state
            // Added last so all changes take priority
            onAllFragmentsLoaded {
                addPrepareMenuProvider { menu ->
                    if (!fragmented) return@addPrepareMenuProvider

                    /** Return the menu item with a particular identifier or `null` */
                    operator fun Menu.get(id: Int): MenuItem? = this.findItem(id)

                    // NoteEditorFragment: Remove save/preview note options if there are no notes
                    if (viewModel.rowCount == 0) {
                        host.hideNoteEditorActions(menu)
                    }

                    menu[BrowseR.id.action_edit_note]?.isVisible = false

                    // TODO: https://github.com/ankidroid/Anki-Android/issues/20206
                    // This blocks a user from previewing all cards
                    menu[BrowseR.id.action_preview_many]?.isVisible = false
                }
            }
        }
    }

    fun setupBackPressedCallbacks() {
        with(activity) {
            onBackPressedDispatcher.addCallback(this, multiSelectOnBackPressedCallback)
        }
    }

    private fun applyToolbarInsets() {
        with(activity) {
            val container = findViewById<View>(BrowseR.id.toolbar_container) ?: return
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                // systemBars (not just statusBars) so a landscape 3-button navigation bar,
                // which is a side inset, is also cleared
                val bars =
                    insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                    )
                view.updatePadding(left = bars.left, top = bars.top, right = bars.right)
                insets
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setupFlows() {
        with(activity) {
            // provides a name for each flow receiver to improve stack traces

            fun onSearchQueryExpanded(searchQueryExpanded: Boolean) {
                Timber.d("query expansion changed: %b", searchQueryExpanded)
                if (searchQueryExpanded) {
                    searchItem?.expandActionView()
                } else {
                    searchItem?.collapseActionView()
                    // invalidate options menu so that disappeared icons would appear again
                    invalidateOptionsMenu()
                }
            }

            fun onSelectedRowsChanged(rows: Set<Any>) = invalidateOptionsMenu()

            fun onFilterQueryChanged(filterQuery: String) {
                // setQuery before expand does not set the view's value
                searchItem?.expandActionView()
                searchView?.setQuery(filterQuery, submit = false)
            }

            fun onDeckIdChanged(deckId: DeckId?) {
                if (useSearchView) return
                launchCatchingTask {
                    findViewById<TextView>(BrowseR.id.deck_name)?.text =
                        when (deckId) {
                            null -> TR.sentenceCase.allDecks
                            ALL_DECKS_ID -> TR.sentenceCase.allDecks
                            else -> withCol { decks.getLegacy(deckId)?.name }
                        }
                }
            }

            fun onMultiSelectModeChanged(modeChange: ChangeMultiSelectMode) {
                if (modeChange.resultedInMultiSelect) {
                    // Turn on Multi-Select Mode so that the user can select multiple cards at once.
                    Timber.d("load multiselect mode")
                    // show title and hide spinner
                    actionBarTitle?.visibility = View.VISIBLE
                    findViewById<TextView>(BrowseR.id.deck_name)?.isVisible = false
                    findViewById<TextView>(BrowseR.id.subtitle)?.isVisible = false
                    multiSelectOnBackPressedCallback.isEnabled = true
                } else {
                    Timber.d("end multiselect mode")
                    findViewById<TextView>(BrowseR.id.deck_name)?.isVisible = true
                    findViewById<TextView>(BrowseR.id.subtitle)?.isVisible = true
                    actionBarTitle?.visibility = View.GONE
                    multiSelectOnBackPressedCallback.isEnabled = false
                }
                // reload the actionbar using the multi-select mode actionbar
                invalidateOptionsMenu()
            }

            fun searchStateChanged(searchState: SearchState) {
                Timber.d("search state: %s", searchState)

                when (searchState) {
                    Initializing -> { }
                    Searching -> {
                        if ("" != viewModel.searchTerms && searchView != null) {
                            searchView!!.setQuery(viewModel.searchTerms, false)
                            searchItem!!.expandActionView()
                        }
                    }
                    is SearchState.Completed -> {
                        // HACK: required now we use MenuProvider for searches
                        // this causes a very brief flicker, as we call `setQuery` to restore the menu state
                        searchView?.post { searchView?.clearFocus() }
                    }
                    is SearchState.Error -> { }
                }
            }

            fun onLastCompletedSearchChanged(search: SearchState.Completed) {
                findViewById<TextView>(BrowseR.id.subtitle)?.text = search.formatCardCount(resources)
            }

            fun onNoteEditorCommand(command: NoteEditorCommand) {
                launchCatchingTask {
                    when (command) {
                        is NoteEditorCommand.LoadInPane -> {
                            binding?.noteEditorFrame?.isVisible = true
                            host.loadNoteEditor(command.destination)
                        }
                        is NoteEditorCommand.LaunchActivity -> {
                            navigate(command.destination)
                        }
                        NoteEditorCommand.HidePane -> {
                            binding?.noteEditorFrame?.isVisible = false
                            invalidateOptionsMenu()
                        }
                    }
                }
            }

            viewModel.flowOfSearchQueryExpanded.collectInLifecycle(::onSearchQueryExpanded)
            viewModel.flowOfSelectedRows.collectInLifecycle(::onSelectedRowsChanged)
            viewModel.flowOfFilterQuery.collectInLifecycle(::onFilterQueryChanged)
            viewModel.flowOfDeckId.collectInLifecycle(::onDeckIdChanged)
            viewModel.flowOfMultiSelectModeChanged.collectInLifecycle(::onMultiSelectModeChanged)
            viewModel.flowOfSearchState.collectInLifecycle(::searchStateChanged)
            viewModel.flowOfLastCompletedSearch.filterNotNull().collectInLifecycle(::onLastCompletedSearchChanged)
            viewModel.flowOfNoteEditorCommand.collectInLifecycle(::onNoteEditorCommand)
        }
    }

    private fun hideKeyboard() {
        with(activity) {
            Timber.d("hideKeyboard()")
            searchView?.let { view ->
                view.clearFocus()
                view.hideKeyboard()
            }
        }
    }

    fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        with(activity) {
            if (cardBrowserFragment.onKeyUp(keyCode, event)) {
                return true
            }

            // This method is called even when the user is typing in the search text field.
            // So we must ensure that all shortcuts uses a modifier.
            // A shortcut without modifier would be triggered while the user types, which is not what we want.
            when (keyCode) {
                KeyEvent.KEYCODE_F -> {
                    if (event.isCtrlPressed) {
                        Timber.i("Ctrl+F - Find notes")
                        searchItem?.expandActionView()
                        return true
                    }
                }
            }
            return false
        }
    }

    fun onPause() {
        with(activity) {
            // If the user entered something into the search, but didn't press "search", clear this.
            // It's confusing if the bar is shown with a query that does not relate to the data on the screen
            viewModel.removeUnsubmittedInput()
        }
    }

    fun onResume() {
        with(activity) {
            searchView?.post {
                this@CardBrowserScreen.hideKeyboard()
            }
            viewModel.refreshColumnsFromPrefs()
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        with(activity) {
            // Save current search terms
            outState.putString("mSearchTerms", viewModel.searchTerms)
        }
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        with(activity) {
            viewModel.onReinit()
            viewModel.setQuery(
                savedInstanceState.getString("mSearchTerms", ""),
                forceRefresh = false,
            )
        }
    }

    private fun forceRefreshSearch(
        useSearchTextValue: Boolean = false,
        feedbackMode: BrowserSearchFeedbackMode = BrowserSearchFeedbackMode.NONE,
    ) {
        if (useSearchTextValue && searchView != null) {
            viewModel.setQuery(searchView!!.query.toString(), feedbackMode = feedbackMode)
        } else {
            viewModel.launchSearchForCards(feedbackMode = feedbackMode)
        }
    }

    private fun refreshBrowserUI(feedbackMode: BrowserSearchFeedbackMode) {
        with(activity) {
            host.hideBrowserProgressBar()
            // reload whole view
            forceRefreshSearch(feedbackMode = feedbackMode)
            viewModel.endMultiSelectMode(SingleSelectCause.Other)
            invalidateOptionsMenu() // maybe the availability of undo changed
        }
    }

    /**
     * Implementation of `by viewModels()` for use in [onCreate]
     *
     * @see showedActivityFailedScreen - we may not have appContext and therefore can't
     * create the ViewModel
     *
     * @param fragmented True if `noteEditorFrame` is non-null (x-large displays)
     */
    private fun createViewModel(
        launchOptions: CardBrowserLaunchOptions?,
        fragmented: Boolean,
    ) = with(activity) {
        ViewModelProvider(
            viewModelStore,
            CardBrowserViewModel.factory(
                lastDeckIdRepository = host.lastDeckIdRepository,
                idsFileDirectory = IdsFile.getDirectory(),
                options = launchOptions,
                isFragmented = fragmented,
                initialDeckId = intent?.getLongExtra(CardBrowserViewModel.EXTRA_DECK_ID),
            ),
            defaultViewModelCreationExtras,
        )[CardBrowserViewModel::class.java]
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        with(activity) {
            if (handler === this || handler === viewModel) {
                return
            }

            if (changes.browserSidebar ||
                changes.browserTable ||
                changes.noteText ||
                changes.card
            ) {
                // We refresh the Browser's own UI
                val feedbackMode =
                    if (host.isNoteEditor(handler)) BrowserSearchFeedbackMode.COUNT_ONLY else BrowserSearchFeedbackMode.NONE
                refreshBrowserUI(feedbackMode)
            }
        }
    }

    val shortcuts
        get() = cardBrowserFragment.shortcuts

    companion object {
        // Keys for saving pane weights in SharedPreferences
        private const val PREF_CARD_BROWSER_PANE_WEIGHT = "cardBrowserPaneWeight"
        private const val PREF_NOTE_EDITOR_PANE_WEIGHT = "cardBrowserNoteEditorPaneWeight"
    }
}
