// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.withManageExternalStorageInManifest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Context and manifest integration for storage permission selection. */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class, sdk = [Build.VERSION_CODES.R])
@Category(EmptyApplicationCategory::class)
class SelectStoragePermissionsAndroidTest {
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R, guaranteed by @Config
    @Test // #13574: no collection path is set: permissions are based on device capabilities
    fun `full build - screen is required while no collection path is set`() {
        context.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        withManageExternalStorageInManifest {
            assertThat(selectStoragePermissions(context), equalTo(StoragePermissionSet.EXTERNAL_MANAGER))
        }
    }

    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R, guaranteed by @Config
    @Test // #13574: public storage which the app cannot access: the screen is required
    fun `full build - screen is required when access to public storage was revoked`() {
        context.sharedPrefs().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, "/storage/emulated/0/AnkiDroid")
        }
        withManageExternalStorageInManifest {
            assertThat(selectStoragePermissions(context), equalTo(StoragePermissionSet.EXTERNAL_MANAGER))
        }
    }

    @Test // #13574: app-private storage can be accessed without storage permissions
    fun `app-private collection path requires no storage permissions`() {
        context.sharedPrefs().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, File(context.filesDir, "AnkiDroid").path)
        }
        withManageExternalStorageInManifest {
            assertThat(selectStoragePermissions(context), equalTo(StoragePermissionSet.APP_PRIVATE))
        }
    }

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
}
