// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.Manifest.permission.RECORD_AUDIO
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import com.ichi2.anki.multimedia.AudioRecordingFragment
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.multimedia.MultimediaActivityExtra
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.testutils.ActivityList
import com.ichi2.testutils.ActivityList.ActivityLaunchParam
import com.ichi2.testutils.grantPermissions
import com.ichi2.widget.deckpicker.DeckPickerWidgetConfig
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises the real activities and their ViewModels, including ViewModels in hosted fragments. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class ActivitySavedStateTest(
    private val activityName: String,
    private val launcher: ActivityLaunchParam,
) : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    override fun tearDown() {
        try {
            super.tearDown()
        } finally {
            ArchTaskExecutor.getInstance().setDelegate(null)
        }
    }

    @Test
    fun `ViewModels do not persist unrelated launch arguments`() {
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        val intent = launchIntent()
        val payload = ByteArray(300_000)
        intent.putExtra(UNRELATED_EXTRA, payload)
        if (SingleFragmentActivity::class.java.isAssignableFrom(launcher.activity)) {
            intent.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS)?.putByteArray(UNRELATED_EXTRA, payload)
        }

        val controller = startActivityControllerNormallyOpenCollectionWithIntent(launcher.activity, intent)
        assertFalse(controller.get().isFinishing, "$activityName must reach its normal screen")
        // New ViewModels must be safe without opting into a custom factory.
        ViewModelProvider(controller.get() as ComponentActivity)[DefaultViewModel::class.java]
        val savedState = Bundle()
        controller.pause().stop().saveInstanceState(savedState)

        val parcel = Parcel.obtain()
        val restoredState =
            try {
                parcel.writeBundle(savedState)
                parcel.setDataPosition(0)
                requireNotNull(parcel.readBundle(javaClass.classLoader))
            } finally {
                parcel.recycle()
            }

        // Check the serialized handles, allowing FragmentManager to retain the original arguments.
        assertTrue(restoredState.pathsToKey(SAVED_STATE_HANDLES).isNotEmpty(), "No SavedStateHandle provider found for $activityName")
        val copies = restoredState.pathsToKey(UNRELATED_EXTRA).filter { SAVED_STATE_HANDLES in it }
        assertThat("$activityName persisted an unrelated argument in these ViewModels", copies, empty())
    }

    private fun launchIntent(): Intent {
        val note = addBasicNote()
        return when (launcher.activity) {
            NoteTypeFieldEditor::class.java ->
                Intent().apply {
                    putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_ID, note.noteTypeId)
                    putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_NAME, "Basic")
                }
            CardTemplateEditor::class.java -> Intent().putExtra(CardTemplateEditor.EDITOR_NOTE_TYPE_ID, note.noteTypeId)
            NoteEditorActivity::class.java -> Intent().putExtras(NoteEditorFragment.addNoteArgs())
            SingleFragmentActivity::class.java, ConfigAwareSingleFragmentActivity::class.java -> DrawingFragment.getIntent(targetContext)
            CardViewerActivity::class.java ->
                PreviewerFragment.getIntent(
                    targetContext,
                    idsFile = IdsFile(tempFolder.root, note.cardIds(col)),
                    currentIndex = 0,
                )
            MultimediaActivity::class.java -> {
                grantPermissions(RECORD_AUDIO)
                val field = AudioRecordingField()
                val multimediaNote =
                    MultimediaEditableNote().apply {
                        setNumFields(1)
                        setField(0, field)
                        freezeInitialFieldValues()
                    }
                AudioRecordingFragment.getIntent(targetContext, MultimediaActivityExtra(index = 0, field = field, note = multimediaNote))
            }
            InstantNoteEditorActivity::class.java -> {
                // With a synchronous collection, deliver LiveData before the editor opens its dialog.
                ArchTaskExecutor.getInstance().setDelegate(
                    object : TaskExecutor() {
                        override fun executeOnDiskIO(runnable: Runnable) = runnable.run()

                        override fun postToMainThread(runnable: Runnable) = runnable.run()

                        override fun isMainThread() = true
                    },
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "A note")
                }
            }
            PermissionsActivity::class.java -> PermissionsActivity.getIntent(targetContext, StoragePermissionSet.entries.first())
            PreferencesActivity::class.java -> PreferencesActivity.getIntent(targetContext)
            DeckPickerWidgetConfig::class.java -> Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1)
            AccountActivity::class.java -> AccountActivity.getIntent(targetContext)
            else -> launcher.buildIntent(targetContext)
        }
    }

    @Suppress("DEPRECATION") // Bundle.get is needed to inspect nested values of different types.
    private fun Bundle.pathsToKey(
        target: String,
        path: String = "savedState",
    ): List<String> =
        keySet().flatMap { key ->
            val childPath = "$path/$key"
            val matches = if (key == target) listOf(childPath) else emptyList()
            matches + ((get(key) as? Bundle)?.pathsToKey(target, childPath) ?: emptyList())
        }

    class DefaultViewModel(
        val state: SavedStateHandle,
    ) : ViewModel()

    companion object {
        private const val UNRELATED_EXTRA = "saved_state_test_unrelated_extra"
        private const val SAVED_STATE_HANDLES = "androidx.lifecycle.internal.SavedStateHandlesProvider"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun activities(): Collection<Array<Any>> =
            ActivityList
                .allActivitiesAndIntents()
                // Plain Activities do not own ViewModels or SavedStateHandles.
                .filter { ComponentActivity::class.java.isAssignableFrom(it.activity) }
                .map { arrayOf(it.simpleName, it) }
    }
}
