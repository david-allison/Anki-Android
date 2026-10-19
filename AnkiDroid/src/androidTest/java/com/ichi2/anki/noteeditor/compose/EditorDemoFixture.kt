// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.graphics.Color
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
    val introduction: Note
    val languages: Note
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
                putString("composeEditorToolbarDisplay", "SCROLL")
            }.commit()
        toolbars.saveCustomButtons(listOf(star))
        val all = EditorToolbarButton.entries.map { it.name }.toSet()
        toolbars.savePresets(
            listOf(
                EditorToolbarPreset(
                    id = EditorToolbarPreset.DEFAULT_ID,
                    name = "",
                    hiddenButtons = all - setOf("BOLD", "ITALIC", "UNDERLINE", "UNDO", "REDO", "SOURCE_MODE", "MEDIA", "TEXT_COLOR"),
                ),
                EditorToolbarPreset(
                    id = "demo-writing",
                    name = "Writing",
                    hiddenButtons = all - setOf("BOLD", "ITALIC", "SOURCE_MODE"),
                    buttonOrder = listOf("BOLD", "ITALIC", "SOURCE_MODE", "custom:${star.id}", "UNDERLINE"),
                ),
                EditorToolbarPreset(id = "demo-source", name = "Source", hiddenButtons = all - "SOURCE_MODE"),
            ),
        )
        val introductionType =
            type("Japanese vocabulary", listOf("Front", "Back")) {
                fields.forEach { it.fontSize = 20 }
                fields[0].languageHint = Locale.ENGLISH
                fields[1].languageHint = Locale.JAPANESE
            }
        introduction = note(introductionType, "Memorization", "")
        val languageType =
            type("Languages", listOf("Topic", "Chinese", "Traditional Chinese", "Japanese")) {
                fields[0].jsonObject.put("collapsed", true)
                listOf("zh-Hans-CN", "zh-Hant-TW", "ja").forEachIndexed { i, tag ->
                    fields[i + 1].fontSize = 36
                    fields[i + 1].languageHint = Locale.forLanguageTag(tag)
                }
            }
        languages = note(languageType, "Love", "", "", "")
        val styleType =
            type("Field styles", listOf("Text", "HTML", "Details", "Arabic")) {
                fields[0].fontSize = 24
                fields[0].description = "Write a memorable sentence."
                fields[1].jsonObject.put("plainText", true)
                fields[2].jsonObject.put("collapsed", true)
                fields[3].jsonObject.put("rtl", true)
                fields.forEach { it.languageHint = Locale.ENGLISH }
            }
        styles =
            note(styleType, "A star worth remembering.", "<b>A memorable example.</b>", "One picture can be a useful memory cue.", "الحب")
        toolbars.selectPreset(collectionKey, styleType.id, styleType.fields[1].editorKey(), "demo-source")
        toolbars.saveToolbarVisibility(collectionKey, styleType.id, styleType.fields[3].editorKey(), false)
        val drawingType =
            type("Drawing", listOf("Prompt", "Sketch")) {
                fields[0].sticky = true
                fields[1].description = "Draw something memorable."
                fields.forEach { it.languageHint = Locale.ENGLISH }
            }
        drawing = note(drawingType, "Draw the Anki star", "")
        toolbars.saveQuickActions(collectionKey, drawingType.id, drawingType.fields[1].editorKey(), listOf("drawing"))
        toolbars.saveToolbarVisibility(collectionKey, drawingType.id, drawingType.fields[1].editorKey(), false)

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
