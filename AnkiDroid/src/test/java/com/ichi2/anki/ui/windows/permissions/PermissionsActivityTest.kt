/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.permissions

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.edit
import androidx.fragment.app.commitNow
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.ActivityAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.OptionalPermissionSet
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.HamcrestUtils.containsInAnyOrder
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.withAllFilesAccess
import com.ichi2.testutils.withManageExternalStorageInManifest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class PermissionsActivityTest : RobolectricTest() {
    @Test
    fun testActivityCantBeClosedByBackButton() {
        testActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
            assertThat("activity is not finishing", !activity.isFinishing)
        }
    }

    @Test
    fun testOnClickingContinueActivityFinishes() {
        testActivity { activity ->
            activity.setContinueButtonEnabled(true)
            activity.findViewById<AppCompatButton>(R.id.continue_button).performClick()
            assertThat("activity is finishing", activity.isFinishing)
        }
    }

    @Test // #13574: completing the screen must record the storage decision
    @Config(sdk = [Build.VERSION_CODES.R])
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R, guaranteed by @Config
    fun `continuing decides the collection path`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        withManageExternalStorageInManifest {
            withAllFilesAccess {
                testActivity(StoragePermissionSet.EXTERNAL_MANAGER) { activity ->
                    activity.setContinueButtonEnabled(true)
                    activity.findViewById<AppCompatButton>(R.id.continue_button).performClick()
                    assertThat("activity is finishing", activity.isFinishing)
                }
            }
        }
        assertEquals(publicCollectionPath, collectionPath)
    }

    @Test // #13574: PermissionsStartingAt30Fragment completes the screen once 'All files access' is granted
    @Config(sdk = [Build.VERSION_CODES.R])
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R, guaranteed by @Config
    fun `completing without Continue decides the collection path`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        withManageExternalStorageInManifest {
            withAllFilesAccess {
                testActivity(StoragePermissionSet.EXTERNAL_MANAGER) { activity ->
                    activity.supportFragmentManager.setFragmentResult(PermissionsActivity.RESULT_COMPLETE, Bundle())
                    assertThat("activity is finishing", activity.isFinishing)
                }
            }
        }
        assertEquals(publicCollectionPath, collectionPath)
    }

    private val collectionPath: String?
        get() = targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null)

    @Test
    fun `error toast is shown if EXTRA_PERMISSIONS_SET is missing`() {
        testInvalidActivityFinishes()
        assertThat(
            ShadowToast.getTextOfLatestToast(),
            equalTo(getResourceString(R.string.something_wrong)),
        )
    }

    @Test
    fun `Each screen starts normally and has the same permissions of a PermissionSet`() {
        testActivity { activity ->
            val permissionSets: List<PermissionSet> = StoragePermissionSet.entries + OptionalPermissionSet.entries
            for (permissionSet in permissionSets) {
                val fragment = permissionSet.permissionsFragment.getDeclaredConstructor().newInstance()
                activity.supportFragmentManager.commitNow {
                    replace(R.id.fragment_container, fragment)
                }
                val allPermissions = fragment.permissionsItems.flatMap { it.permissions }

                assertThat(permissionSet.permissions, containsInAnyOrder(allPermissions))
            }
        }
    }

    private fun testInvalidActivityFinishes() {
        val ex = assertFailsWith<NullPointerException> { testActivity(permissionSet = null) { } }
        assertEquals("Cannot run onActivity since Activity has been destroyed already", ex.message)
    }

    private fun testActivity(
        permissionSet: StoragePermissionSet? = ARBITRARY_PERMISSION_SET,
        action: ActivityAction<PermissionsActivity>,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            if (permissionSet != null) {
                PermissionsActivity.getIntent(context, permissionSet)
            } else {
                Intent(context, PermissionsActivity::class.java)
            }
        ActivityScenario.launch<PermissionsActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                action.perform(activity)
            }
        }
    }

    companion object {
        val ARBITRARY_PERMISSION_SET = StoragePermissionSet.entries.first()
    }
}
