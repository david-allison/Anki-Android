// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.graphics.Color
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.browser.BrowserColumnCollection
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.libanki.BrowserConfig
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.CollectionFiles
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardRepository
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardTool
import org.json.JSONArray
import java.util.Locale

/** All synthetic content is prepared before the recording starts. */
internal class EditorDemoFixture(
    private val col: Collection,
) {
    val deckId = col.decks.id("Demo")
    val star = EditorCustomToolbarButton("demo-star", "★", "<span style=\"color: #0288d1;\">★ ", "</span>")
    val highlight = EditorCustomToolbarButton("demo-highlight", "Highlight", "<mark>", "</mark>")
    val introductionType: NotetypeJson
    val styles: Note
    val drawing: Note
    private val toolbars = EditorToolbarPreferences(Prefs.sharedPrefs)
    private val collectionKey = (col.collectionFiles as? CollectionFiles.FolderBasedCollection)?.colDb?.absolutePath ?: ":memory:"

    init {
        Prefs.isComposeNoteEditorEnabled = true
        Prefs.sharedPrefs
            .edit()
            .apply {
                Prefs.sharedPrefs.all.keys
                    .filter { it.startsWith("composeEditor") }
                    .forEach(::remove)
                remove("note_editor_custom_buttons")
                putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true)
                putString("composeEditorToolbarDisplay", "SCROLL")
            }.commit()
        toolbars.saveCustomButtons(listOf(star, highlight))
        val all = EditorToolbarButton.entries.map { it.name }.toSet()
        toolbars.savePresets(
            listOf(
                EditorToolbarPreset(
                    id = EditorToolbarPreset.DEFAULT_ID,
                    name = "",
                    hiddenButtons = all - setOf("BOLD", "ITALIC", "UNDERLINE", "UNDO", "REDO", "SOURCE_MODE", "MEDIA", "TEXT_COLOR"),
                    buttonOrder = listOf("custom:${star.id}") + all,
                ),
                EditorToolbarPreset(
                    id = "demo-writing",
                    name = "Writing",
                    hiddenButtons = all - setOf("BOLD", "ITALIC", "SOURCE_MODE"),
                    customButtonIds = listOf(star.id),
                    buttonOrder = listOf("custom:${star.id}", "BOLD", "ITALIC", "SOURCE_MODE") + all,
                ),
                EditorToolbarPreset(id = "demo-source", name = "Source", hiddenButtons = all - "SOURCE_MODE"),
            ),
        )
        introductionType =
            type("English/Japanese", listOf("English", "Japanese")) {
                fields[1].languageHint = Locale.JAPANESE
            }
        val styleType =
            type("Astronomy", listOf("Question", "Answer", "Notes", "Arabic answer")) {
                fields[0].description = "Ask one clear question."
                fields[2].jsonObject.put("collapsed", true)
                fields[3].jsonObject.put("rtl", true)
            }
        styles =
            note(styleType, "Which star is closest to Earth?", "<b>The Sun</b>", "Sunlight takes about 8 minutes to reach Earth.", "الشمس")
        toolbars.selectPreset(collectionKey, styleType.id, styleType.fields[1].editorKey(), "demo-source")
        toolbars.saveToolbarVisibility(collectionKey, styleType.id, styleType.fields[3].editorKey(), false)
        val drawingType = type("App icons", listOf("Logo", "App")) {}
        drawing = note(drawingType, "", "AnkiDroid")
        toolbars.saveQuickActions(collectionKey, drawingType.id, drawingType.fields[0].editorKey(), listOf("drawing"))
        toolbars.saveToolbarVisibility(collectionKey, drawingType.id, drawingType.fields[0].editorKey(), false)
        col.tags.setCollapsed("Japanese", true)
        col.decks.select(deckId)
        // Use the backend's real Add defaults, then remove this off-camera setup note.
        val setup = note(introductionType, "Preparation", "")
        col.removeNotes(listOf(setup.id))
        check(col.defaultsForAdding().notetypeId == introductionType.id)

        BrowserColumnCollection.save(
            Prefs.sharedPrefs,
            CardsOrNotes.CARDS,
            BrowserColumnCollection(listOf(CardBrowserColumn.SFLD, CardBrowserColumn.NOTE_TYPE)),
        )
        col.config.set(BrowserConfig.sortColumnKey(false), "noteCrt")
        col.config.set(BrowserConfig.sortBackwardsKey(false), false)
        WhiteboardRepository(Prefs.sharedPrefs).apply {
            saveBrushes(
                listOf(
                    WhiteboardTool.Brush(Color.rgb(70, 80, 90), 7f),
                    WhiteboardTool.Brush(Color.rgb(2, 136, 209), 12f),
                ),
                isDarkMode = false,
            )
            saveLastActiveBrushIndex(0, isDarkMode = false)
            stylusOnlyMode = false
        }
    }

    private fun type(
        name: String,
        names: List<String>,
        configure: NotetypeJson.() -> Unit,
    ): NotetypeJson {
        val type = col.notetypes.newBasicNotetype()
        type.name = name
        type.jsonObject.put("flds", JSONArray(names.map { col.notetypes.newField(it).jsonObject }))
        val template = type.templates[0].jsonObject
        template.put("qfmt", "{{${names.first()}}}")
        template.put("afmt", "{{FrontSide}}<hr id=answer>" + names.drop(1).joinToString("<br>") { "{{$it}}" })
        type.jsonObject.put(
            "css",
            ".card { font-family: sans-serif; font-size: 28px; text-align: center; } img { max-width: 100%; max-height: 70vh; }",
        )
        type.configure()
        col.notetypes.save(type)
        return type
    }

    private fun note(
        type: NotetypeJson,
        vararg values: String,
    ): Note =
        col.newNote(type).also { note ->
            values.forEachIndexed { index, value -> note.fields[index] = value }
            col.addNote(note, deckId)
        }
}
