// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.NoteFieldsCheckResult
import com.ichi2.anki.checkNoteFieldsResponse
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.CollectionFiles
import com.ichi2.anki.libanki.Field
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.mediaFolder
import com.ichi2.anki.noteeditor.web.WebEditorSettings
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale

/**
 * Owns native editor metadata and collection operations. Field HTML remains in the WebView until
 * the host requests a snapshot for Save, a note-type change, or recovery.
 */
@MainThread
class EditorViewModel(
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<EditorState?>(null)
    val state: StateFlow<EditorState?> = mutableState

    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError

    private val mutableSaveResult = MutableStateFlow<EditorSaveResult?>(null)
    val saveResult: StateFlow<EditorSaveResult?> = mutableSaveResult
    private var activeSave: Deferred<EditorSaveResult>? = null

    private var collectionKey: String? = null
    private var affectedCardIds: List<Long> = emptyList()
    private var baseline: Metadata? = null

    /** A retained ViewModel already owns newer native metadata than a pending web checkpoint may contain. */
    internal var hasAttachedEditor: Boolean = false

    private val draftIds = EditorDraftIds(Prefs.sharedPrefs)
    private val toolbarPreferences = EditorToolbarPreferences(Prefs.sharedPrefs)

    val draftId: String get() = requireNotNull(state.value).sessionId

    /** Consume before the first web request, including when attachment may be canceled. */
    internal fun takeDraftRecoveryRequired(): Boolean = draftIds.takeRecoveryRequired()

    suspend fun rememberDraft(keepForNextLaunch: Boolean = false) = withContext(ioDispatcher) { draftIds.remember(keepForNextLaunch) }

    suspend fun forgetDraft() = withContext(ioDispatcher) { draftIds.forget() }

    override fun onCleared() {
        draftIds.release()
        super.onCleared()
    }

    suspend fun load(arguments: Bundle) {
        if (state.value != null) return
        try {
            val loaded =
                withCol {
                    collectionKey = editorCollectionKey()
                    val caller = arguments.getInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.DECKPICKER.value)
                    val cardId =
                        when (caller) {
                            NoteEditorCaller.EDIT.value -> arguments.getLong(NoteEditorFragment.EXTRA_CARD_ID)
                            NoteEditorCaller.PREVIEWER_EDIT.value -> arguments.getLong(NoteEditorFragment.EXTRA_EDIT_FROM_CARD_ID)
                            else -> null
                        }
                    val defaults = if (cardId == null) defaultsForAdding() else null
                    val card = cardId?.let(::getCard)
                    val defaultType = defaults?.let { notetypes.get(it.notetypeId) }
                    val note =
                        card?.note(this) ?: Note.fromNotetypeId(
                            this,
                            requireNotNull(
                                defaultType?.takeUnless { it.isImageOcclusion }
                                    ?: notetypes
                                        .allNamesAndIds()
                                        .mapNotNull { notetypes.get(it.id) }
                                        .firstOrNull { !it.isImageOcclusion },
                            ) { "No supported note type is available." }.id,
                        )
                    // Image Occlusion has its own editor and remains on that route.
                    require(!note.notetype.isImageOcclusion) { "This note type requires the Image Occlusion editor." }
                    val deckId =
                        card?.currentDeckId() ?: arguments
                            .getLong(NoteEditorFragment.EXTRA_DID)
                            .takeIf { id -> decks.getLegacy(id)?.isFiltered == false }
                            ?: requireNotNull(defaults).deckId
                    affectedCardIds =
                        arguments.getLongArray(NoteEditorFragment.EXTRA_CARD_IDS)?.toList()?.takeIf { it.isNotEmpty() }
                            ?: listOfNotNull(cardId)
                    if (card == null) {
                        arguments.sharedFields()?.let { shared ->
                            shared.take(note.fields.size).forEachIndexed { index, text ->
                                note.fields[index] = TextUtils.htmlEncode(text).replace("\n", "<br>")
                            }
                        }
                        arguments.getString(NoteEditorFragment.EXTRA_CONTENTS)?.split('\u001f')?.let { contents ->
                            note.fields = List(note.fields.size) { contents.getOrElse(it) { "" } }.toMutableList()
                        }
                        arguments.getString(NoteEditorFragment.EXTRA_TEXT_FROM_SEARCH_VIEW)?.takeIf { it.isNotEmpty() }?.let {
                            note.fields[0] = TextUtils.htmlEncode(it).replace("\n", "<br>")
                        }
                        arguments.getStringArray(NoteEditorFragment.EXTRA_TAGS)?.let { note.setTagsFromStr(this, it.joinToString(" ")) }
                    }
                    EditorState(
                        sessionId = "",
                        generation = 0,
                        isAdding = card == null,
                        noteId = note.id,
                        cardId = cardId,
                        deckId = deckId,
                        deckName = decks.name(deckId),
                        noteTypeId = note.noteTypeId,
                        noteTypeName = note.notetype.name,
                        notetypeJson = note.notetype.toString(),
                        mediaDirectory = mediaFolder?.absolutePath.orEmpty(),
                        isCloze = note.notetype.isCloze,
                        fields = note.notetype.editorFields(note.fields),
                        tags = note.tags.toList(),
                        customButtonTemplates = toolbarPreferences.loadCustomButtons(),
                        toolbarDisplay =
                            EditorToolbarDisplay.entries.firstOrNull {
                                it.name == Prefs.sharedPrefs.getString("composeEditorToolbarDisplay", null)
                            } ?: EditorToolbarDisplay.SCROLL,
                        settings =
                            WebEditorSettings(
                                shrinkImages = config.get<Boolean>("shrinkEditorImages") ?: true,
                                mathjaxPreview = config.get<Boolean>("renderMathjax") ?: true,
                                autoCloseTags = config.get<Boolean>("closeHTMLTags") ?: true,
                                fontSize =
                                    Prefs.sharedPrefs
                                        .getInt(
                                            NoteEditorFragment.PREF_NOTE_EDITOR_FONT_SIZE,
                                            0,
                                        ).takeIf { it in 1..99 },
                            ),
                    )
                }
            val target =
                if (loaded.isAdding) {
                    editorAddDraftTarget(arguments)
                } else {
                    "edit:${loaded.noteId}:${loaded.cardId}:${affectedCardIds.joinToString(",")}"
                }
            // A new Add starts empty. Restore only this saved instance or an explicitly kept draft.
            val sessionId =
                draftIds.acquire(requireNotNull(collectionKey), target, savedState[SESSION_KEY], recoverCheckpoint = !loaded.isAdding)
            savedState[SESSION_KEY] = sessionId
            baseline = loaded.metadata()
            mutableState.value = loaded.copy(sessionId = sessionId)
            mutableError.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(e)
        }
    }

    /** Reload field/template edits while preserving the unsaved values by field identity. */
    suspend fun reloadNoteType(snapshotHtml: List<String>): Boolean {
        val current = requireNotNull(state.value)
        require(snapshotHtml.size == current.fields.size)
        val oldType = NotetypeJson(current.notetypeJson)
        val type =
            withCol {
                requireCurrentCollection()
                requireNotNull(notetypes.get(current.noteTypeId))
            }
        if (type.toString() == current.notetypeJson) return false
        val mapping = mapEditorFields(oldType.fields.map { it.editorKey() }, current.fields.map { it.name }, snapshotHtml, type)
        val changedFields = oldType.jsonObject.getJSONArray("flds").toString() != type.jsonObject.getJSONArray("flds").toString()
        mutableState.value =
            current.copy(
                generation = current.generation + if (changedFields) 1 else 0,
                resetFieldBaseline = false,
                baselineFieldMap = if (changedFields) mapping.indices else current.baselineFieldMap,
                noteTypeName = type.name,
                notetypeJson = type.toString(),
                fields = type.editorFields(mapping.html),
                removedFields = current.removedFields + mapping.removed,
            )
        return true
    }

    fun discardRemovedFields() {
        mutableState.value = requireNotNull(state.value).copy(removedFields = emptyList())
    }

    suspend fun setEditorSettings(settings: WebEditorSettings) {
        requireNotNull(state.value)
        require(settings.fontSize == null || settings.fontSize in 1..99)
        withCol {
            requireCurrentCollection()
            config.set("shrinkEditorImages", settings.shrinkImages)
            config.set("renderMathjax", settings.mathjaxPreview)
            config.set("closeHTMLTags", settings.autoCloseTags)
        }
        withContext(ioDispatcher) {
            check(
                Prefs.sharedPrefs
                    .edit()
                    .putInt(NoteEditorFragment.PREF_NOTE_EDITOR_FONT_SIZE, settings.fontSize ?: 0)
                    .commit(),
            )
        }
        mutableState.value = requireNotNull(state.value).copy(settings = settings)
    }

    suspend fun setToolbarDisplay(display: EditorToolbarDisplay) {
        requireNotNull(state.value)
        withContext(ioDispatcher) {
            check(
                Prefs.sharedPrefs
                    .edit()
                    .putString("composeEditorToolbarDisplay", display.name)
                    .commit(),
            )
        }
        mutableState.value = requireNotNull(state.value).copy(toolbarDisplay = display)
    }

    suspend fun noteTypeChoices(): List<EditorChoice> =
        loadChoices {
            val types = notetypes.all()
            buildList {
                addAll(types.map { EditorChoice(it.id, it.name) })
                if (types.none { it.isImageOcclusion }) {
                    // Zero lets the existing Image Occlusion flow create the stock type when saving.
                    add(EditorChoice(0, TR.notetypesImageOcclusionName()))
                }
            }.sortedBy { it.name.lowercase() }
        }

    suspend fun isImageOcclusionType(noteTypeId: Long): Boolean =
        withCol {
            requireCurrentCollection()
            noteTypeId == 0L || requireNotNull(notetypes.get(noteTypeId)).isImageOcclusion
        }

    private suspend fun loadChoices(readChoices: Collection.() -> List<EditorChoice>): List<EditorChoice> {
        if (state.value == null) return emptyList()
        return try {
            withCol {
                requireCurrentCollection()
                readChoices()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(e)
            emptyList()
        }
    }

    suspend fun selectDeck(id: Long) {
        val current = state.value?.takeUnless { it.isSaving } ?: return
        try {
            val name =
                withCol {
                    requireCurrentCollection()
                    require(decks.getLegacy(id)?.isFiltered == false) { "The selected deck is no longer available." }
                    decks.name(id)
                }
            state.value?.takeIf { it.generation == current.generation && !it.isSaving }?.let {
                updateMetadata(it.copy(deckId = id, deckName = name))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(e)
        }
    }

    fun setTags(tags: List<String>) {
        val current = state.value?.takeUnless { it.isSaving } ?: return
        val normalized = tags.flatMap { it.replace('\u3000', ' ').split(Regex("\\s+")) }.filter { it.isNotEmpty() }.distinct()
        updateMetadata(current.copy(tags = normalized))
    }

    internal data class RemovedTag(
        val sessionId: String,
        val generation: Int,
        val name: String,
        val index: Int,
    )

    internal fun removeTag(name: String): RemovedTag? {
        val current = state.value?.takeUnless { it.isSaving } ?: return null
        val index = current.tags.indexOf(name).takeIf { it >= 0 } ?: return null
        updateMetadata(current.copy(tags = current.tags.filterNot { it == name }))
        return RemovedTag(current.sessionId, current.generation, name, index)
    }

    /** Undo only this removal, preserving subsequent tag edits and never changing the next Add note. */
    internal fun restoreTag(removed: RemovedTag) {
        val current = state.value?.takeUnless { it.isSaving } ?: return
        if (current.sessionId != removed.sessionId || current.generation != removed.generation || removed.name in current.tags) return
        val tags = current.tags.toMutableList().apply { add(removed.index.coerceAtMost(size), removed.name) }
        updateMetadata(current.copy(tags = tags))
    }

    /** Add-mode type changes retain values by ordinal, like the existing note editor. */
    suspend fun selectNoteType(
        id: Long,
        snapshotHtml: List<String>,
    ) {
        val current = state.value?.takeIf { it.isAdding && !it.isSaving && it.noteTypeId != id } ?: return
        if (snapshotHtml.size != current.fields.size) return
        mutableState.value = current.copy(isSaving = true)
        try {
            val changed =
                withCol {
                    requireCurrentCollection()
                    val type = requireNotNull(notetypes.get(id)) { "The selected note type is no longer available." }
                    require(!type.isImageOcclusion) { "This note type requires the Image Occlusion editor." }
                    val targetDeck = defaultDeckForNoteType(id) ?: current.deckId
                    current.copy(
                        generation = current.generation + 1,
                        resetFieldBaseline = false,
                        baselineFieldMap = null,
                        noteTypeId = type.id,
                        noteTypeName = type.name,
                        notetypeJson = type.toString(),
                        isCloze = type.isCloze,
                        fields = type.editorFields(List(type.fields.size) { snapshotHtml.getOrElse(it) { "" } }),
                        deckId = targetDeck,
                        deckName = decks.name(targetDeck),
                    )
                }
            updateMetadata(changed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(e)
        } finally {
            mutableState.value = mutableState.value?.copy(isSaving = false)
        }
    }

    suspend fun setFieldPinned(
        target: EditorFieldTarget,
        pinned: Boolean,
    ) = updateFieldSettings(target, optimisticPinned = pinned) { sticky = pinned }

    suspend fun setFieldLanguageHint(
        target: EditorFieldTarget,
        locale: Locale?,
    ) = updateFieldSettings(target) { languageHint = locale }

    suspend fun setFieldToolbar(
        target: EditorFieldTarget,
        toolbar: EditorFieldToolbar,
    ) {
        val current = state.value?.takeIf { target.matches(it) && !it.isSaving } ?: return
        val fieldKey = NotetypeJson(current.notetypeJson).fields[target.field].editorKey()
        withCol {
            requireCurrentCollection()
            val type = requireNotNull(notetypes.get(current.noteTypeId))
            check(target.field in 0 until type.fields.size && type.fields[target.field].editorKey() == fieldKey) {
                "The field changed; reopen its options."
            }
        }
        if (!target.matches(state.value)) return
        withContext(ioDispatcher) {
            toolbarPreferences.save(requireNotNull(collectionKey), current.noteTypeId, fieldKey, toolbar)
        }
        state.value?.takeIf(target::matches)?.let { latest ->
            mutableState.value =
                latest.copy(
                    fields =
                        latest.fields.mapIndexed { index, field ->
                            if (index == target.field) field.copy(toolbar = toolbar) else field
                        },
                )
        }
    }

    suspend fun setCustomButtonTemplates(buttons: List<EditorCustomToolbarButton>) {
        if (state.value?.isSaving != false) return
        withContext(ioDispatcher) { toolbarPreferences.saveCustomButtons(buttons) }
        val byId = buttons.associateBy { it.id }
        mutableState.value =
            state.value?.let { latest ->
                latest.copy(
                    customButtonTemplates = buttons,
                    fields =
                        latest.fields.map { field ->
                            field.copy(toolbar = field.toolbar.copy(customButtons = field.toolbar.customButtons.mapNotNull { byId[it.id] }))
                        },
                )
            }
    }

    private suspend fun updateFieldSettings(
        target: EditorFieldTarget,
        optimisticPinned: Boolean? = null,
        update: Field.() -> Unit,
    ) {
        val current = state.value?.takeIf { target.matches(it) && !it.isSaving } ?: return
        if (optimisticPinned != null) {
            mutableState.value =
                current.copy(
                    fields =
                        current.fields.mapIndexed { i, field ->
                            if (i == target.field) {
                                field.copy(sticky = optimisticPinned)
                            } else {
                                field
                            }
                        },
                )
        }
        var persisted = false
        try {
            val updated =
                withCol {
                    requireCurrentCollection()
                    if (!target.matches(state.value) || state.value?.isSaving != false) return@withCol null
                    val type = requireNotNull(notetypes.get(current.noteTypeId)).deepClone()
                    require(type.fields.map { it.name } == current.fields.map { it.name }) { "The note type changed; reopen the editor." }
                    val field = type.fields[target.field]
                    field.update()
                    notetypes.save(type)
                    field.sticky to field.languageHint?.toLanguageTag()
                } ?: return
            persisted = true
            state.value?.takeIf(target::matches)?.let { latest ->
                mutableState.value =
                    latest.copy(
                        fields =
                            latest.fields.mapIndexed { i, f ->
                                if (i == target.field) f.copy(sticky = updated.first, languageTag = updated.second) else f
                            },
                    )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(e)
        } finally {
            if (!persisted && optimisticPinned != null) {
                state.value?.takeIf(target::matches)?.let { latest ->
                    mutableState.value =
                        latest.copy(
                            fields =
                                latest.fields.mapIndexed { i, field ->
                                    if (i == target.field && field.sticky == optimisticPinned) {
                                        field.copy(sticky = current.fields[i].sticky)
                                    } else {
                                        field
                                    }
                                },
                        )
                }
            }
        }
    }

    /**
     * Own the operation in the ViewModel so recreation cannot cancel between the backend commit and
     * advancing to the next Add document. The current host handles and consumes [saveResult].
     */
    suspend fun save(
        snapshotHtml: List<String>,
        allowMissingCloze: Boolean = false,
    ): EditorSaveResult {
        val operation =
            activeSave?.takeIf { it.isActive }
                ?: viewModelScope
                    .async(start = CoroutineStart.UNDISPATCHED) {
                        saveSnapshot(snapshotHtml, allowMissingCloze).also { mutableSaveResult.value = it }
                    }.also { activeSave = it }
        return operation.await()
    }

    fun consumeSaveResult() {
        mutableSaveResult.value = null
    }

    private suspend fun saveSnapshot(
        snapshotHtml: List<String>,
        allowMissingCloze: Boolean,
    ): EditorSaveResult {
        val current = state.value?.takeUnless { it.isSaving } ?: return EditorSaveResult.Invalid(null)
        if (current.removedFields.isNotEmpty()) return EditorSaveResult.Invalid(null)
        if (snapshotHtml.size != current.fields.size) return EditorSaveResult.Invalid("The note fields changed; reopen the editor.")
        mutableState.value = current.copy(isSaving = true)
        try {
            val (note, noteChanged) =
                withCol {
                    requireCurrentCollection()
                    require(decks.getLegacy(current.deckId)?.isFiltered == false) { "The selected deck is no longer available." }
                    val note = if (current.isAdding) Note.fromNotetypeId(this, current.noteTypeId) else getNote(current.noteId)
                    require(
                        note.noteTypeId == current.noteTypeId && note.notetype.fields.map { it.name } == current.fields.map { it.name },
                    ) {
                        "The note type changed; reopen the editor."
                    }
                    val oldFields = note.fields.toList()
                    val oldTags = note.tags.toSet()
                    note.fields = snapshotHtml.toMutableList()
                    note.setTagsFromStr(this, current.tags.joinToString(" "))
                    note to (oldFields != snapshotHtml || oldTags != note.tags.toSet())
                }
            if (current.isAdding) {
                val check = checkNoteFieldsResponse(note)
                if (check is NoteFieldsCheckResult.Failure) {
                    val missingCloze = check.localizedMessage == TR.addingYouHaveAClozeDeletionNote()
                    if (!allowMissingCloze || !missingCloze) {
                        return EditorSaveResult.Invalid(check.localizedMessage, missingCloze, check.fieldIndex)
                    }
                }
                val changes =
                    undoableOp {
                        requireCurrentCollection()
                        addNote(note, current.deckId)
                    }
                val next =
                    current.copy(
                        generation = current.generation + 1,
                        resetFieldBaseline = true,
                        baselineFieldMap = null,
                        fields =
                            current.fields.mapIndexed {
                                index,
                                field,
                                ->
                                field.copy(html = if (field.sticky) snapshotHtml[index] else "")
                            },
                        tags = note.tags.toList(),
                        customButtonTemplates = toolbarPreferences.loadCustomButtons(),
                        isSaving = false,
                        hasMetadataChanges = false,
                    )
                baseline = next.metadata()
                mutableState.value = next
                mutableError.value = null
                return EditorSaveResult.Added(changes.count)
            }
            val needsDeckChange =
                withCol {
                    requireCurrentCollection()
                    getCard(requireNotNull(current.cardId)).currentDeckId() != current.deckId
                }
            if (needsDeckChange) {
                undoableOp {
                    requireCurrentCollection()
                    setDeck(affectedCardIds, current.deckId)
                }
            }
            if (noteChanged) {
                undoableOp {
                    requireCurrentCollection()
                    updateNote(note)
                }
            }
            val saved =
                current.copy(
                    generation = current.generation + 1,
                    resetFieldBaseline = true,
                    baselineFieldMap = null,
                    fields = current.fields.mapIndexed { index, field -> field.copy(html = snapshotHtml[index]) },
                    tags = note.tags.toList(),
                    hasMetadataChanges = false,
                    isSaving = false,
                )
            baseline = saved.metadata()
            mutableState.value = saved
            mutableError.value = null
            return EditorSaveResult.Saved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(e)
            return EditorSaveResult.Invalid(e.localizedMessage)
        } finally {
            mutableState.value = mutableState.value?.copy(isSaving = false)
        }
    }

    /** Store this opaque JSON in the same IndexedDB transaction as fields and their clean baseline. */
    fun hostState(): String {
        val current = requireNotNull(state.value)
        return JSONObject()
            .put("version", 1)
            .put("generation", current.generation)
            .put("collection", collectionKey)
            .put("adding", current.isAdding)
            .put("note", current.noteId)
            .put("card", current.cardId ?: 0L)
            .put("cards", JSONArray(affectedCardIds))
            .put("fields", JSONArray(current.fields.map { it.name }))
            .put("fieldKeys", JSONArray(NotetypeJson(current.notetypeJson).fields.map { it.editorKey() }))
            .put("fieldSchema", NotetypeJson(current.notetypeJson).jsonObject.getJSONArray("flds").toString())
            .put("removedFields", JSONArray(current.removedFields.map { JSONObject().put("name", it.name).put("html", it.html) }))
            .put("current", current.metadata().json())
            .put("baseline", requireNotNull(baseline).json())
            .toString()
    }

    /** Reject incompatible recovery rather than applying a draft to another note or collection. */
    suspend fun restoreHostState(
        json: String,
        snapshotHtml: List<String>,
    ): Boolean {
        val current = state.value?.takeUnless { it.isSaving } ?: return false
        try {
            val stored = JSONObject(json)
            require(stored.getInt("version") == 1 && stored.getString("collection") == collectionKey)
            require(stored.getBoolean("adding") == current.isAdding && stored.getLong("note") == current.noteId)
            require(stored.getLong("card") == (current.cardId ?: 0L))
            require(stored.getJSONArray("cards").longs() == affectedCardIds)
            val metadata = Metadata.from(stored.getJSONObject("current"))
            val clean = Metadata.from(stored.getJSONObject("baseline"))
            val restored =
                withCol {
                    requireCurrentCollection()
                    val type = requireNotNull(notetypes.get(metadata.noteTypeId))
                    require(!type.isImageOcclusion)
                    val oldNames = stored.getJSONArray("fields").strings()
                    val oldKeys = stored.optJSONArray("fieldKeys")?.strings()
                    if (oldKeys == null) require(type.fields.map { it.name } == oldNames)
                    stored.optString("fieldSchema").takeIf { it.isNotEmpty() }?.let { schema ->
                        val fields = JSONArray(schema).let { array -> List(array.length()) { Field(array.getJSONObject(it)) } }
                        require(fields.map { it.name } == oldNames && fields.map { it.editorKey() } == oldKeys)
                    }
                    val mapping = mapEditorFields(oldKeys ?: type.fields.map { it.editorKey() }, oldNames, snapshotHtml, type)
                    val changedFields =
                        stored.has("fieldSchema") &&
                            stored.getString("fieldSchema") != type.jsonObject.getJSONArray("flds").toString()
                    val removed =
                        stored
                            .optJSONArray("removedFields")
                            ?.let { values ->
                                List(values.length()) { index ->
                                    val value = values.getJSONObject(index)
                                    EditorRemovedField(value.getString("name"), value.getString("html"))
                                }
                            }.orEmpty()
                    if (!current.isAdding) require(getNote(current.noteId).noteTypeId == type.id)
                    require(decks.getLegacy(metadata.deckId)?.isFiltered == false)
                    current.copy(
                        generation = stored.getInt("generation").also { require(it >= 0) } + if (changedFields) 1 else 0,
                        baselineFieldMap = if (changedFields) mapping.indices else null,
                        removedFields = removed + mapping.removed,
                        resetFieldBaseline = false,
                        deckId = metadata.deckId,
                        deckName = decks.name(metadata.deckId),
                        noteTypeId = type.id,
                        noteTypeName = type.name,
                        notetypeJson = type.toString(),
                        isCloze = type.isCloze,
                        fields = type.editorFields(mapping.html),
                        tags = metadata.tags,
                        hasMetadataChanges = metadata != clean,
                    )
                }
            baseline = clean
            mutableState.value = restored
            mutableError.value = null
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Unable to restore Compose note editor metadata")
            return false
        }
    }

    fun clearError() {
        mutableError.value = null
    }

    private fun updateMetadata(updated: EditorState) {
        mutableState.value = updated.copy(hasMetadataChanges = updated.metadata() != baseline)
        mutableError.value = null
    }

    private fun report(exception: Exception) {
        Timber.w(exception, "Compose note editor operation failed")
        mutableError.value = exception.localizedMessage ?: exception.javaClass.simpleName
    }

    private fun Collection.requireCurrentCollection() {
        check(editorCollectionKey() == collectionKey) { "The collection changed; reopen the editor." }
    }

    private fun NotetypeJson.editorFields(html: List<String>): List<EditorFieldState> =
        fields.mapIndexed { index, field ->
            EditorFieldState(
                name = field.name,
                html = html[index],
                font = field.font,
                fontSize = field.fontSize,
                rtl = field.jsonObject.optBoolean("rtl"),
                languageTag = field.languageHint?.toLanguageTag(),
                sticky = field.sticky,
                collapsed = field.jsonObject.optBoolean("collapsed"),
                sourceMode = field.jsonObject.optBoolean("plainText"),
                toolbar = toolbarPreferences.load(requireNotNull(collectionKey), id, field.editorKey()),
            )
        }

    private fun EditorState.metadata() = Metadata(deckId, noteTypeId, tags)

    private data class Metadata(
        val deckId: Long,
        val noteTypeId: Long,
        val tags: List<String>,
    ) {
        fun json(): JSONObject = JSONObject().put("deck", deckId).put("type", noteTypeId).put("tags", JSONArray(tags))

        companion object {
            fun from(json: JSONObject): Metadata = Metadata(json.getLong("deck"), json.getLong("type"), json.getJSONArray("tags").strings())
        }
    }

    companion object {
        private const val SESSION_KEY = "composeEditorSession"
    }
}

private fun Collection.editorCollectionKey(): String =
    (collectionFiles as? CollectionFiles.FolderBasedCollection)?.colDb?.absolutePath ?: ":memory:"

private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

private fun JSONArray.longs(): List<Long> = List(length()) { getLong(it) }

private fun Bundle.sharedFields(): List<String>? =
    when {
        containsKey(Intent.EXTRA_PROCESS_TEXT) -> listOf(getCharSequence(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty())
        containsKey(NoteEditorFragment.SOURCE_TEXT) || containsKey(NoteEditorFragment.TARGET_TEXT) ->
            listOf(getString(NoteEditorFragment.SOURCE_TEXT).orEmpty(), getString(NoteEditorFragment.TARGET_TEXT).orEmpty())
        containsKey(Intent.EXTRA_TEXT) || containsKey(Intent.EXTRA_SUBJECT) -> {
            val subject = getCharSequence(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
            val text = getCharSequence(Intent.EXTRA_TEXT)?.toString().orEmpty()
            if (subject.isEmpty()) listOf(text) else listOf(subject, text)
        }
        else -> null
    }
