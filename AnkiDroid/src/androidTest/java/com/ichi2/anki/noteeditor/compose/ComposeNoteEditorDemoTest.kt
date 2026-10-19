// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.app.Activity
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.core.text.HtmlCompat
import androidx.core.view.descendants
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.backend.createDatabaseUsingRustBackend
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.libanki.Storage
import com.ichi2.anki.noteeditor.compose.preview.EditorPreviewFragment
import com.ichi2.anki.noteeditor.web.WebEditorStatus
import com.ichi2.anki.noteeditor.web.WebEditorView
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.evaluate
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.utils.LanguageUtil
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt-in, real-touch walkthrough. See docs/compose-note-editor-demo-script.md. */
class ComposeNoteEditorDemoTest : InstrumentedTest() {
    @get:Rule(order = Int.MIN_VALUE)
    val manualDemoOnly =
        TestRule { base, _ ->
            object : Statement() {
                override fun evaluate() {
                    assumeTrue("Demo recording is disabled in CI", !BuildConfig.CI)
                    assumeTrue("Opt in with -e editorDemo true", InstrumentationRegistry.getArguments().getString("editorDemo") == "true")
                    base.evaluate()
                }
            }
        }

    @get:Rule
    val runtimePermissions = grantPermissions(storagePermission, notificationPermission)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var recording: EditorDemoRecording
    private lateinit var fixture: EditorDemoFixture
    private val lastStatus = AtomicReference<WebEditorStatus>()
    private val keyboardPoints = mutableMapOf<String, Point>()
    private var kanaFlickDistance = 0
    private lateinit var addSavePoint: Point
    private lateinit var addBackPoint: Point

    @Test
    fun recordEditorWalkthrough() {
        check(InstrumentedDemoEnvironment.isAllowed()) { "Run on an emulator using the .composefieldsprobe build" }
        ensureWebViewIsSupported()
        check(device.executeShellCommand("settings get secure default_input_method").contains("com.google.android.inputmethod.latin")) {
            "Prepare Gboard with English QWERTY as its default and Japanese 12-key kana before recording"
        }
        val originalPrefs = Prefs.sharedPrefs.all.toMap()
        val oldIdle = Configurator.getInstance().waitForIdleTimeout
        try {
            Configurator.getInstance().waitForIdleTimeout = 100
            LanguageUtil.setDefaultBackendLanguages("en")
            runBlocking { CollectionManager.discardBackend() }
            val demoDirectory =
                File(testContext.filesDir, "editor-demo-collection").apply {
                    check(deleteRecursively())
                    check(mkdirs())
                }
            Prefs.sharedPrefs
                .edit()
                .putString(CollectionHelper.PREF_COLLECTION_PATH, demoDirectory.absolutePath)
                .commit()
            CollectionHelper.initializeAnkiDroidDirectory(CollectionHelper.getCurrentAnkiDroidDirectory(testContext))
            CollectionManager.setColForTests(
                Storage.collection(
                    CollectionHelper.getCollectionPaths(testContext),
                    databaseBuilder = ::createDatabaseUsingRustBackend,
                    backend = CollectionManager.getBackend(),
                ),
            )
            LanguageUtil.setDefaultBackendLanguages()
            fixture = EditorDemoFixture(col)
            val section = InstrumentationRegistry.getArguments().getString("demoSection") ?: "all"
            require(section in setOf("all", "introduction", "styles", "drawing", "formatting"))
            val startsOnDeckPicker = section in setOf("all", "introduction")
            val intent =
                if (startsOnDeckPicker) {
                    Intent(testContext, DeckPicker::class.java)
                } else {
                    Intent(testContext, CardBrowser::class.java).putExtra(CardBrowserViewModel.EXTRA_DECK_ID, fixture.deckId)
                }
            ActivityScenario.launch<Activity>(intent).use {
                if (startsOnDeckPicker) prepareKeyboards() else visible(By.text("Which star is closest to Earth?"))
                EditorDemoRecording(device).use { take ->
                    recording = take
                    take.start()
                    if (section in setOf("all", "introduction")) introduction() else take.scene("Prepared note types", 1_000) {}
                    if (section == "all") {
                        take.scene("Open prepared examples") {
                            if (device.hasObject(By.res(testContext.packageName, "nav_browser"))) {
                                click(By.res(testContext.packageName, "nav_browser"))
                            } else {
                                click(By.desc(s(CommonString.drawer_open)))
                                click(By.text(s(CommonString.card_browser)))
                            }
                            visible(By.text("Which star is closest to Earth?"))
                        }
                    }
                    if (section in setOf("all", "styles")) styles()
                    if (section in setOf("all", "drawing")) drawing()
                    if (section in setOf("all", "formatting")) formatting()
                    take.finish()
                }
            }
        } finally {
            LanguageUtil.setDefaultBackendLanguages()
            Configurator.getInstance().waitForIdleTimeout = oldIdle
            Prefs.sharedPrefs
                .edit()
                .apply {
                    clear()
                    originalPrefs.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }.commit()
        }
    }

