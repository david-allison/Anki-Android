// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.ColumnType
import com.ichi2.anki.browser.JvmCardBrowserViewModelTestBase
import com.ichi2.anki.browser.search.SortOrderBottomSheetFragment.ColumnUiModel
import com.ichi2.testutils.EmptyApplication
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.jupiter.api.assertInstanceOf
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Tests [SortOrderBottomSheetFragment.ColumnUiModel] */
class SortColumnUiModelTest : JvmCardBrowserViewModelTestBase() {
    @Test
    fun `initial item is available without ordering`() =
        runViewModelTest {
            val initialItem = ColumnUiModel.buildList(this).first()
            val item = assertInstanceOf<ColumnUiModel.NoOrdering>(initialItem)
            assertEquals(true, item.available)
        }

    @Test
    fun `ordering is correct`() =
        runViewModelTest {
            updateActiveColumns(
                CardBrowserColumn.ORIGINAL_POSITION,
                CardBrowserColumn.QUESTION,
                CardBrowserColumn.LAPSES,
            )

            val list = ColumnUiModel.buildList(this)

            val first = listOf(null)
            val selected = listOf("originalPosition", "cardLapses")
            val excluded = listOf("question", "answer")
            // all known columns, in our expected order
            // CardBrowserColumn defines this custom order
            val all =
                listOf(
                    "question",
                    "answer",
                    "noteFld",
                    "deck",
                    "noteTags",
                    "template",
                    "cardDue",
                    "cardEase",
                    "cardMod",
                    "noteCrt",
                    "noteMod",
                    "cardIvl",
                    "cardLapses",
                    "note",
                    "cardReps",
                    "difficulty",
                    "retrievability",
                    "stability",
                    "originalPosition",
                )

            val expected =
                first +
                    selected +
                    all.filter { !selected.contains(it) && !excluded.contains(it) } +
                    excluded

            val actual =
                list
                    .filterIsInstance<ColumnUiModel>()
                    .map { it.keyOrNull }
            assertEquals(expected, actual)
        }

    @Test
    fun `properties of unselected column`() =
        runViewModelTest {
            val column =
                ColumnUiModel
                    .buildList(this)
                    .filterIsInstance<ColumnUiModel.AnkiColumn>()
                    .single { it.key.value == "cardEase" }

            val item = assertInstanceOf<ColumnUiModel.AnkiColumn>(column)
            assertEquals("cardEase", item.key.value)
            assertEquals("Ease", item.label)
            assertEquals(null, item.tooltipValue)
            assertEquals(true, item.canBeSorted)
            assertEquals(false, item.isShownInUI)
            assertEquals(ColumnType.NUMERIC, item.type)
        }

    @Test
    fun `column type is assigned by key`() =
        runViewModelTest {
            val typeByKey =
                ColumnUiModel
                    .buildList(this)
                    .filterIsInstance<ColumnUiModel.AnkiColumn>()
                    .associate { it.key.value to it.type }

            assertEquals(ColumnType.TEXT, typeByKey["noteFld"])
            assertEquals(ColumnType.TEXT, typeByKey["deck"])
            assertEquals(ColumnType.NUMERIC, typeByKey["cardEase"])
            assertEquals(ColumnType.NUMERIC, typeByKey["originalPosition"])
            assertEquals(ColumnType.DATE, typeByKey["noteCrt"])
            assertEquals(ColumnType.DATE, typeByKey["cardMod"])
            assertEquals(ColumnType.UNSPECIFIED, typeByKey["cardDue"])
        }

    @Test
    fun `properties of selected column`() =
        runViewModelTest {
            updateActiveColumns(CardBrowserColumn.EASE)

            val column =
                ColumnUiModel
                    .buildList(this)
                    .filterIsInstance<ColumnUiModel.AnkiColumn>()
                    .single { it.key.value == "cardEase" }

            val item = assertInstanceOf<ColumnUiModel.AnkiColumn>(column)
            assertEquals("cardEase", item.key.value)
            assertEquals("Ease", item.label)
            assertEquals(null, item.tooltipValue)
            assertEquals(true, item.canBeSorted)
            assertEquals(true, item.isShownInUI)
        }

    @Test
    fun `properties of unusable column`() =
        runViewModelTest {
            val column =
                ColumnUiModel
                    .buildList(this)
                    .filterIsInstance<ColumnUiModel.AnkiColumn>()
                    .single { it.key.value == "question" }

            val item = assertInstanceOf<ColumnUiModel.AnkiColumn>(column)
            assertEquals("question", item.key.value)
            assertEquals("Question", item.label)
            assertEquals(null, item.tooltipValue)
            assertEquals(false, item.canBeSorted)
            assertEquals(false, item.isShownInUI)
        }

    @Test
    fun `properties of unusable column in UI`() =
        runViewModelTest {
            updateActiveColumns(CardBrowserColumn.QUESTION)

            val column =
                ColumnUiModel
                    .buildList(this)
                    .filterIsInstance<ColumnUiModel.AnkiColumn>()
                    .single { it.key.value == "question" }

            val item = assertInstanceOf<ColumnUiModel.AnkiColumn>(column)
            assertEquals("question", item.key.value)
            assertEquals("Question", item.label)
            assertEquals(null, item.tooltipValue)
            assertEquals(false, item.canBeSorted)
            assertEquals(true, item.isShownInUI)
        }

    fun CardBrowserViewModel.updateActiveColumns(vararg columns: CardBrowserColumn) {
        assertTrue(updateActiveColumns(columns.toList(), this.cardsOrNotes))
    }
}

val ColumnUiModel.keyOrNull get() =
    when (this) {
        is ColumnUiModel.NoOrdering -> null
        is ColumnUiModel.AnkiColumn -> this.key.value
    }

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class SortColumnUiModelAndroidTest {
    @Test
    fun `no ordering has a translated label`() {
        assertEquals("No sorting", ColumnUiModel.NoOrdering.getLabel(ApplicationProvider.getApplicationContext()))
    }
}
