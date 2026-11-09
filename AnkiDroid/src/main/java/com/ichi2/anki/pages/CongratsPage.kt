// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import anki.collection.OpChanges
import anki.scheduler.UnburyDeckRequest
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.DeckOptionsDestination
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.progress.HasProgress
import com.ichi2.anki.progress.ProgressManager
import com.ichi2.anki.progress.observeProgress
import com.ichi2.anki.study.StudyAvailability
import com.ichi2.anki.study.currentDeckStudyState
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.listItems
import com.ichi2.utils.negativeButton
import com.ichi2.utils.show
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

class CongratsPage : PageFragment() {
    override val pagePath: String = "congrats"

    private val viewModel by viewModels<CongratsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeProgress(viewModel) { progress -> progressMessage(progress) }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.onError
            .flowWithLifecycle(lifecycle)
            .onEach { errorMessage ->
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(CommonString.vague_error)
                    .setMessage(errorMessage)
                    .show()
            }.launchIn(lifecycleScope)

        viewModel.unburyState
            .onEach { state ->
                when (state) {
                    UnburyState.SelectMode -> {
                        val unburyOptions =
                            mutableListOf(
                                TR.studyingManuallyBuriedCards(),
                                TR.studyingBuriedSiblings(),
                                TR.studyingAllBuriedCards(),
                            )
                        AlertDialog.Builder(requireContext()).show {
                            setTitle(TR.studyingWhatWouldYouLikeToUnbury())
                            negativeButton(CommonString.dialog_cancel)
                            listItems(
                                unburyOptions,
                            ) { _, position ->
                                val mode =
                                    when (position) {
                                        0 -> UnburyDeckRequest.Mode.USER_ONLY
                                        1 -> UnburyDeckRequest.Mode.SCHED_ONLY
                                        2 -> UnburyDeckRequest.Mode.ALL
                                        else -> error("Unhandled unbury option: ${unburyOptions[position]}")
                                    }
                                viewModel.onUnburyModeSelected(mode)
                            }
                        }
                    }
                }
            }.launchIn(lifecycleScope)

        viewModel.deckOptionsDestination
            .flowWithLifecycle(lifecycle)
            .onEach { destination ->
                navigate(destination)
            }.launchIn(lifecycleScope)

        // Subscribe before refreshing on STARTED so reload requests reach an active collector.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.studyOptionsDestination.collect { destination ->
                        Timber.i("opening study options")
                        navigate(destination)
                        requireActivity().finish()
                        viewModel.onStudyOptionsOpened()
                    }
                }
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.congratsRefreshState.collect { state ->
                        when (state) {
                            CongratsRefreshState.ReloadPage -> {
                                Timber.d("congrats: reloading page after rebuild")
                                webViewLayout.post { webViewLayout.reload() }
                            }
                        }
                    }
                }
                viewModel.refreshIfNeeded()
            }
        }

        with(view.findViewById<MaterialToolbar>(R.id.toolbar)) {
            inflateMenu(R.menu.congrats)
            menu.findItem(R.id.action_rebuild).title = TR.actionsRebuild()
            menu.findItem(R.id.action_open_deck_options)?.title = TR.sentenceCase.deckOptions
            viewModel.isFilteredDeck
                .flowWithLifecycle(viewLifecycleOwner.lifecycle)
                .onEach { isFilteredDeck ->
                    menu.findItem(R.id.action_rebuild).isVisible = isFilteredDeck
                }.launchIn(viewLifecycleOwner.lifecycleScope)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_open_deck_options -> viewModel.onDeckOptions()
                    R.id.action_rebuild -> viewModel.onRebuild()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }

        setFragmentResultListener(CustomStudyAction.REQUEST_KEY) { _, bundle ->
            when (CustomStudyAction.fromBundle(bundle)) {
                CustomStudyAction.CUSTOM_STUDY_SESSION,
                CustomStudyAction.EXTEND_STUDY_LIMITS,
                -> viewModel.onCustomStudyCompleted()
            }
        }
    }

    override val bridgeCommands =
        mapOf(
            "unbury" to { viewModel.onUnbury() },
            "customStudy" to { onStudyMore() },
        )

    private fun progressMessage(progress: CongratsProgress): String =
        getString(
            when (progress) {
                CongratsProgress.REBUILDING_FILTERED_DECK -> CommonString.rebuild_filtered_deck
                CongratsProgress.EMPTYING_FILTERED_DECK -> CommonString.empty_filtered_deck
            },
        )

    private fun onStudyMore() {
        launchCatchingTask {
            val customStudy = CustomStudyDialog.createInstance(deckId = withCol { decks.selected() })
            requireActivity().showDialogFragment(customStudy)
        }
    }

    companion object {
        fun getIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, fragmentClass = CongratsPage::class)
    }
}

