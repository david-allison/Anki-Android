// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose.preview

import androidx.lifecycle.SavedStateHandle
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.previewer.CardViewerViewModel
import com.ichi2.anki.previewer.TypeAnswer
import com.ichi2.anki.previewer.typeAnsRe
import com.ichi2.anki.reviewer.CardSide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.sample
import timber.log.Timber

internal data class PreviewCards(
    val choices: List<PreviewCardChoice> = emptyList(),
    val selectedOrdinal: CardOrdinal = 0,
)

/** Shares the reviewer's HTML, media handling and renderer without changing the editor's focus. */
@OptIn(FlowPreview::class)
class EditorPreviewViewModel(
    savedStateHandle: SavedStateHandle,
) : CardViewerViewModel(savedStateHandle) {
    private val input = MutableStateFlow<EditorPreviewInput?>(null)
    private val ordinal = savedStateHandle.getMutableStateFlow("previewOrdinal", 0)
    private val answer = savedStateHandle.getMutableStateFlow("previewAnswer", false)
    private val pageRevision = MutableStateFlow(0)
    internal val cards = MutableStateFlow(PreviewCards())

    override var currentCard: Deferred<Card> = CompletableDeferred()
    override val server = AnkiServer(this).also { it.start() }

    private data class Request(
        val input: EditorPreviewInput,
        val ordinal: CardOrdinal,
        val answer: Boolean,
        val pageRevision: Int,
    )

    init {
        launchCatchingIO {
            var previous: Request? = null
            combine(input, ordinal, answer, pageRevision) { input, ordinal, answer, pageRevision ->
                if (input == null || pageRevision == 0) null else Request(input, ordinal, answer, pageRevision)
            }.filterNotNull()
                // Sampling keeps the preview moving during continuous typing; debounce would not.
                .sample(100)
                .collect { request ->
                    try {
                        val rendered = withCol { renderEditorPreview(request.input, request.ordinal) }
                        currentCard = CompletableDeferred(rendered.card)
                        cards.value = PreviewCards(rendered.choices, rendered.card.ord)
                        cardMediaPlayer.loadCardAvTags(rendered.card)
                        if (request.answer) showAnswer() else showQuestion()

                        // User navigation may play media, but typing must not restart audio every 100 ms.
                        val navigationChanged = previous?.let { it.answer != request.answer || it.ordinal != request.ordinal } == true
                        if (navigationChanged) {
                            cardMediaPlayer.autoplayAllForSide(if (request.answer) CardSide.ANSWER else CardSide.QUESTION)
                        }
                        previous = request
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        // A failed render must not stop later content updates from recovering.
                        Timber.w(exception, "Unable to render the editor preview")
                        onError.emit(exception.localizedMessage ?: exception.toString())
                    }
                }
        }
    }

    fun update(input: EditorPreviewInput) {
        this.input.value = input
    }

    fun selectCard(ordinal: CardOrdinal) {
        this.ordinal.value = ordinal
    }

    fun toggleAnswer() {
        answer.value = !answer.value
    }

    override fun onPageFinished(isAfterRecreation: Boolean) {
        pageRevision.value += 1
    }

    override suspend fun typeAnsFilter(text: String): String =
        if (showingAnswer.value) {
            val typeAnswer = TypeAnswer.getInstance(currentCard.await(), text)
            if (typeAnswer?.expectedAnswer?.isEmpty() == true) typeAnswer.expectedAnswer = "sample"
            typeAnswer?.answerFilter(typedAnswer = "example") ?: text
        } else {
            val replacement = "<center><input id='typeans' type=text value='example' readonly='readonly'></center>"
            val warning = "<center><b>${CollectionManager.TR.cardTemplatesTypeBoxesWarning()}</b></center>"
            StringBuilder(text).replaceFirst(typeAnsRe, replacement).replace(typeAnsRe, warning)
        }
}