    /** Resolve key positions off camera, then return to the Deck Picker with an empty editor. */
    private fun prepareKeyboards() {
        openAddNote()
        addSavePoint = visible(By.desc(s(CommonString.save))).visibleCenter
        addBackPoint = visible(By.desc(s(androidx.appcompat.R.string.abc_action_bar_up_description))).visibleCenter
        selectDefaultKeyboard()
        for (letter in "memorizationlearningbyheart".toSet()) keyboardPoints[letter.toString()] = keyboardKey(letter.toString())
        keyboardPoints["Enter"] = keyboardKey("Enter")
        keyboardPoints["Space"] = keyboardKey("Space")
        tapField(1)
        val kana = visible(By.pkg(GBOARD).desc("アー"))
        keyboardPoints["あ"] = kana.visibleCenter
        kanaFlickDistance = kana.visibleBounds.width() / 2
        keyboardPoints["わ"] = keyboardKey("わ")
        keyboardPoints["か"] = keyboardKey("か")
        tapField(0)
        selectDefaultKeyboard()
        click(By.desc(s(androidx.appcompat.R.string.abc_action_bar_up_description)))
        visible(By.res(testContext.packageName, "fab_main"))
    }

    private fun selectDefaultKeyboard() {
        if (!device.hasObject(By.pkg(GBOARD).desc(Pattern.compile("(?i)q")))) {
            click(By.desc("Switch input method"))
            click(By.textContains("English (US)"))
            visible(By.pkg(GBOARD).desc(Pattern.compile("(?i)q")))
        }
    }

    private fun openAddNote() {
        click(By.res(testContext.packageName, "fab_main"))
        click(By.text(s(CommonString.menu_add)))
        awaitEditorReady()
        assertEquals(listOf("English", "Japanese"), state().fields.map { it.name })
        assertEquals(null, state().fields[0].languageTag)
    }

