// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.graphics.Point
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.R
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercise native touch dispatch, recycling and edge scrolling, rather than calling the move callback. */
class EditorToolbarDragTest : InstrumentedTest() {
    @Test
    fun handlesMoveCustomAndAnkiButtonsAndScrollAtBothEdges() {
        CollectionManager.setColForTests(emptyCol)
        val custom = EditorCustomToolbarButton(id = "wrap", label = "Wrap", prefix = "<b>", suffix = "</b>")
        val initial = EditorFieldToolbar(hiddenButtons = setOf("ITALIC"), customButtons = listOf(custom))
        val saved = AtomicReference<EditorFieldToolbar>()
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
                                noteTypeName = "Basic",
                                isCloze = false,
                                templates = listOf(custom),
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
                assertEquals("BOLD", save().buttonOrder.last())
                dragBoldToEdge(bottom = false)
                val result = save()
                assertEquals("BOLD", result.buttonOrder.first())
                assertEquals(initial.hiddenButtons, result.hiddenButtons)
                assertEquals(initial.customButtons, result.customButtons)
                assertEquals(initial.orderedItems(listOf(custom)).map { it.key }.toSet(), result.buttonOrder.toSet())
                assertEquals(result.buttonOrder.size, result.buttonOrder.distinct().size)
            }
    }
}
