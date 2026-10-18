// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.graphics.Point
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.R
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercise native touch dispatch, recycling and edge scrolling, rather than calling the move callback. */
class EditorToolbarDragTest : InstrumentedTest() {
    @Test
    fun handlesMoveCustomAndAnkiButtonsAndScrollAtBothEdges() {
        CollectionManager.setColForTests(emptyCol)
        val custom = EditorCustomToolbarButton(id = "wrap", label = "Wrap", prefix = "<b>", suffix = "</b>")
        // Italic is the first drag target; leave Redo in the separate Available group.
        val initial = EditorFieldToolbar(hiddenButtons = setOf("REDO"), customButtons = listOf(custom))
        val saved = AtomicReference<EditorFieldToolbar>()
        val libraryOpened = AtomicInteger()
        ActivityScenario
            .launch<ComposeNoteEditorActivity>(
                Intent(testContext, ComposeNoteEditorActivity::class.java).putExtras(NoteEditorFragment.addNoteArgs()),
            ).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        AnkiDroidTheme {
                            EditorToolbarDialog(
                                target = EditorFieldTarget("drag-test", 0, 1),
                                field = EditorFieldState("Front", "", "Arial", 20, false, null, false, false, false, initial),
                                isCloze = false,
                                templates = listOf(custom),
                                onCustomButtons = { libraryOpened.incrementAndGet() },
                                onDismiss = {},
                                onSave = { saved.set(it) },
                            )
                        }
                    }
                }
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

                fun handle(label: String) =
                    checkNotNull(
                        device.wait(Until.findObject(By.desc(testContext.getString(R.string.compose_editor_reorder_button, label))), 5_000),
                    )

                fun save(): EditorFieldToolbar {
                    device.findObject(By.desc(testContext.getString(CommonString.save))).click()
                    return checkNotNull(saved.get())
                }
                val start = handle("Wrap").visibleCenter
                val library = By.desc(testContext.getString(R.string.compose_editor_custom_buttons))
                assertTrue(device.hasObject(library))
                InstrumentationRegistry.getArguments().getString("artifactDir")?.let { name ->
                    val directory = File(testContext.filesDir, name)
                    check(directory.isDirectory || directory.mkdirs())
                    device.takeScreenshot(File(directory, "toolbar-expanded.png"))
                    device.dumpWindowHierarchy(File(directory, "toolbar-expanded.xml"))
                }
                val expandedBounds = checkNotNull(device.findObject(library)).visibleBounds
                val expandedWidth = expandedBounds.width()
                assertTrue(expandedWidth > expandedBounds.height(), "The action must show its label at the top: $expandedBounds")
                val downTime = SystemClock.uptimeMillis()

                fun touch(action: Int) {
                    val event =
                        MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, start.x.toFloat(), start.y.toFloat(), 0).apply {
                            source = InputDevice.SOURCE_TOUCHSCREEN
                        }
                    try {
                        assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true))
                    } finally {
                        event.recycle()
                    }
                }
                try {
                    touch(MotionEvent.ACTION_DOWN)
                    assertTrue(device.wait(Until.gone(library), 5_000), "The library action must leave the drag area")
                } finally {
                    touch(MotionEvent.ACTION_CANCEL)
                }
                assertTrue(device.wait(Until.hasObject(library), 5_000), "The library action must return after dragging")
                val italic = handle(testContext.getString(R.string.compose_editor_italic)).visibleBounds
                assertTrue(device.swipe(start.x, start.y, start.x, italic.centerY() + italic.height() / 4, 40))
                assertEquals(listOf("BOLD", "ITALIC", "custom:wrap"), save().buttonOrder.take(3))

                fun dragBoldToEdge(bottom: Boolean) {
                    val bold = handle(testContext.getString(R.string.compose_editor_bold)).visibleCenter
                    val viewport = device.findObject(By.clazz("androidx.recyclerview.widget.RecyclerView")).visibleBounds
                    val edge = if (bottom) viewport.bottom - 2 else viewport.top + 2
                    // A continuous gesture holds at the edge long enough to recycle every intervening row.
                    val points =
                        (0..20).map { step -> Point(bold.x, bold.y + (edge - bold.y) * step / 20) } +
                            List(70) { Point(bold.x, edge) }
                    assertTrue(device.swipe(points.toTypedArray(), 10))
                }
                dragBoldToEdge(bottom = true)
                val available = save()
                assertEquals("BOLD", available.buttonOrder.last())
                assertTrue("BOLD" in available.hiddenButtons, "Dragging below Available must disable the button")
                // At the end, the last row and every handle above it must be clear of the action.
                val list = device.findObject(By.clazz("androidx.recyclerview.widget.RecyclerView"))
                list.scroll(Direction.DOWN, 1f)
                val action = checkNotNull(device.findObject(library))
                val actionBounds = action.visibleBounds
                assertTrue(actionBounds.width() < expandedWidth, "Scrolling must collapse the library action")
                action.click()
                assertEquals(1, libraryOpened.get(), "The collapsed icon must still open the library")
                val lastHandle = handle(testContext.getString(EditorToolbarButton.BOLD.label)).visibleBounds
                assertTrue(lastHandle.bottom < actionBounds.top, "The last handle must remain above the floating action")
                dragBoldToEdge(bottom = false)
                val result = save()
                assertEquals("BOLD", result.buttonOrder.first())
                device.waitForIdle()
                assertEquals(
                    expandedWidth,
                    checkNotNull(device.findObject(library)).visibleBounds.width(),
                    "The label must return at the top",
                )
                assertEquals(initial.hiddenButtons, result.hiddenButtons)
                assertEquals(initial.customButtons, result.customButtons)
                assertEquals(initial.orderedItems(listOf(custom)).map { it.key }.toSet(), result.buttonOrder.toSet())
                assertEquals(result.buttonOrder.size, result.buttonOrder.distinct().size)
            }
    }

    @Test
    fun quickActionsMoveBetweenGroupsIncludingEmptyDestinations() {
        CollectionManager.setColForTests(emptyCol)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pin = testContext.getString(R.string.compose_editor_pin_field)
        for (initialActions in listOf(emptyList(), EditorQuickAction.entries.map { it.id })) {
            val initial = EditorFieldToolbar(quickActions = initialActions)
            val saved = AtomicReference(initial)
            ActivityScenario
                .launch<ComposeNoteEditorActivity>(
                    Intent(testContext, ComposeNoteEditorActivity::class.java).putExtras(NoteEditorFragment.addNoteArgs()),
                ).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.setContent {
                            AnkiDroidTheme {
                                EditorQuickActionsDialog(
                                    target = EditorFieldTarget("quick-drag-test", 0, 0),
                                    field = EditorFieldState("Front", "", "Arial", 20, false, null, false, false, false, initial),
                                    onDismiss = {},
                                    onChange = { saved.set(it) },
                                )
                            }
                        }
                    }

                    fun dragToSection(label: Int) {
                        val handle =
                            checkNotNull(
                                device.wait(
                                    Until.findObject(By.desc(testContext.getString(R.string.compose_editor_reorder_button, pin))),
                                    5_000,
                                ),
                            )
                        val heading = checkNotNull(device.wait(Until.findObject(By.text(testContext.getString(label))), 5_000))
                        val from = handle.visibleCenter
                        val to = heading.visibleCenter
                        assertTrue(device.swipe(from.x, from.y, from.x, to.y, 60))
                        device.waitForIdle()
                    }
                    val startsActive = "pin" in initialActions
                    dragToSection(if (startsActive) CommonString.user_potential_columns else CommonString.user_active_columns)
                    assertEquals(!startsActive, "pin" in saved.get().quickActions)
                    assertEquals(initialActions.filterNot { it == "pin" }, saved.get().quickActions.filterNot { it == "pin" })
                    dragToSection(if (startsActive) CommonString.user_active_columns else CommonString.user_potential_columns)
                    assertEquals(initialActions, saved.get().quickActions)
                    assertFalse(device.hasObject(By.desc(testContext.getString(CommonString.save))))
                }
        }
    }
}
