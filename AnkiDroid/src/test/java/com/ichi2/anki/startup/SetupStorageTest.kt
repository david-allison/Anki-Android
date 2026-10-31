// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.withAllFilesAccess
import com.ichi2.testutils.withManageExternalStorageInManifest
import com.ichi2.testutils.withWritePermissions
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SetupStorageTest {
    private val targetContext get() = getApplicationContext<Application>()
    private val prefs
        get() = targetContext.sharedPrefs()

    private val collectionPath: String?
        get() = prefs.getString(CollectionHelper.PREF_COLLECTION_PATH, null)

    @Before
    fun grantNetworkPermission() {
        grantPermissions(Manifest.permission.INTERNET)
    }

    @After
    fun resetCollectionHelperState() {
        CollectionHelper.ankiDroidDirectoryOverride = null
        CollectionHelper.systemStorageFailure = null
    }

    @Test
    fun `maybeSetDefaultCollectionPath persists a default when unset and storage is accessible`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withAllFilesAccess { maybeSetDefaultCollectionPath(targetContext) }

        assertTrue(!collectionPath.isNullOrEmpty(), "a default collection path should be set")
    }

    @Test
    fun `maybeSetDefaultCollectionPath does not overwrite an existing value`() {
        prefs.edit { putString(CollectionHelper.PREF_COLLECTION_PATH, "/a/custom/path") }

        maybeSetDefaultCollectionPath(targetContext)

        assertEquals("/a/custom/path", collectionPath)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `Android 10 - public storage is persisted before permissions are granted`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        maybeSetDefaultCollectionPath(targetContext)

        assertEquals(publicCollectionPath, collectionPath)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `Android 10 - public storage is persisted once permissions are granted`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withWritePermissions { maybeSetDefaultCollectionPath(targetContext) }

        assertEquals(publicCollectionPath, collectionPath)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `full build - storage decision is deferred before 'All files access' is granted`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withManageExternalStorageInManifest { maybeSetDefaultCollectionPath(targetContext) }

        assertNull(collectionPath, "the storage decision should be deferred to the user")
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `full build - public storage is persisted once 'All files access' is granted`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withManageExternalStorageInManifest {
            withAllFilesAccess { maybeSetDefaultCollectionPath(targetContext) }
        }

        assertEquals(publicCollectionPath, collectionPath)
    }
}
