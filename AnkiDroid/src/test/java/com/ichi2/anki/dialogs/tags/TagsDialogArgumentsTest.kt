// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.tags

import android.app.Application
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.common.android.ApplicationContextInitializer
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.testutils.EmptyApplication
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class TagsDialogArgumentsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `selection snapshots use the supplied context cache directory`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val profileCache = temporaryFolder.newFolder("profile-cache")
        val profileContext =
            object : ContextWrapper(application) {
                override fun getCacheDir(): File = profileCache
            }
        val previous = ApplicationContextInitializer.instanceOrNull as Application?
        var snapshot: IdsFile? = null
        try {
            ApplicationContextInitializer.setInstance(application)
            val dialog = TagsDialog().withArguments(profileContext, TagsDialog.DialogType.EDIT_TAGS, listOf(1L, 2L))
            val created = dialog.requireArguments().requireParcelable<IdsFile>(TagsDialog.ARG_TAGS_FILE)
            snapshot = created

            assertEquals(File(profileCache, "idsfiles").canonicalFile, created.parentFile!!.canonicalFile)
            assertEquals(listOf(1L, 2L), created.getIdsOrNull())
        } finally {
            snapshot?.delete()
            if (previous == null) ApplicationContextInitializer.clearForTesting() else ApplicationContextInitializer.setInstance(previous)
        }
    }
}