    private fun introduction() {
        recording.scene("Deck Picker", 1_000) {}
        recording.scene("Add English/Japanese note") { openAddNote() }
        hold(200)
        recording.scene("English: memorization and a new line") {
            tapField(0)
            visible(By.pkg(GBOARD).desc(Pattern.compile("(?i)m")))
            "memorization".forEach { tapKey(it.toString()) }
            tapKey("Enter")
            "learning by heart".forEach { tapKey(if (it == ' ') "Space" else it.toString()) }
            recording.markFrame("multiline-vocabulary")
        }
        recording.scene("Japanese: 暗記") {
            tapField(1)
            visible(By.pkg(GBOARD).desc("アー"))
            tapKey("あ")
            flickKey("わ", 0, -1)
            flickKey("か", -1, 0)
            chooseCandidate("暗記", field = 1)
            recording.markFrame("vocabulary")
        }
        recording.scene("Japanese tag") {
            hideKeyboard()
            click(By.text(s(R.string.compose_editor_no_tags)))
            click(By.res(testContext.packageName, "text_view").text("Japanese"))
            click(By.text(s(CommonString.dialog_confirm)))
            await("Japanese tag selected") { "Japanese" in state().tags }
            hold(750)
            recording.markFrame("tagged-vocabulary")
            tap(addSavePoint)
        }
        recording.scene("Return to Deck Picker") {
            await("Saved note and next empty Add form") { !state().isSaving && fieldText(0).isEmpty() }
            // Empty fields arrive before the editor finishes resetting and accepts navigation.
            visible(By.desc(s(CommonString.save)).enabled(true))
            tap(addBackPoint)
            visible(By.res(testContext.packageName, "fab_main"))
            val added = col.findNotes("note:\"English/Japanese\"").single().let(col::getNote)
            val english = HtmlCompat.fromHtml(added.fields[0], HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            assertTrue(english.equals("memorization\nlearning by heart", ignoreCase = true), english)
            assertEquals("暗記", added.fields[1])
            assertTrue("Japanese" in added.tags)
            recording.markFrame("deck-picker-after-add")
            hold(500)
        }
    }

    private fun tapKey(label: String) {
        val point = keyboardPoints.getValue(label)
        tap(point)
    }

    private fun flickKey(
        label: String,
        dx: Int,
        dy: Int,
    ) {
        val start = keyboardPoints.getValue(label)
        gesture(listOf(start, Point(start.x + dx * kanaFlickDistance, start.y + dy * kanaFlickDistance)), 100)
    }

    private fun chooseCandidate(
        character: String,
        field: Int,
    ) {
        var candidate: Point? = null
        await("Keyboard candidate $character") {
            candidate =
                instrumentation.uiAutomation.windows
                    .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                    .firstNotNullOfOrNull { window ->
                        window.root
                            ?.findAccessibilityNodeInfosByText(character)
                            ?.firstOrNull {
                                it.isVisibleToUser && it.text?.toString() == character
                            }?.let { node ->
                                Rect().also(node::getBoundsInScreen).let { Point(it.centerX(), it.centerY()) }
                            }
                    }
            candidate != null
        }
        recording.markFrame("keyboard-$field")
        tap(checkNotNull(candidate))
        await("Committed $character") { fieldText(field) == character && lastStatus.get()?.composing == false }
    }

    private fun styles() {
        recording.scene("Prepared Astronomy note") {
            openNote("Which star is closest to Earth?")
            hold(1_000)
        }
        recording.scene("Answer: normal to HTML and back") {
            tapField(1)
            assertTrue(!state().fields[1].sourceMode)
            hold(500)
            click(By.desc(s(R.string.compose_editor_source)))
            await("Answer in HTML mode") { lastStatus.get()?.let { it.activeField == 1 && it.sourceMode } == true }
            hold(1_000)
            click(By.desc(s(R.string.compose_editor_rich_text)))
            await("Answer back in normal mode") { lastStatus.get()?.let { it.activeField == 1 && !it.sourceMode } == true }
            hold(700)
            tapField(0)
        }
        recording.scene("All field options") {
            openOptions()
            val hierarchy = ByteArrayOutputStream().also(device::dumpWindowHierarchy).toString("UTF-8")
            EditorFieldOption.entries.forEach {
                val label = if (it == EditorFieldOption.KEYBOARD) R.string.compose_editor_language_hint else it.label
                assertTrue(hierarchy.contains(s(label)), "Missing field option: ${s(label)}")
            }
            hold(2_000)
        }
        recording.scene("Font size") {
            val defaultSize = state().fields[0].fontSize
            click(By.descContains(s(R.string.compose_editor_field_font)))
            assertEquals(defaultSize.toString(), visible(By.clazz("android.widget.EditText")).text)
            hold(1_500)
            click(By.text(s(CommonString.dialog_cancel)))
            assertEquals(defaultSize, state().fields[0].fontSize)
        }
        recording.scene("Description") {
            click(By.text(s(R.string.compose_editor_field_description)))
            visible(By.text("Ask one clear question."))
            hold(1_500)
            click(By.text(s(CommonString.dialog_cancel)))
        }
        recording.scene("Pin and choose Arabic answer") {
            click(By.desc(s(R.string.compose_editor_pin_field)))
            await("Text pinned") { state().fields[0].sticky }
            chooseField("Arabic answer")
            closeOverview()
        }
        recording.scene("Collapsed and RTL fields") {
            val notesToggle = "document.querySelector('.field-container[data-index=\"2\"] .collapse-toggle')"
            assertEquals("false", web().evaluate("$notesToggle.getAttribute('aria-expanded') === 'true'"))
            tapElement(notesToggle)
            await("Notes expanded") { web().evaluate("$notesToggle.getAttribute('aria-expanded') === 'true'") == "true" }
            assertEquals("Sunlight takes about 8 minutes to reach Earth.", fieldText(2))
            assertTrue(state().fields[3].rtl)
            assertTrue(!state().fields[3].toolbar.showToolbar)
            // Expanding Notes only reveals its content. Let it register without opening an IME.
            assertTrue(!onMain { web().keyboardVisible })
            recording.markFrame("notes-expanded")
            hold(1_500)
            tapField(0)
        }
        recording.scene("Toolbar layout") {
            hideKeyboard()
            click(By.desc(s(R.string.compose_editor_options)))
            click(By.text(s(R.string.compose_editor_toolbar_layout)))
            listOf(R.string.compose_editor_toolbar_scroll, R.string.compose_editor_toolbar_stack, R.string.compose_editor_toolbar_hidden)
                .forEach { visible(By.text(s(it))) }
            hold(750)
            click(By.text(s(R.string.compose_editor_toolbar_stack)))
            hold(700)
        }
        recording.scene("Customize the current toolbar") {
            openOptions()
            hold(750)
            click(By.text(s(R.string.compose_editor_toolbar)))
            visible(By.text(s(R.string.compose_editor_default_toolbar)))
            hold(1_000)
        }
        recording.scene("Available to Active, then reorder") {
            val boldHandle = By.desc(s(R.string.compose_editor_reorder_button, s(R.string.compose_editor_bold)))
            val sourceHandle = By.desc(s(R.string.compose_editor_reorder_button, s(R.string.compose_editor_source)))
            val starHandle = By.desc(s(R.string.compose_editor_reorder_button, "★"))
            dragHandle("★", visible(sourceHandle).visibleCenter.y)
            visible(By.clickable(true).checked(true).hasDescendant(By.text("★")))
            dragHandle("★", visible(boldHandle).visibleBounds.top)
            await("Star reordered above Bold") { visible(starHandle).visibleCenter.y < visible(boldHandle).visibleCenter.y }
            recording.markFrame("star-reordered")
            hold(750)
        }
        recording.scene("Change toolbar preset") {
            click(By.text(s(R.string.compose_editor_default_toolbar)))
            click(By.text("Writing"))
            hold(750)
            click(By.desc(s(R.string.compose_editor_preset_options, "Writing")))
            visible(By.text(s(R.string.compose_editor_add_preset)))
            hold(800)
            device.pressBack()
        }
        recording.scene("Available custom button") {
            scrollToolbarTo(fixture.highlight.label)
            visible(By.clickable(true).checked(false).hasDescendant(By.text(fixture.highlight.label)))
            recording.markFrame("available-custom-button")
            hold(1_000)
        }
        recording.scene("Custom button definition") {
            click(By.desc(s(R.string.compose_editor_custom_buttons)))
            click(By.text("★"))
            visible(By.text(fixture.star.prefix))
            visible(By.text(fixture.star.suffix))
            hold(2_000)
            click(By.text(s(CommonString.dialog_cancel)))
            click(By.desc(s(R.string.compose_editor_toolbar)))
            click(By.desc(s(CommonString.save)))
            await("Writing saved") {
                state()
                    .fields[0]
                    .toolbar.customButtons
                    .any { it.id == fixture.star.id }
            }
            assertEquals(
                "custom:${fixture.star.id}",
                state()
                    .fields[0]
                    .toolbar.buttonOrder
                    .first(),
            )
            val defaultPreset = EditorToolbarPreferences(Prefs.sharedPrefs).loadPresets().first { it.id == EditorToolbarPreset.DEFAULT_ID }
            assertEquals("custom:${fixture.star.id}", defaultPreset.buttonOrder.first())
            assertTrue(fixture.star.id in defaultPreset.customButtonIds)
        }
        recording.scene("Quick actions drag") {
            openOptions()
            click(By.text(s(R.string.compose_editor_quick_actions)))
            dragHandle(s(CommonString.drawing), visible(By.text(s(CommonString.user_active_columns))).visibleCenter.y + 50)
            click(By.desc(s(R.string.compose_editor_field_options)))
            closeOverview()
            await("Drawing quick action saved") { "drawing" in state().fields[0].toolbar.quickActions }
        }
        recording.scene("Long-press menu and custom button") {
            tapField(0)
            visible(By.pkg(GBOARD).desc("q"))
            val word = """(() => {
                const input = ${richField(0)};
                const node = [...input.childNodes].find(n => n.nodeType === Node.TEXT_NODE && n.textContent.includes('star'));
                const range = document.createRange(); const start = node.textContent.indexOf('star');
                range.setStart(node, start); range.setEnd(node, start + 4); return range;
            })()"""
            var point = elementPoint(word)
            await("Selection target settled after keyboard opened") {
                hold(100)
                val next = elementPoint(word)
                val settled = next == point
                point = next
                settled
            }
            gesture(listOf(point, point), 700)
            visible(By.text(Pattern.compile("(?i)copy")))
            hold(1_000)
            val starButton = visible(By.text("★")).visibleCenter
            hold(400)
            // A deliberate press makes the toolbar action and its touch dot easy to follow.
            gesture(listOf(starButton, starButton), 200)
            await("Custom star wrapped selection") { fieldText(0).contains("★ star") }
            hold(2_000)
            recording.markFrame("custom-button")
        }
        recording.scene("Save field styles") { saveNote() }
        assertTrue(col.getNote(fixture.styles.id).fields[0].contains("★ star"))
    }

    private fun drawing() {
        recording.scene("Open Drawing") {
            openNote("App icons")
            tapField(1)
            hideKeyboard()
        }
        recording.scene("Drawing from an unselected field") {
            assertEquals(1, runBlocking { web().captureTarget() }?.field)
            click(By.text("${s(CommonString.drawing)}: Logo"))
            visible(By.res(testContext.packageName, "whiteboard_view"))
        }
        recording.scene("Card outline and blue Anki star") {
            val canvas = visible(By.res(testContext.packageName, "whiteboard_view")).visibleBounds
            val centerX = canvas.exactCenterX()
            val centerY = canvas.top + canvas.height() * 0.43f
            val width = canvas.width() * 0.65f
            val height = width * 1.3f

            fun point(
                x: Double,
                y: Double,
            ): Point {
                val angle = -8.0 * PI / 180
                return Point(
                    (centerX + x * cos(angle) - y * sin(angle)).roundToInt(),
                    (centerY + x * sin(angle) + y * cos(angle)).roundToInt(),
                )
            }
            val cardStrokes =
                listOf(
                    listOf(-0.46 to -0.50, 0.49 to -0.51, 0.50 to -0.46),
                    listOf(0.50 to -0.46, 0.51 to 0.50, 0.46 to 0.51),
                    listOf(0.46 to 0.51, -0.50 to 0.50, -0.50 to 0.45),
                    listOf(-0.50 to 0.45, -0.51 to -0.50, -0.46 to -0.50),
                )
            cardStrokes.forEachIndexed { index, stroke ->
                if (index > 0) hold(70)
                gesture(roundedStroke(stroke.map { (x, y) -> point(x * width, y * height) }, width * 0.05), 650)
            }
            click(By.text("12").clazz("android.widget.CompoundButton"))
            val star =
                listOf(
                    0.05 to -0.255,
                    0.10 to -0.13,
                    0.35 to -0.16,
                    0.18 to 0.035,
                    0.23 to 0.30,
                    -0.015 to 0.16,
                    -0.28 to 0.32,
                    -0.20 to 0.035,
                    -0.39 to -0.09,
                    -0.14 to -0.13,
                    0.0 to -0.38,
                    0.05 to -0.255,
                ).map { (x, y) -> point(x * width, y * width) }
            gesture(roundedStroke(star, width * 0.035), 4_500)
            hold(1_500)
            recording.markFrame("star-on-card")
        }
        recording.scene("Drawing inserted into Logo") {
            click(By.res(testContext.packageName, "action_save"))
            visible(By.text(s(CommonString.multimedia_editor_field_editing_done)))
            hold(600)
            click(By.text(s(CommonString.multimedia_editor_field_editing_done)))
            await("Return from drawing") {
                onMain {
                    ActivityLifecycleMonitorRegistry
                        .getInstance()
                        .getActivitiesInStage(
                            Stage.RESUMED,
                        ).singleOrNull() is ComposeNoteEditorActivity
                }
            }
            await("Drawing inserted") { runBlocking { web().snapshot().fields[0] }.contains("<img") }
            assertEquals("AnkiDroid", fieldText(1))
            hideKeyboard()
            hold(2_000)
        }
        recording.scene("Finished card preview") {
            click(By.desc(s(CommonString.card_editor_preview_card)))
            var preview: WebView? = null
            await("Question image loaded") {
                preview = cardPreview()
                preview?.evaluate("document.querySelector('#qa img')?.naturalWidth > 0") == "true"
            }
            assertEquals("false", checkNotNull(preview).evaluate("document.querySelector('#qa').textContent.includes('AnkiDroid')"))
            recording.markFrame("logo-question")
            hold(2_000)
            click(By.text(s(CommonString.show_answer)))
            await("App name revealed") {
                checkNotNull(preview).evaluate("document.querySelector('#qa').textContent.includes('AnkiDroid')") == "true"
            }
            hold(4_000)
            recording.markFrame("finished-card")
            click(By.desc(s(androidx.appcompat.R.string.abc_action_bar_up_description)))
        }
        recording.scene("Save drawing") {
            saveNote()
            hold(750)
        }
        assertTrue(col.getNote(fixture.drawing.id).fields[0].contains("<img"))
    }

    private fun formatting() {
        recording.scene("Add a quick toolbar demo") {
            click(By.res(testContext.packageName, "action_add_note_from_card_browser"))
            awaitEditorReady()
            click(By.text(state().noteTypeName))
            click(By.text("Basic"))
            await("Basic selected") { state().noteTypeId == col.notetypes.basic.id }
            awaitEditorReady()
            tapField(0)
            typeShortText("Toolbar demo")
            await("Demo title entered") { fieldText(0) == "Toolbar demo" }
            hideKeyboard()
        }
        recording.scene("Alignment dialog") {
            click(By.desc(s(R.string.compose_editor_alignment)))
            visible(By.text(TR.editingCenter()))
            recording.markFrame("alignment-dialog")
            hold(750)
            click(By.text(TR.editingCenter()))
            await("Demo title centered") {
                web().evaluate("getComputedStyle(${richField(0)}.querySelector('div') ?? ${richField(0)}).textAlign === 'center'") == "true"
            }
            recording.markFrame("centered-title")
            hold(500)
            tapField(1)
            hideKeyboard()
        }
        recording.scene("Equations: LaTeX preview") {
            click(By.desc(s(R.string.compose_editor_equations)))
            click(By.clazz("android.widget.EditText"))
            typeShortText("H_2O")
            hideKeyboard()
            awaitEquationPreview()
            recording.markFrame("latex-preview")
            hold(750)
        }
        recording.scene("Equations: Chemistry H2O preview") {
            click(By.text(s(R.string.compose_editor_math_chemistry)))
            click(By.clazz("android.widget.EditText"))
            device.pressKeyCode(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
            typeShortText("H2O")
            hideKeyboard()
            awaitEquationPreview()
            recording.markFrame("chemistry-preview")
            hold(1_000)
            click(By.text(s(CommonString.dialog_ok)))
            await("Chemistry equation inserted") { runBlocking { web().snapshot().fields[1] }.contains("\\ce{H2O}") }
            await("Equation rendered in the editor") {
                web().evaluate("${richField(1)}.querySelector('img[data-anki=mathjax]')?.naturalWidth > 0") == "true"
            }
            hold(500)
        }
        recording.scene("Toolbar demo card preview") {
            click(By.desc(s(CommonString.card_editor_preview_card)))
            var preview: WebView? = null
            await("Demo question loaded") {
                preview = cardPreview()
                preview?.evaluate("document.querySelector('#qa')?.textContent.includes('Toolbar demo')") == "true"
            }
            hold(500)
            click(By.text(s(CommonString.show_answer)))
            await("H2O rendered on the card") {
                preview?.evaluate("!!document.querySelector('#qa mjx-container') && !document.querySelector('#qa mjx-merror')") == "true"
            }
            recording.markFrame("formatting-card")
            hold(2_000)
            click(By.desc(s(androidx.appcompat.R.string.abc_action_bar_up_description)))
        }
        recording.scene("Save the toolbar demo note") {
            click(By.desc(s(CommonString.save)).enabled(true))
            await("Demo note saved and Add form reset") { !state().isSaving && fieldText(0).isEmpty() }
            visible(By.desc(s(CommonString.save)).enabled(true))
            click(By.desc(s(androidx.appcompat.R.string.abc_action_bar_up_description)))
            visible(By.res(testContext.packageName, "action_add_note_from_card_browser"))
            val added = col.findNotes("\"Toolbar demo\"").single().let(col::getNote)
            assertEquals("Toolbar demo", HtmlCompat.fromHtml(added.fields[0], HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim())
            assertTrue(added.fields[0].contains("text-align: center"))
            assertTrue(added.fields[1].contains("\\ce{H2O}"))
            hold(500)
        }
    }

    /** Short source strings entered as paced key events into the focused control. */
    private fun typeShortText(text: String) {
        text.forEach {
            instrumentation.sendStringSync(it.toString())
            hold(35)
        }
    }

    private fun awaitEquationPreview() {
        await("Equation dialog preview rendered") {
            val preview =
                onMain {
                    WindowInspector
                        .getGlobalWindowViews()
                        .asSequence()
                        .filterIsInstance<ViewGroup>()
                        .flatMap { it.descendants }
                        .filterIsInstance<WebView>()
                        .firstOrNull { it.url?.endsWith("/equation-preview.html") == true }
                }
            preview?.evaluate("!!document.querySelector('#preview mjx-container') && !document.querySelector('#preview mjx-merror')") ==
                "true"
        }
    }

    private fun cardPreview(): WebView? =
        onMain {
            val root =
                (resumed() as ComposeNoteEditorActivity)
                    .supportFragmentManager.fragments
                    .filterIsInstance<EditorPreviewFragment>()
                    .firstOrNull()
                    ?.view as? ViewGroup
            root?.descendants?.filterIsInstance<WebView>()?.firstOrNull()
        }

    private fun openNote(label: String) {
        click(By.text(label))
        awaitEditorReady()
    }

    private fun awaitEditorReady() {
        await("Editor ready") { runCatching { web().evaluate("!!window.AnkiEditorFields") == "true" }.getOrDefault(false) }
        visible(By.desc(s(R.string.compose_editor_options)).enabled(true))
        lastStatus.set(null)
        onMain {
            val view = web()
            val nativeHandler = view.onChanged
            view.onChanged = { status ->
                lastStatus.set(status)
                nativeHandler?.invoke(status)
            }
        }
    }

    private fun saveNote() {
        hideKeyboard()
        click(By.desc(s(CommonString.save)).enabled(true))
        await("Return to browser") {
            onMain { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is CardBrowser } }
        }
    }

    private fun openOptions() {
        hideKeyboard()
        if (!device.hasObject(By.desc(s(R.string.compose_editor_options)))) {
            // Scrolling through fields collapses the native header.
            // Reveal it with the same downward gesture a viewer would use.
            gesture(
                listOf(
                    Point(device.displayWidth / 2, device.displayHeight * 2 / 5),
                    Point(device.displayWidth / 2, device.displayHeight * 3 / 5),
                ),
                175,
            )
        }
        click(By.desc(s(R.string.compose_editor_options)).enabled(true))
        click(By.text(s(R.string.compose_editor_field_options)))
        visible(By.desc(s(R.string.compose_editor_choose_field)))
    }

    private fun chooseField(name: String) {
        click(By.desc(s(R.string.compose_editor_choose_field)))
        click(By.text(name))
        visible(By.descContains(s(R.string.compose_editor_field_options_title, name)))
    }

    private fun closeOverview() {
        click(By.desc(s(androidx.appcompat.R.string.abc_action_bar_up_description)))
        await("Field panel closed") {
            onMain { web().importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO }
        }
    }

    private fun keyboardKey(key: String): Point {
        val pattern = Pattern.compile(Pattern.quote(key), Pattern.CASE_INSENSITIVE)
        return visible(By.pkg(GBOARD).desc(pattern)).visibleCenter
    }

    private fun hideKeyboard() {
        if (device.hasObject(By.pkg(GBOARD))) {
            device.pressBack()
            await("Keyboard hidden") { !device.hasObject(By.pkg(GBOARD)) }
        }
    }

    private fun dragHandle(
        label: String,
        targetY: Int,
    ) {
        val from = visible(By.desc(s(R.string.compose_editor_reorder_button, label))).visibleCenter
        gesture(listOf(from, Point(from.x, targetY)), 350)
        hold(200)
    }

    private fun scrollToolbarTo(label: String) {
        val handle = By.desc(s(R.string.compose_editor_reorder_button, label))
        repeat(8) {
            val bounds = visible(By.clazz("androidx.recyclerview.widget.RecyclerView")).visibleBounds
            val row = device.findObject(handle)?.visibleBounds
            if (row != null && row.top >= bounds.top && row.bottom <= bounds.bottom && row.height() >= row.width()) return
            gesture(
                listOf(Point(bounds.centerX(), bounds.bottom - 80), Point(bounds.centerX(), bounds.top + 80)),
                250,
            )
            hold(150)
        }
        error("Unable to expose toolbar button $label")
    }

    private fun tapField(index: Int) = tapElement("document.querySelector('.field-container[data-index=\"$index\"] .rich-text-editable')")

    private fun tapElement(expression: String) {
        repeat(8) {
            val point = elementPoint(expression)
            val bounds =
                onMain {
                    val v = web()
                    val p = IntArray(2)
                    v.getLocationOnScreen(p)
                    Rect(
                        p[0],
                        p[1],
                        p[0] + v.width,
                        p[1] + v.height,
                    )
                }
            if (point.y in (bounds.top + 20)..(bounds.bottom - 20)) {
                tap(point)
                hold(250)
                return
            }
            val down = point.y > bounds.bottom
            gesture(
                listOf(
                    Point(bounds.centerX(), if (down) bounds.bottom - 80 else bounds.top + 80),
                    Point(bounds.centerX(), if (down) bounds.top + 80 else bounds.bottom - 80),
                ),
                225,
            )
        }
        error("Unable to expose $expression")
    }

    private fun elementPoint(expression: String): Point {
        val view = web()
        val rect =
            JSONObject(
                view.evaluate(
                    "(() => { const r = ($expression).getBoundingClientRect(); return { x:r.x+r.width/2, y:r.y+Math.min(r.height/2,30), viewport:innerWidth }; })()",
                ),
            )
        return onMain {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val scale = view.width / rect.getDouble("viewport")
            Point((location[0] + rect.getDouble("x") * scale).roundToInt(), (location[1] + rect.getDouble("y") * scale).roundToInt())
        }
    }

    private fun gesture(
        points: List<Point>,
        duration: Long,
    ) {
        val down = SystemClock.uptimeMillis()

        fun inject(
            action: Int,
            x: Float,
            y: Float,
        ) {
            if (this::recording.isInitialized) recording.touch(action, x, y)
            MotionEvent
                .obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                .apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }.let { event ->
                    try {
                        // Stream movement at the requested pace; synchronize only at gesture boundaries.
                        check(instrumentation.uiAutomation.injectInputEvent(event, action != MotionEvent.ACTION_MOVE))
                    } finally {
                        event.recycle()
                    }
                }
        }
        inject(MotionEvent.ACTION_DOWN, points.first().x.toFloat(), points.first().y.toFloat())
        try {
            val steps = (duration / 16).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                val position = i.toDouble() / steps * (points.size - 1)
                val segment = position.toInt().coerceAtMost(points.lastIndex - 1)
                val fraction = position - segment
                val a = points[segment]
                val b = points[segment + 1]
                val scheduled = down + duration * i / steps
                val delay = scheduled - SystemClock.uptimeMillis()
                if (delay > 0) SystemClock.sleep(delay)
                inject(MotionEvent.ACTION_MOVE, (a.x + (b.x - a.x) * fraction).toFloat(), (a.y + (b.y - a.y) * fraction).toFloat())
            }
            inject(MotionEvent.ACTION_UP, points.last().x.toFloat(), points.last().y.toFloat())
        } catch (error: Throwable) {
            inject(MotionEvent.ACTION_CANCEL, points.last().x.toFloat(), points.last().y.toFloat())
            throw error
        }
    }