class CongratsViewModel :
    ViewModel(),
    OnErrorListener,
    ChangeManager.Subscriber,
    HasProgress<CongratsProgress> {
    override val progressManager = ProgressManager<CongratsProgress>()
    override val onError = MutableSharedFlow<String>()
    val unburyState = MutableSharedFlow<UnburyState>()
    val deckOptionsDestination = MutableSharedFlow<DeckOptionsDestination>()

    // Keep the request until the fragment has performed navigation.
    val studyOptionsDestination = MutableSharedFlow<StudyOptionsDestination>(replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun onStudyOptionsOpened() {
        studyOptionsDestination.resetReplayCache()
    }

    init {
        ChangeManager.subscribe(this)
    }

    override fun onCleared() {
        ChangeManager.unsubscribe(this)
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        // Changes this ViewModel causes are already handled by the operation's completion.
        if (changes.studyQueues && handler !== this) {
            Timber.i("study queues updated")
            onStudyQueuesChanged()
        }
    }

    fun onCustomStudyCompleted() = launchCatchingIO { openStudyOptions() }

    private suspend fun openStudyOptions() {
        studyOptionsDestination.emit(StudyOptionsDestination)
    }

    private val filteredDeckId = MutableStateFlow<DeckId?>(null)
    val isFilteredDeck: Flow<Boolean> = filteredDeckId.map { it != null }.distinctUntilChanged()

    fun onRebuild() {
        val deckId = filteredDeckId.value ?: return
        launchCatchingIO {
            progressManager.withProgress(message = CongratsProgress.REBUILDING_FILTERED_DECK) {
                undoableOp(handler = this@CongratsViewModel) { sched.rebuildFilteredDeck(deckId) }
            }
            onStudyQueuesChanged()
        }
    }

    fun onEmpty() {
        val deckId = filteredDeckId.value ?: return
        launchCatchingIO {
            progressManager.withProgress(message = CongratsProgress.EMPTYING_FILTERED_DECK) {
                undoableOp(handler = this@CongratsViewModel) { sched.emptyFilteredDeck(deckId) }
            }
            onStudyQueuesChanged()
        }
    }

    fun onUnbury() {
        launchCatchingIO {
            // https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/qt/aqt/overview.py#L154
            val congratsInfo = withCol { sched.congratulationsInfo() }
            if (congratsInfo.haveSchedBuried && congratsInfo.haveUserBuried) {
                unburyState.emit(UnburyState.SelectMode)
                return@launchCatchingIO
            }
            unburyAndStudy(UnburyDeckRequest.Mode.ALL)
        }
    }

    fun onUnburyModeSelected(mode: UnburyDeckRequest.Mode) {
        launchCatchingIO {
            unburyAndStudy(mode)
        }
    }

    private suspend fun unburyAndStudy(mode: UnburyDeckRequest.Mode) {
        undoableOp(handler = this) {
            sched.unburyDeck(decks.getCurrentId(), mode)
        }
        openStudyOptions()
    }

    fun onDeckOptions() {
        launchCatchingIO {
            val deckId = withCol { decks.getCurrentId() }
            val isFiltered = withCol { decks.isFiltered(deckId) }
            deckOptionsDestination.emit(DeckOptionsDestination(deckId, isFiltered))
        }
    }

    val congratsRefreshState = MutableSharedFlow<CongratsRefreshState>()

    // set when onStudyQueuesChanged() has nothing to emit to (screen not STARTED, so
    // congratsRefreshState has no collector and would silently drop the event) - consumed
    // and cleared by refreshIfNeeded() on the next STARTED
    private var missedRefresh = false

    fun onStudyQueuesChanged() {
        launchCatchingIO {
            if (congratsRefreshState.subscriptionCount.value == 0 && studyOptionsDestination.subscriptionCount.value == 0) {
                missedRefresh = true
                return@launchCatchingIO
            }
            emitRefreshState()
        }
    }

    /** Called every time the screen becomes STARTED. [isDeckFinished] is always checked,
     * but reload/navigation only happens if something was actually
     * missed while unwatched, or the deck no longer needs the congrats page. */
    suspend fun refreshIfNeeded() {
        filteredDeckId.value = withCol { decks.current().takeIf { it.isFiltered }?.id }
        // desktop's Overview re-checks sched._is_finished() on every refresh instead of
        // assuming the deck is still done - do the same before reloading the congrats page
        val stillFinished = isDeckFinished()
        if (!missedRefresh && stillFinished) return
        missedRefresh = false
        emitRefreshState(stillFinished)
    }

    private suspend fun emitRefreshState() {
        missedRefresh = false
        emitRefreshState(isDeckFinished())
    }

    private suspend fun emitRefreshState(stillFinished: Boolean) {
        if (stillFinished) {
            congratsRefreshState.emit(CongratsRefreshState.ReloadPage)
        } else {
            openStudyOptions()
        }
    }

    private suspend fun isDeckFinished(): Boolean = withCol { currentDeckStudyState().availability == StudyAvailability.NoCardsAvailable }
}

enum class CongratsProgress {
    REBUILDING_FILTERED_DECK,
    EMPTYING_FILTERED_DECK,
}

sealed class UnburyState {
    data object SelectMode : UnburyState()
}

sealed class CongratsRefreshState {
    data object ReloadPage : CongratsRefreshState()
}
