/****************************************************************************************
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation.Direction
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.browser.BrowserRowCollection
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.browser.CardBrowserLaunchOptions
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.SingleSelectCause
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Initializing
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Searching
import com.ichi2.anki.browser.CardOrNoteId
import com.ichi2.anki.browser.FindAndReplaceDialogFragment
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.browser.Mode
import com.ichi2.anki.browser.RepositionCardFragment
import com.ichi2.anki.browser.RepositionCardFragment.Companion.REQUEST_REPOSITION_NEW_CARDS
import com.ichi2.anki.browser.RepositionCardsRequest.ContainsNonNewCardsError
import com.ichi2.anki.browser.RepositionCardsRequest.RepositionData
import com.ichi2.anki.browser.SaveSearchResult
import com.ichi2.anki.browser.SharedPreferencesLastDeckIdRepository
import com.ichi2.anki.browser.registerFindReplaceHandler
import com.ichi2.anki.browser.setup
import com.ichi2.anki.browser.toCardBrowserLaunchOptions
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.dialogs.BrowserOptionsDialog
import com.ichi2.anki.dialogs.CardBrowserMySearchesDialog
import com.ichi2.anki.dialogs.CardBrowserMySearchesDialog.Companion.newInstance
import com.ichi2.anki.dialogs.CardBrowserMySearchesDialog.MySearchesDialogListener
import com.ichi2.anki.dialogs.CardBrowserOrderDialog
import com.ichi2.anki.dialogs.CreateDeckDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.Companion.newInstance
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.dialogs.GradeNowDialog
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.SortOrder
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.anki.model.SortType
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.scheduling.registerOnForgetHandler
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.anki.utils.ext.getCurrentDialogFragment
import com.ichi2.anki.utils.ext.ifNotZero
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.widgets.DeckDropDownAdapter
import com.ichi2.ui.CardBrowserSearchView
import com.ichi2.anki.widgets.DeckDropDownAdapter.SubtitleListener
import com.ichi2.themes.setTransparentStatusBar
import com.ichi2.utils.TagsUtil.getUpdatedTags
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import com.ichi2.widget.WidgetStatus.updateInBackground
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.Translations
import timber.log.Timber

@Suppress("LeakingThis")
// The class is only 'open' due to testing
@KotlinCleanup("scan through this class and add attributes - in process")
open class CardBrowser :
    NavigationDrawerActivity(),
    DeckDropDownAdapter.SubtitleProvider,
    DeckSelectionListener,
    TagsDialogListener,
    ChangeManager.Subscriber {
    /**
     * Provides an instance of NoteEditorLauncher for adding a note
     */
    @get:VisibleForTesting
    val addNoteLauncher: NoteEditorLauncher
        get() = createAddNoteLauncher(viewModel, fragmented)

    /**
     * Provides an instance of NoteEditorLauncher for editing a note
     */
    private val editNoteLauncher: NoteEditorLauncher
        get() =
            NoteEditorLauncher.EditCard(viewModel.currentCardId, Direction.DEFAULT, fragmented).also {
                Timber.i("editNoteLauncher: %s", it)
            }

    override fun onDeckSelected(deck: SelectableDeck?) {
        deck?.let {
            launchCatchingTask {
                viewModel.setDeckId(deck.deckId)
            }
        }
    }

    override var fragmented: Boolean
        get() = viewModel.isFragmented
        set(value) {
            throw UnsupportedOperationException()
        }

    private enum class TagsDialogListenerAction {
        FILTER,
        EDIT_TAGS,
    }

    lateinit var viewModel: CardBrowserViewModel

    /**
     * The frame containing the NoteEditor. Non null only in layout x-large.
     */
    private var noteEditorFrame: FragmentContainerView? = null

    private lateinit var tagsDialogFactory: TagsDialogFactory
    private var undoSnackbar: Snackbar? = null

    // card that was clicked (not marked)
    override var currentCardId
        get() = viewModel.currentCardId
        set(value) {
            viewModel.currentCardId = value
        }

    // DEFECT: Doesn't need to be a local
    private var tagsDialogListenerAction: TagsDialogListenerAction? = null

    private var onEditCardActivityResult =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            Timber.i("onEditCardActivityResult: resultCode=%d", result.resultCode)
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR)
                return@registerForActivityResult
            }

            // handle template edits

            // in use by reviewer?
            result.data?.let {
                if (
                    it.getBooleanExtra(NoteEditorFragment.RELOAD_REQUIRED_EXTRA_KEY, false) ||
                    it.getBooleanExtra(NoteEditorFragment.NOTE_CHANGED_EXTRA_KEY, false)
                ) {
                    if (reviewerCardId == currentCardId) {
                        reloadRequired = true
                    }
                }
            }

            invalidateOptionsMenu() // maybe the availability of undo changed

            // handle card edits
            if (result.resultCode == RESULT_OK) {
                viewModel.onCurrentNoteEdited()
            }
        }
    private var onAddNoteActivityResult =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            Timber.d("onAddNoteActivityResult: resultCode=%d", result.resultCode)
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR)
            }
            if (result.resultCode == RESULT_OK) {
                forceRefreshSearch()
            }
            invalidateOptionsMenu() // maybe the availability of undo changed
        }
    private var onPreviewCardsActivityResult =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            Timber.d("onPreviewCardsActivityResult: resultCode=%d", result.resultCode)
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR)
            }
            // Previewing can now perform an "edit", so it can pass on a reloadRequired
            val data = result.data
            if (data != null &&
                (
                    data.getBooleanExtra(NoteEditorFragment.RELOAD_REQUIRED_EXTRA_KEY, false) ||
                        data.getBooleanExtra(NoteEditorFragment.NOTE_CHANGED_EXTRA_KEY, false)
                )
            ) {
                forceRefreshSearch()
                if (reviewerCardId == currentCardId) {
                    reloadRequired = true
                }
            }
            invalidateOptionsMenu() // maybe the availability of undo changed
        }

    // TODO: Remove this and use `opChanges`
    private var reloadRequired = false

    init {
        ChangeManager.subscribe(this)
    }

    @VisibleForTesting
    internal val mySearchesDialogListener: MySearchesDialogListener =
        object : MySearchesDialogListener {
            override fun onSelection(searchName: String) {
                Timber.d("OnSelection using search named: %s", searchName)
                launchCatchingTask {
                    viewModel.savedSearches()[searchName]?.also { searchQuery ->
                        Timber.d("OnSelection using search terms: %s", searchQuery)
                        viewModel.launchSearchForCards(searchQuery)
                    }
                }
            }

            override fun onRemoveSearch(searchName: String) {
                Timber.d("OnRemoveSelection using search named: %s", searchName)
                launchCatchingTask {
                    viewModel.removeSavedSearch(searchName)
                }
            }

            override fun onSaveSearch(
                searchName: String,
                searchTerms: String?,
            ) {
                if (searchTerms == null) {
                    return
                }
                if (searchName.isEmpty()) {
                    showSnackbar(
                        R.string.card_browser_list_my_searches_new_search_error_empty_name,
                        Snackbar.LENGTH_SHORT,
                    )
                    return
                }
                launchCatchingTask {
                    when (viewModel.saveSearch(searchName, searchTerms)) {
                        SaveSearchResult.ALREADY_EXISTS ->
                            showSnackbar(
                                R.string.card_browser_list_my_searches_new_search_error_dup,
                                Snackbar.LENGTH_SHORT,
                            )
                        SaveSearchResult.SUCCESS -> { }
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

    /**
     * Change Deck
     * @param did Id of the deck
     */
    @VisibleForTesting
    fun moveSelectedCardsToDeck(did: DeckId): Job =
        launchCatchingTask {
            val changed = withProgress { viewModel.moveSelectedCardsToDeck(did).await() }
            showUndoSnackbar(TR.browsingCardsUpdated(changed.count))
        }

    @Suppress("deprecation") // STOPSHIP
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        tagsDialogFactory = TagsDialogFactory(this).attachToActivity<TagsDialogFactory>(this)
        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }

        // set the default result
        setResult(
            RESULT_OK,
            Intent().apply {
                // Add reload flag to result intent so that schedule reset when returning to note editor
                putExtra(NoteEditorFragment.RELOAD_REQUIRED_EXTRA_KEY, reloadRequired)
            },
        )

        val launchOptions = intent?.toCardBrowserLaunchOptions() // must be called after super.onCreate()

        setContentView(R.layout.card_browser)
        initNavigationDrawer(findViewById(android.R.id.content))

        setTransparentStatusBar()
        drawerLayout.setStatusBarBackgroundColor(Color.TRANSPARENT)

        noteEditorFrame = findViewById(R.id.note_editor_frame)

        /**
         * Check if noteEditorFrame is not null and if its visibility is set to VISIBLE.
         * If both conditions are true, assign true to the variable [fragmented], otherwise assign false.
         * [fragmented] will be true if the view size is large otherwise false
         */
        // TODO: Consider refactoring by storing noteEditorFrame and similar views in a sealed class (e.g., FragmentAccessor).
        val fragmented =
            Prefs.devIsCardBrowserFragmented &&
                noteEditorFrame?.visibility == View.VISIBLE
        Timber.i("Using split Browser: %b", fragmented)

        if (fragmented) {
            val parentLayout = findViewById<LinearLayout>(R.id.card_browser_xl_view)
            val divider = findViewById<View>(R.id.card_browser_resizing_divider)
            val leftPane = findViewById<View>(R.id.card_browser_frame)
            val rightPane = findViewById<View>(R.id.note_editor_frame)
            if (parentLayout != null && divider != null && leftPane != null && rightPane != null) {
                ResizablePaneManager(
                    parentLayout = parentLayout,
                    divider = divider,
                    leftPane = leftPane,
                    rightPane = rightPane,
                    sharedPrefs = Prefs.getUiConfig(this),
                    leftPaneWeightKey = PREF_CARD_BROWSER_PANE_WEIGHT,
                    rightPaneWeightKey = PREF_NOTE_EDITOR_PANE_WEIGHT,
                )
            }
        }

        // must be called once we have an accessible collection
        viewModel = createViewModel(launchOptions, fragmented)

        if (supportFragmentManager.findFragmentById(R.id.card_browser_frame) == null) {
            supportFragmentManager.commit {
                replace(R.id.card_browser_frame, CardBrowserFragment())
            }
        }

        startLoadingCollection()

        // Selected cards aren't restored on activity recreation,
        // so it is necessary to dismiss the change deck dialog
        getCurrentDialogFragment<DeckSelectionDialog>()?.let { dialogFragment ->
            if (dialogFragment.requireArguments().getBoolean(CHANGE_DECK_KEY, false)) {
                Timber.d("onCreate(): Change deck dialog dismissed")
                dialogFragment.dismiss()
            }
        }

        setupFlows()
        registerOnForgetHandler { viewModel.queryAllSelectedCardIds() }
        setFragmentResultListener(REQUEST_REPOSITION_NEW_CARDS) { _, bundle ->
            repositionCardsNoValidation(
                position = bundle.getInt(RepositionCardFragment.ARG_POSITION),
                step = bundle.getInt(RepositionCardFragment.ARG_STEP),
                shuffle = bundle.getBoolean(RepositionCardFragment.ARG_RANDOM),
                shift = bundle.getBoolean(RepositionCardFragment.ARG_SHIFT),
            )
        }

        registerFindReplaceHandler { result ->
            launchCatchingTask {
                withProgress {
                    val count =
                        withProgress {
                            viewModel.findAndReplace(result)
                        }.await()
                    showSnackbar(TR.browsingNotesUpdated(count))
                }
            }
        }
    }

    override fun setupBackPressedCallbacks() {
        onBackPressedDispatcher.addCallback(this, multiSelectOnBackPressedCallback)
        super.setupBackPressedCallbacks()
    }

    private fun showSaveChangesDialog(launcher: NoteEditorLauncher) {
        DiscardChangesDialog.showDialog(
            context = this,
            positiveButtonText = this.getString(R.string.save),
            negativeButtonText = this.getString(R.string.discard),
            // The neutral button allows the user to back out of the action,
            // e.g., if they accidentally triggered a navigation or card selection.
            neutralButtonText = this.getString(R.string.dialog_cancel),
            message = this.getString(R.string.save_changes_message),
            positiveMethod = {
                launchCatchingTask {
                    fragment?.saveNote()
                    loadNoteEditorFragment(launcher)
                }
            },
            negativeMethod = {
                loadNoteEditorFragment(launcher)
            },
            neutralMethod = {},
        )
    }

    private fun loadNoteEditorFragment(launcher: NoteEditorLauncher) {
        val noteEditorFragment = NoteEditorFragment.newInstance(launcher)
        supportFragmentManager.commit {
            replace(R.id.note_editor_frame, noteEditorFragment)
        }
        // Invalidate options menu so that note editor menu will show
        invalidateOptionsMenu()
    }

    /**
     * Retrieves the `NoteEditor` fragment if it is present in the fragment container
     */
    val fragment: NoteEditorFragment?
        get() = supportFragmentManager.findFragmentById(R.id.note_editor_frame) as? NoteEditorFragment

    /**
     * Loads the NoteEditor fragment in container if the view is x-large.
     */
    fun loadNoteEditorFragmentIfFragmented() {
        require(fragmented)

        noteEditorFrame!!.isVisible = true

        // If there are unsaved changes in NoteEditor then show dialog for confirmation
        if (fragment?.hasUnsavedChanges() == true) {
            showSaveChangesDialog(editNoteLauncher)
        } else {
            loadNoteEditorFragment(editNoteLauncher)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setupFlows() {
        // provides a name for each flow receiver to improve stack traces

        suspend fun onDeckIdChanged(deckId: DeckId?) {
            if (deckId == null) return
            // this handles ALL_DECKS_ID
            Timber.e("TOOD")
        }

        fun isInMultiSelectModeChanged(inMultiSelect: Boolean) {
            multiSelectOnBackPressedCallback.isEnabled = inMultiSelect
            // reload the actionbar using the multi-select mode actionbar
            invalidateOptionsMenu()
        }

        fun searchStateChanged(searchState: SearchState) {
            Timber.d("search state: %s", searchState)

            when (searchState) {
                Initializing -> { }
                Searching -> { }
                SearchState.Completed -> {
                    redrawAfterSearch()
                    // TODO: move to ViewModel
                    launchCatchingTask {
                        viewModel.currentCardId =
                            (
                                viewModel.focusedRow
                                    ?: viewModel.cards[0]
                            ).toCardId(viewModel.cardsOrNotes)
                    }
                }
                is SearchState.Error -> {
                    showError(searchState.error, crashReportData = null)
                }
            }
        }

        fun onSelectedCardUpdated(unit: Unit) {
            if (fragmented) {
                // TODO: this shouldn't be needed
                noteEditorFrame?.isVisible = true
                loadNoteEditorFragmentIfFragmented()
            } else {
                onEditCardActivityResult.launch(editNoteLauncher.toIntent(this))
            }
        }

        /** [menuState] is used inside [onCreateOptionsMenu] */
        fun onMenuChanged(menuState: CardBrowserViewModel.MenuState) {
            invalidateOptionsMenu()
        }

        fun onNoteEditorVisibleChanged(isVisible: Boolean) {
            noteEditorFrame?.isVisible = isVisible
            if (isVisible) {
                loadNoteEditorFragmentIfFragmented()
            }
            invalidateOptionsMenu()
        }

        viewModel.flowOfDeckId.launchCollectionInLifecycleScope(::onDeckIdChanged)
        viewModel.flowOfCanSearch.launchCollectionInLifecycleScope(::onCanSaveChanged)
        viewModel.flowOfMultiSelectModeChanged.launchCollectionInLifecycleScope(::onMultiSelectModeChanged)
        viewModel.flowOfIsInMultiSelectMode.launchCollectionInLifecycleScope(::isInMultiSelectModeChanged)
        viewModel.flowOfSearchState.launchCollectionInLifecycleScope(::searchStateChanged)
        viewModel.cardSelectionEventFlow.launchCollectionInLifecycleScope(::onSelectedCardUpdated)
        viewModel.flowOfNoteEditorVisible.launchCollectionInLifecycleScope(::onNoteEditorVisibleChanged)
        // TODO: This is called too much
        viewModel.flowOfStandardMenuState.launchCollectionInLifecycleScope(::onMenuChanged)
        viewModel.flowOfMultiSelectMenuState.launchCollectionInLifecycleScope(::onMenuChanged)
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        Timber.d("onCollectionLoaded()")
        registerReceiver()

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (currentFocus is EditText || viewModel.flowOfSearchViewExpanded.value) {
            return super.onKeyUp(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+A - Show edit tags dialog")
                    showEditTagsDialog()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+A - Select All")
                    viewModel.selectAll()
                    return true
                }
            }
            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+E: Export selected cards")
                    exportSelected()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+E: Add Note")
                    launchCatchingTask { addNoteFromCardBrowser() }
                    return true
                } else {
                    Timber.i("E: Edit note")
                    // search box is not available so treat the event as a shortcut
                    openNoteEditorForCurrentlySelectedNote()
                    return true
                }
            }
            KeyEvent.KEYCODE_D -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+D: Change Deck")
                    showChangeDeckDialog()
                    return true
                }
            }
            KeyEvent.KEYCODE_K -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+K: Toggle Mark")
                    toggleMark()
                    return true
                }
            }
            KeyEvent.KEYCODE_R -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+R - Reschedule")
                    rescheduleSelectedCards()
                    return true
                }
            }
            KeyEvent.KEYCODE_G -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+G - Grade Now")
                    openGradeNow()
                    return true
                }
            }
            KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_DEL -> {
                Timber.i("Delete pressed - Delete Selected Note")
                // deleteSelectedNotes()
                return true
            }
            KeyEvent.KEYCODE_F -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("CTRL+ALT+F - Find and replace")
                    showFindAndReplaceDialog()
                    return true
                }
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+F - Find notes")
                    viewModel.expandSearchView()
                    return true
                }
            }
            KeyEvent.KEYCODE_P -> {
                if (event.isShiftPressed && event.isCtrlPressed) {
                    Timber.i("Ctrl+Shift+P - Preview")
                    onPreview()
                    return true
                }
            }
            KeyEvent.KEYCODE_N -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+N: Reset card progress")
                    onResetProgress()
                    return true
                }
            }
            KeyEvent.KEYCODE_T -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+T: Toggle cards/notes")
                    showOptionsDialog()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+T: Show filter by tags dialog")
                    showFilterByTagsDialog()
                    return true
                }
            }
            KeyEvent.KEYCODE_S -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+S: Reposition selected cards")
                    repositionSelectedCards()
                    return true
                } else if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+S: Show saved searches")
                    showSavedSearches()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+S: Save search")
                    openSaveSearchView()
                    return true
                } else if (event.isAltPressed) {
                    Timber.i("Alt+S: Show suspended cards")
                    viewModel.searchForSuspendedCards()
                    return true
                }
            }
            KeyEvent.KEYCODE_J -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+J: Toggle bury cards")
                    toggleBury()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+J: Toggle suspended cards")
                    toggleSuspendCards()
                    return true
                }
            }
            KeyEvent.KEYCODE_I -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+I: Card info")
                    displayCardInfo()
                    return true
                }
            }
            KeyEvent.KEYCODE_O -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+O: Show order dialog")
                    changeDisplayOrder()
                    return true
                }
            }
            KeyEvent.KEYCODE_M -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+M: Search marked notes")
                    viewModel.searchForMarkedNotes()
                    return true
                }
            }
            KeyEvent.KEYCODE_Z -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+Z: Undo")
                    onUndo()
                    return true
                }
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                Timber.i("ESC: Select none")
                viewModel.selectNone()
                return true
            }
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_7 -> {
                if (event.isCtrlPressed) {
                    Timber.i("Update flag")
                    updateFlag(keyCode)
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun updateFlag(keyCode: Int) {
        val flag =
            when (keyCode) {
                KeyEvent.KEYCODE_1 -> Flag.RED
                KeyEvent.KEYCODE_2 -> Flag.ORANGE
                KeyEvent.KEYCODE_3 -> Flag.GREEN
                KeyEvent.KEYCODE_4 -> Flag.BLUE
                KeyEvent.KEYCODE_5 -> Flag.PINK
                KeyEvent.KEYCODE_6 -> Flag.TURQUOISE
                KeyEvent.KEYCODE_7 -> Flag.PURPLE
                else -> return
            }
        updateFlagForSelectedRows(flag)
    }

    /** All the notes of the selected cards will be marked
     * If one or more card is unmarked, all will be marked,
     * otherwise, they will be unmarked  */
    @NeedsTest("Test that the mark get toggled as expected for a list of selected cards")
    @VisibleForTesting
    fun toggleMark() =
        launchCatchingTask {
            withProgress { viewModel.toggleMark() }
        }

    /** Opens the note editor for a card.
     * We use the Card ID to specify the preview target  */
    @NeedsTest("note edits are saved")
    @NeedsTest("I/O edits are saved")
    fun openNoteEditorForCard(cardId: CardId) {
        viewModel.openNoteEditorForCard(cardId)
    }

    /**
     * In case of selection, the first card that was selected, otherwise the first card of the list.
     */
    private suspend fun getCardIdForNoteEditor(): CardId {
        // Just select the first one if there's a multiselect occurring.
        return if (viewModel.isInMultiSelectMode) {
            viewModel.querySelectedCardIdAtPosition(0)
        } else {
            viewModel.getRowAtPosition(0).toCardId(viewModel.cardsOrNotes)
        }
    }

    private fun openNoteEditorForCurrentlySelectedNote() =
        launchCatchingTask {
            // Check whether the deck is empty
            if (viewModel.rowCount == 0) {
                showSnackbar(R.string.no_note_to_edit)
                return@launchCatchingTask
            }

            try {
                val cardId = getCardIdForNoteEditor()
                openNoteEditorForCard(cardId)
            } catch (e: Exception) {
                Timber.w(e, "Error Opening Note Editor")
                showSnackbar(R.string.multimedia_editor_something_wrong)
            }
        }

    override fun onStop() {
        // cancel rendering the question and answer, which has shared access to mCards
        super.onStop()
        if (!isFinishing) {
            updateInBackground(this)
        }
    }

    override fun onPause() {
        super.onPause()
        // If the user entered something into the search, but didn't press "search", clear this.
        // It's confusing if the bar is shown with a query that does not relate to the data on the screen
        viewModel.removeUnsubmittedInput()
    }

    @KotlinCleanup("Add a few variables to get rid of the !!")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Timber.d("onCreateOptionsMenu()")

        if (!viewModel.isInMultiSelectMode) {
            menuInflater.inflate(R.menu.card_browser, menu)
            viewModel.flowOfStandardMenuState.value.setup(menu, this, viewModel)
        } else {
            menuInflater.inflate(R.menu.card_browser_multiselect, menu)
            viewModel.flowOfMultiSelectMenuState.value.setup(menu, this, viewModel)
            increaseHorizontalPaddingOfOverflowMenuIcons(menu)
        }
        // Append note editor menu to card browser menu if fragmented and deck is not empty
        if (fragmented && viewModel.rowCount != 0) {
            fragment?.onCreateMenu(menu, menuInflater)
        }
        menu.findItem(R.id.action_undo)?.title = getColUnsafe.undoLabel()
        return super.onCreateOptionsMenu(menu)
    }

    private fun updateFlagForSelectedRows(flag: Flag) =
        launchCatchingTask {
            updateSelectedCardsFlag(flag)
        }

    /**
     * Sets the flag for selected cards
     */
    @VisibleForTesting
    suspend fun updateSelectedCardsFlag(flag: Flag) {
        // list of cards with updated flags
        val updatedCardIds = withProgress { viewModel.updateSelectedCardsFlag(flag) }
        if (updatedCardIds.any { it == reviewerCardId }) {
            reloadRequired = true
        }
    }

    /**
     * @return `false` if the user may proceed; `true` if a warning is shown due to being in [NOTES]
     */
    private fun warnUserIfInNotesOnlyMode(): Boolean {
        if (viewModel.cardsOrNotes != NOTES) return false
        showSnackbar(R.string.card_browser_unavailable_when_notes_mode) {
            setAction(R.string.error_handling_options) { showOptionsDialog() }
        }
        return true
    }

    @NeedsTest("filter-marked query needs testing")
    @NeedsTest("filter-suspended query needs testing")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when {
            // dismiss undo-snackbar if shown to avoid race condition
            // (when another operation will be performed on the model, it will undo the latest operation)
            undoSnackbar != null && undoSnackbar!!.isShown -> undoSnackbar!!.dismiss()
        }

        Flag.entries.find { it.ordinal == item.itemId }?.let { flag ->
            when (item.groupId) {
                Mode.SINGLE_SELECT.value -> filterByFlag(flag)
                Mode.MULTI_SELECT.value -> updateFlagForSelectedRows(flag)
                else -> return@let
            }
            return true
        }

        when (item.itemId) {
            android.R.id.home -> {
                viewModel.endMultiSelectMode(SingleSelectCause.NavigateBack)
                return true
            }
            R.id.action_add_note_from_card_browser -> {
                addNoteFromCardBrowser()
                return true
            }
            R.id.action_save_search -> {
                Timber.d("Menu::save search")
                openSaveSearchView()
                return true
            }
            R.id.action_list_my_searches -> {
                showSavedSearches()
                return true
            }
            R.id.action_sort_by_size -> {
                changeDisplayOrder()
                return true
            }
            R.id.action_show_marked -> {
                viewModel.searchForMarkedNotes()
                return true
            }
            R.id.action_show_suspended -> {
                viewModel.searchForSuspendedCards()
                return true
            }
            R.id.action_search_by_tag -> {
                showFilterByTagsDialog()
                return true
            }
            R.id.action_delete_card -> {
                deleteSelectedNotes()
                return true
            }
            R.id.action_mark_card -> {
                toggleMark()
                return true
            }
            R.id.action_suspend_card -> {
                toggleSuspendCards()
                return true
            }
            R.id.action_toggle_bury -> {
                toggleBury()
                return true
            }
            R.id.action_change_deck -> {
                showChangeDeckDialog()
                return true
            }
            R.id.action_undo -> {
                Timber.w("CardBrowser:: Undo pressed")
                onUndo()
                return true
            }
            R.id.action_select_all -> {
                viewModel.selectAll()
                return true
            }
            R.id.action_preview -> {
                onPreview()
                return true
            }
            R.id.action_reset_cards_progress -> {
                Timber.i("NoteEditor:: Reset progress button pressed")
                onResetProgress()
                return true
            }
            R.id.action_grade_now -> {
                Timber.i("CardBrowser:: Grade now button pressed")
                openGradeNow()
                return true
            }
            R.id.action_reschedule_cards -> {
                Timber.i("CardBrowser:: Reschedule button pressed")
                rescheduleSelectedCards()
                return true
            }
            R.id.action_reposition_cards -> {
                repositionSelectedCards()
                return true
            }
            R.id.action_edit_note -> {
                openNoteEditorForCurrentlySelectedNote()
                return super.onOptionsItemSelected(item)
            }
            R.id.action_view_card_info -> {
                displayCardInfo()
                return true
            }
            R.id.action_edit_tags -> {
                showEditTagsDialog()
                return true
            }
            R.id.action_open_options -> {
                showOptionsDialog()
                return true
            }
            R.id.action_export_selected -> {
                exportSelected()
                return true
            }
            R.id.action_create_filtered_deck -> {
                showCreateFilteredDeckDialog()
                return true
            }
            R.id.action_find_replace -> {
                showFindAndReplaceDialog()
                return true
            }
        }
        if (fragment?.onMenuItemSelected(item) == true) {
            return true
        }
        if (fragment == null) {
            Timber.w("Unexpected onOptionsItemSelected call: %s", item.itemId)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showCreateFilteredDeckDialog() {
        val dialog = CreateDeckDialog(this, R.string.new_deck, CreateDeckDialog.DeckDialogType.FILTERED_DECK, null)
        dialog.onNewDeckCreated = {
            startActivity(
                FilteredDeckOptions.getIntent(
                    this,
                    deckId = null,
                    searchTerms = viewModel.searchTerms,
                ),
            )
        }
        launchCatchingTask {
            withProgress {
                dialog.showFilteredDeckDialog()
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun showFindAndReplaceDialog() {
        FindAndReplaceDialogFragment().show(supportFragmentManager, FindAndReplaceDialogFragment.TAG)
    }

    private fun changeDisplayOrder() {
        showDialogFragment(
            // TODO: move this into the ViewModel
            CardBrowserOrderDialog.newInstance { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                viewModel.changeCardOrder(SortType.fromCardBrowserLabelIndex(which))
            },
        )
    }

    private fun showSavedSearches() {
        launchCatchingTask {
            val savedFilters = viewModel.savedSearches()
            showDialogFragment(
                newInstance(
                    savedFilters,
                    mySearchesDialogListener,
                    "",
                    CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_LIST,
                ),
            )
        }
    }

    private fun openSaveSearchView() =
        launchCatchingTask {
            Timber.d("open save search")
            val searchTerms = viewModel.flowOfFilterQuery.value
            showDialogFragment(
                newInstance(
                    null,
                    mySearchesDialogListener,
                    searchTerms,
                    CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_SAVE,
                ),
            )
        }

    fun openGradeNow() =
        launchCatchingTask {
            val cardIds = viewModel.queryAllSelectedCardIds()
            GradeNowDialog.showDialog(this@CardBrowser, cardIds)
        }

    private fun repositionSelectedCards(): Boolean {
        Timber.i("CardBrowser:: Reposition button pressed")
        if (warnUserIfInNotesOnlyMode()) return false
        launchCatchingTask {
            when (val repositionCardsResult = viewModel.prepareToRepositionCards()) {
                is ContainsNonNewCardsError -> {
                    // Only new cards may be repositioned (If any non-new found show error dialog and return false)
                    showDialogFragment(
                        SimpleMessageDialog.newInstance(
                            title = getString(R.string.vague_error),
                            message = getString(R.string.reposition_card_not_new_error),
                            reload = false,
                        ),
                    )
                    return@launchCatchingTask
                }
                is RepositionData -> {
                    val top = repositionCardsResult.queueTop
                    val bottom = repositionCardsResult.queueBottom
                    if (top == null || bottom == null) {
                        showSnackbar(R.string.something_wrong)
                        return@launchCatchingTask
                    }
                    val repositionDialog =
                        RepositionCardFragment.newInstance(
                            queueTop = top,
                            queueBottom = bottom,
                            random = repositionCardsResult.random,
                            shift = repositionCardsResult.shift,
                        )
                    showDialogFragment(repositionDialog)
                }
            }
        }
        return true
    }

    private fun displayCardInfo() {
        launchCatchingTask {
            viewModel.queryCardInfoDestination()?.let { destination ->
                val intent: Intent = destination.toIntent(this@CardBrowser)
                startActivity(intent)
            }
        }
    }

    private fun exportSelected() {
        val (type, selectedIds) = viewModel.querySelectionExportData() ?: return
        ExportDialogFragment.newInstance(type, selectedIds).show(supportFragmentManager, "exportDialog")
    }

    private fun deleteSelectedNotes() =
        launchCatchingTask {
            withProgress(R.string.deleting_selected_notes) {
                viewModel.deleteSelectedNotes()
            }.ifNotZero { noteCount ->
                val deletedMessage = resources.getQuantityString(R.plurals.card_browser_cards_deleted, noteCount, noteCount)
                showUndoSnackbar(deletedMessage)
            }
        }

    @VisibleForTesting
    fun onUndo() {
        launchCatchingTask {
            undoAndShowSnackbar()
        }
    }

    private fun onResetProgress() {
        if (warnUserIfInNotesOnlyMode()) return
        showDialogFragment(ForgetCardsDialog())
    }

    @VisibleForTesting
    fun repositionCardsNoValidation(
        position: Int,
        step: Int,
        shuffle: Boolean,
        shift: Boolean,
    ) = launchCatchingTask {
        val count =
            withProgress {
                viewModel.repositionSelectedRows(
                    position = position,
                    step = step,
                    shuffle = shuffle,
                    shift = shift,
                )
            }
        showSnackbar(
            TR.browsingChangedNewPosition(count),
            Snackbar.LENGTH_SHORT,
        )
    }

    private fun onPreview() {
        launchCatchingTask {
            val intentData = viewModel.queryPreviewIntentData()
            onPreviewCardsActivityResult.launch(getPreviewIntent(intentData.currentIndex, intentData.idsFile))
        }
    }

    private fun getPreviewIntent(
        index: Int,
        idsFile: IdsFile,
    ): Intent = PreviewerDestination(index, idsFile).toIntent(this)

    private fun rescheduleSelectedCards() {
        if (!viewModel.hasSelectedAnyRows()) {
            Timber.i("Attempted reschedule - no cards selected")
            return
        }
        if (warnUserIfInNotesOnlyMode()) return

        launchCatchingTask {
            val allCardIds = viewModel.queryAllSelectedCardIds()
            showDialogFragment(SetDueDateDialog.newInstance(allCardIds))
        }
    }

    @KotlinCleanup("DeckSelectionListener is almost certainly a bug - deck!!")
    fun getChangeDeckDialog(selectableDecks: List<SelectableDeck>?): DeckSelectionDialog {
        val dialog =
            newInstance(
                getString(R.string.move_all_to_deck),
                null,
                false,
                selectableDecks!!,
            )
        // Add change deck argument so the dialog can be dismissed
        // after activity recreation, since the selected cards will be gone with it
        dialog.requireArguments().putBoolean(CHANGE_DECK_KEY, true)
        dialog.deckSelectionListener = DeckSelectionListener { deck: SelectableDeck? -> moveSelectedCardsToDeck(deck!!.deckId) }
        return dialog
    }

    private fun showChangeDeckDialog() =
        launchCatchingTask {
            if (!viewModel.hasSelectedAnyRows()) {
                Timber.i("Not showing Change Deck - No Cards")
                return@launchCatchingTask
            }
            val selectableDecks =
                viewModel
                    .getAvailableDecks()
                    .map { d -> SelectableDeck(d) }
            val dialog = getChangeDeckDialog(selectableDecks)
            showDialogFragment(dialog)
        }

    private fun addNoteFromCardBrowser() {
        if (fragmented) {
            loadNoteEditorFragmentIfFragmented()
        } else {
            onAddNoteActivityResult.launch(addNoteLauncher.toIntent(this))
        }
    }

    private val reviewerCardId: CardId
        get() = intent.getLongExtra("currentCard", -1)

    private fun showEditTagsDialog() {
        if (!viewModel.hasSelectedAnyRows()) {
            Timber.d("showEditTagsDialog: called with empty selection")
        }
        tagsDialogListenerAction = TagsDialogListenerAction.EDIT_TAGS
        lifecycleScope.launch {
            val noteIds = viewModel.queryAllSelectedNoteIds()
            val dialog =
                tagsDialogFactory.newTagsDialog().withArguments(
                    this@CardBrowser,
                    type = TagsDialog.DialogType.EDIT_TAGS,
                    noteIds = noteIds,
                )
            showDialogFragment(dialog)
        }
    }

    private fun showFilterByTagsDialog() {
        launchCatchingTask {
            tagsDialogListenerAction = TagsDialogListenerAction.FILTER
            val dialog =
                tagsDialogFactory.newTagsDialog().withArguments(
                    context = this@CardBrowser,
                    type = TagsDialog.DialogType.FILTER_BY_TAG,
                    noteIds = emptyList(),
                )
            showDialogFragment(dialog)
        }
    }

    private fun showOptionsDialog() {
        val dialog = BrowserOptionsDialog.newInstance(viewModel.cardsOrNotes, viewModel.isTruncated)
        dialog.show(supportFragmentManager, "browserOptionsDialog")
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        // Save current search terms
        outState.putString("mSearchTerms", viewModel.searchTerms)
        super.onSaveInstanceState(outState)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        viewModel.onReinit()
        viewModel.launchSearchForCards(
            savedInstanceState.getString("mSearchTerms", ""),
            forceRefresh = false,
        )
    }

    private fun forceRefreshSearch() {
        viewModel.launchSearchForCards()
    }

    @NeedsTest("searchView == null -> return early & ensure no snackbar when the screen is opened")
    private fun redrawAfterSearch() {
        Timber.i("CardBrowser:: Completed searchCards() Successfully")
        val subtitleId =
            if (viewModel.cardsOrNotes == CARDS) {
                R.plurals.card_browser_subtitle
            } else {
                R.plurals.card_browser_subtitle_notes_mode
            }
        showSnackbar(resources.getQuantityString(subtitleId, viewModel.rowCount, viewModel.rowCount), Snackbar.LENGTH_SHORT)
    }

    override val subtitleText: String
        get() = ""

    @RustCleanup("this isn't how Desktop Anki does it")
    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        when (tagsDialogListenerAction) {
            TagsDialogListenerAction.FILTER -> filterByTags(selectedTags, stateFilter)
            TagsDialogListenerAction.EDIT_TAGS ->
                launchCatchingTask {
                    editSelectedCardsTags(selectedTags, indeterminateTags)
                }
            else -> {}
        }
    }

    /**
     * Updates the tags of selected/checked notes and saves them to the disk
     * @param selectedTags list of checked tags
     * @param indeterminateTags a list of tags which can checked or unchecked, should be ignored if not expected
     * For more info on [selectedTags] and [indeterminateTags] see [com.ichi2.anki.dialogs.tags.TagsDialogListener.onSelectedTags]
     */
    private suspend fun editSelectedCardsTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
    ) = withProgress {
        val selectedNoteIds = viewModel.queryAllSelectedNoteIds().distinct()
        undoableOp {
            val selectedNotes =
                selectedNoteIds
                    .map { noteId -> getNote(noteId) }
                    .onEach { note ->
                        val previousTags: List<String> = note.tags
                        val updatedTags = getUpdatedTags(previousTags, selectedTags, indeterminateTags)
                        note.setTagsFromStr(this@undoableOp, tags.join(updatedTags))
                    }
            updateNotes(selectedNotes)
        }
    }

    private fun filterByTags(
        selectedTags: List<String>,
        cardState: CardStateFilter,
    ) = launchCatchingTask {
        viewModel.filterByTags(selectedTags, cardState)
    }

    /** Updates search terms to only show cards with selected flag.  */
    @VisibleForTesting
    fun filterByFlag(flag: Flag) = launchCatchingTask { viewModel.setFlagFilter(flag) }

    private fun toggleSuspendCards() = launchCatchingTask { withProgress { viewModel.toggleSuspendCards().join() } }

    /** @see CardBrowserViewModel.toggleBury */
    private fun toggleBury() =
        launchCatchingTask {
            val result = withProgress { viewModel.toggleBury() } ?: return@launchCatchingTask
            // show a snackbar as there's currently no colored background for buried cards
            val message =
                when (result.wasBuried) {
                    true -> TR.studyingCardsBuried(result.count)
                    false -> resources.getQuantityString(R.plurals.unbury_cards_feedback, result.count, result.count)
                }
            showUndoSnackbar(message)
        }

    private fun showUndoSnackbar(message: CharSequence) {
        showSnackbar(message) {
            setAction(R.string.undo) { launchCatchingTask { undoAndShowSnackbar() } }
            undoSnackbar = this
        }
    }

    private fun refreshAfterUndo() {
        // reload whole view
        forceRefreshSearch()
        viewModel.endMultiSelectMode(SingleSelectCause.Other)
    }

    fun searchAllDecks() =
        launchCatchingTask {
            // all we need to do is select all decks
            viewModel.setDeckId(DeckSpinnerSelection.ALL_DECKS_ID)
        }

    /**
     * Returns the current deck name, "All Decks" if all decks are selected, or "Unknown"
     * Do not use this for any business logic, as this will return inconsistent data
     * with the collection.
     */
    val selectedDeckNameForUi: String
        get() =
            try {
                when (val deckId = viewModel.lastDeckId) {
                    null -> getString(R.string.card_browser_unknown_deck_name)
                    DeckSpinnerSelection.ALL_DECKS_ID -> getString(R.string.card_browser_all_decks)
                    else -> getColUnsafe.decks.name(deckId)
                }
            } catch (e: Exception) {
                Timber.w(e, "Unable to get selected deck name")
                getString(R.string.card_browser_unknown_deck_name)
            }

    private fun closeCardBrowser(
        result: Int,
        data: Intent? = null,
    ) {
        // Set result and finish
        setResult(result, data)
        finish()
    }

    /**
     * Implementation of `by viewModels()` for use in [onCreate]
     *
     * @see showedActivityFailedScreen - we may not have AnkiDroidApp.instance and therefore can't
     * create the ViewModel
     *
     * @param fragmented True if `noteEditorFrame` is non-null (x-large displays)
     */
    private fun createViewModel(
        launchOptions: CardBrowserLaunchOptions?,
        fragmented: Boolean,
    ) = ViewModelProvider(
        viewModelStore,
        CardBrowserViewModel.factory(
            lastDeckIdRepository = AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository,
            cacheDir = cacheDir,
            options = launchOptions,
            isFragmented = fragmented,
        ),
        defaultViewModelCreationExtras,
    )[CardBrowserViewModel::class.java]

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun filterByTag(vararg tags: String) {
        tagsDialogListenerAction = TagsDialogListenerAction.FILTER
        onSelectedTags(tags.toList(), emptyList(), CardStateFilter.ALL_CARDS)
        filterByTags(tags.toList(), CardStateFilter.ALL_CARDS)
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        // TODO: move this further down after we're sure undo is handled
        viewModel.onCollectionChange()
        if (handler === this || handler === viewModel) {
            return
        }

        if (changes.browserSidebar ||
            changes.browserTable ||
            changes.noteText ||
            changes.card
        ) {
            refreshAfterUndo()
        }
    }

    override val shortcuts
        get() =
            ShortcutGroup(
                listOf(
                    shortcut("Ctrl+Shift+A", R.string.edit_tags_dialog),
                    shortcut("Ctrl+A", R.string.card_browser_select_all),
                    shortcut("Ctrl+Shift+E", Translations::exportingExport),
                    shortcut("Ctrl+E", R.string.menu_add_note),
                    shortcut("E", R.string.cardeditor_title_edit_card),
                    shortcut("Ctrl+D", R.string.card_browser_change_deck),
                    shortcut("Ctrl+K", Translations::browsingToggleMark),
                    shortcut("Ctrl+Alt+R", Translations::browsingReschedule),
                    shortcut("DEL", R.string.delete_card_title),
                    shortcut("Ctrl+Alt+N", R.string.reset_card_dialog_title),
                    shortcut("Ctrl+Alt+T", R.string.toggle_cards_notes),
                    shortcut("Ctrl+T", R.string.card_browser_search_by_tag),
                    shortcut("Ctrl+Shift+S", Translations::actionsReposition),
                    shortcut("Ctrl+Alt+S", R.string.card_browser_list_my_searches),
                    shortcut("Ctrl+S", R.string.card_browser_list_my_searches_save),
                    shortcut("Alt+S", R.string.card_browser_show_suspended),
                    shortcut("Ctrl+Shift+G", Translations::actionsGradeNow),
                    shortcut("Ctrl+Shift+J", Translations::browsingToggleBury),
                    shortcut("Ctrl+J", Translations::browsingToggleSuspend),
                    shortcut("Ctrl+Shift+I", Translations::actionsCardInfo),
                    shortcut("Ctrl+O", R.string.show_order_dialog),
                    shortcut("Ctrl+M", R.string.card_browser_show_marked),
                    shortcut("Esc", R.string.card_browser_select_none),
                    shortcut("Ctrl+1", R.string.gesture_flag_red),
                    shortcut("Ctrl+2", R.string.gesture_flag_orange),
                    shortcut("Ctrl+3", R.string.gesture_flag_green),
                    shortcut("Ctrl+4", R.string.gesture_flag_blue),
                    shortcut("Ctrl+5", R.string.gesture_flag_pink),
                    shortcut("Ctrl+6", R.string.gesture_flag_turquoise),
                    shortcut("Ctrl+7", R.string.gesture_flag_purple),
                ),
                R.string.card_browser_context_menu,
            )

    companion object {
        /**
         * Argument key to add on change deck dialog,
         * so it can be dismissed on activity recreation,
         * since the cards are unselected when this happens
         */
        private const val CHANGE_DECK_KEY = "CHANGE_DECK"

        // Keys for saving pane weights in SharedPreferences
        private const val PREF_CARD_BROWSER_PANE_WEIGHT = "cardBrowserPaneWeight"
        private const val PREF_NOTE_EDITOR_PANE_WEIGHT = "noteEditorPaneWeight"

        // Values related to persistent state data
        fun clearLastDeckId() = SharedPreferencesLastDeckIdRepository.clearLastDeckId()

        @VisibleForTesting
        fun createAddNoteLauncher(
            viewModel: CardBrowserViewModel,
            inFragmentedActivity: Boolean = false,
        ): NoteEditorLauncher = NoteEditorLauncher.AddNoteFromCardBrowser(viewModel, inFragmentedActivity)
    }
}

suspend fun searchForRows(
    query: String,
    order: SortOrder,
    cardsOrNotes: CardsOrNotes,
): BrowserRowCollection =
    withCol {
        when (cardsOrNotes) {
            CARDS -> findCards(query, order)
            NOTES -> findNotes(query, order)
        }
    }.let { ids ->
        BrowserRowCollection(cardsOrNotes, ids.map { CardOrNoteId(it) }.toMutableList())
    }

class PreviewerDestination(
    val currentIndex: Int,
    val idsFile: IdsFile,
)

@CheckResult
fun PreviewerDestination.toIntent(context: Context) = PreviewerFragment.getIntent(context, idsFile, currentIndex)