    /** Mostly linear strokes with rounded turns, sampled more densely around each corner. */
    private fun roundedStroke(
        vertices: List<Point>,
        radius: Double,
    ): List<Point> {
        val path = mutableListOf(vertices.first())

        fun lineTo(end: Point) {
            val start = path.last()
            val steps = (hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()) / 8).roundToInt().coerceAtLeast(1)
            for (step in 1..steps) {
                val t = step.toDouble() / steps
                path += Point((start.x + (end.x - start.x) * t).roundToInt(), (start.y + (end.y - start.y) * t).roundToInt())
            }
        }

        fun trimmed(
            corner: Point,
            neighbour: Point,
        ): Point {
            val dx = (neighbour.x - corner.x).toDouble()
            val dy = (neighbour.y - corner.y).toDouble()
            val fraction = (radius / hypot(dx, dy)).coerceAtMost(0.45)
            return Point((corner.x + dx * fraction).roundToInt(), (corner.y + dy * fraction).roundToInt())
        }

        for (index in 1 until vertices.lastIndex) {
            val corner = vertices[index]
            val start = trimmed(corner, vertices[index - 1])
            val end = trimmed(corner, vertices[index + 1])
            lineTo(start)
            for (step in 1..12) {
                val t = step / 12.0
                val u = 1 - t
                path +=
                    Point(
                        (u * u * start.x + 2 * u * t * corner.x + t * t * end.x).roundToInt(),
                        (u * u * start.y + 2 * u * t * corner.y + t * t * end.y).roundToInt(),
                    )
            }
        }
        lineTo(vertices.last())
        return path
    }

    private fun richField(index: Int) =
        "document.querySelector('.field-container[data-index=\"$index\"] .rich-text-editable')?.shadowRoot?.querySelector('anki-editable')"

    private fun fieldText(index: Int): String = JSONTokener(web().evaluate("${richField(index)}?.textContent")).nextValue() as? String ?: ""

    private fun state(): EditorState =
        onMain {
            checkNotNull(ViewModelProvider(resumed() as ComposeNoteEditorActivity)[EditorViewModel::class.java].state.value)
        }

    private fun web(): WebEditorView =
        onMain {
            fun find(view: View): WebEditorView? =
                when (view) {
                    is WebEditorView -> view
                    is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
                    else -> null
                }
            checkNotNull(find((resumed() as ComposeNoteEditorActivity).window.decorView))
        }

    private fun resumed(): Activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()

    private fun <T> onMain(block: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }

    private fun s(
        id: Int,
        vararg arguments: Any,
    ) = testContext.getString(id, *arguments)

    private fun visible(selector: BySelector): UiObject2 =
        device.wait(Until.findObject(selector), 8_000) ?: run {
            val directory = File(testContext.getExternalFilesDir(null), "editor-demo").apply { mkdirs() }
            device.takeScreenshot(File(directory, "missing-control.png"))
            device.dumpWindowHierarchy(File(directory, "missing-control.xml"))
            File(directory, "input-method.txt").writeText(device.executeShellCommand("dumpsys input_method"))
            error("Missing $selector")
        }

    private fun click(selector: BySelector) {
        val point = visible(selector).visibleCenter
        tap(point)
        hold(80)
    }

    private fun tap(point: Point) = gesture(listOf(point, point), 40)

    private fun hold(milliseconds: Long) = SystemClock.sleep(milliseconds)

    private fun await(
        message: String,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { message }
            hold(50)
        }
    }

    companion object {
        private const val GBOARD = "com.google.android.inputmethod.latin"
    }
}
