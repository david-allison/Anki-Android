// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.utils

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.OptionalPermissionSet
import com.ichi2.anki.R
import com.ichi2.anki.common.permissions.LEGACY_POST_NOTIFICATIONS
import com.ichi2.anki.common.permissions.POST_NOTIFICATIONS
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.ui.windows.permissions.PermissionsBottomSheet
import com.ichi2.testutils.EmptyApplication
import com.ichi2.utils.Permissions.attemptToEnableNotifications
import com.ichi2.utils.Permissions.openAppSettingsScreen
import com.ichi2.utils.Permissions.openAppSettingsScreenForPermission
import io.mockk.justRun
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class PermissionsTest {
    private lateinit var fragmentActivity: FragmentActivity
    private lateinit var context: Context
    private lateinit var fragment: Fragment
    private lateinit var fragmentManager: FragmentManager
    private lateinit var permissionRequestLauncher: ActivityResultLauncher<String>
    private lateinit var controller: ActivityController<FragmentActivity>
    private var bottomSheetMocked = false
    private val permissionRequests = mutableListOf<String>()
    private val prefs get() = PrefsRepository(context)

    @Before
    fun setUp() {
        context = getApplicationContext()
        controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        fragmentActivity = controller.get()
        fragmentManager = fragmentActivity.supportFragmentManager
        fragment = Fragment()
        fragmentManager.beginTransaction().add(fragment, "permissions").commitNow()
        permissionRequestLauncher =
            object : ActivityResultLauncher<String>() {
                override fun launch(
                    input: String,
                    options: ActivityOptionsCompat?,
                ) {
                    permissionRequests.add(input)
                }

                override fun unregister() = Unit

                override val contract: ActivityResultContract<String, Boolean> = ActivityResultContracts.RequestPermission()
            }

        prefs.notificationsPermissionRequested = false
        prefs.notificationsBottomSheetShownBelowAPI33 = false
    }

    @After
    fun tearDown() {
        if (bottomSheetMocked) unmockkObject(PermissionsBottomSheet)
        prefs.notificationsPermissionRequested = false
        prefs.notificationsBottomSheetShownBelowAPI33 = false
        controller.close()
    }

    @Test
    fun `openAppSettingsScreen is a no-op if no app can show the app settings screen`() {
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val realActivity = controller.setup().get()
            // throw when resolving `ACTION_APPLICATION_DETAILS_SETTINGS`
            shadowOf(realActivity.application).checkActivities(true)
            assertDoesNotThrow { realActivity.openAppSettingsScreen() }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun `attemptToEnableNotifications for API at 33+ requests the notifications permission`() {
        prefs.notificationsPermissionRequested = false
        fragment.attemptToEnableNotifications(permissionRequestLauncher)
        verifyPermissionWasRequested(permission = Permissions.notificationsPermission)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun `attemptToEnableNotifications for API at 33+ opens notif settings screen if we can't request the permission`() {
        prefs.notificationsPermissionRequested = true // not first call
        setCanPermissionBeRequested(false)
        fragment.attemptToEnableNotifications(permissionRequestLauncher)
        verifyOSSettingsWasOpened(openedActivityAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU - 1])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun `attemptToEnableNotifications for API before 33 opens notif settings screen without requesting a permission`() {
        fragment.attemptToEnableNotifications(permissionRequestLauncher)
        // The notifications permission does not exist below API 33, so it should never be requested
        assertThat(permissionRequests, equalTo(emptyList()))
        // The OS request dialog should not have been opened
        assertThat(prefs.notificationsPermissionRequested, equalTo(false))
        verifyOSSettingsWasOpened(openedActivityAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun `runtime notification prompt launches the notifications bottom sheet`() {
        mockBottomSheet()

        setPermissionsGranted(false)
        setCanPermissionBeRequested(true)
        showBottomSheetShouldSucceed()
        verify(exactly = 1) { PermissionsBottomSheet.launch(fragmentManager, OptionalPermissionSet.NOTIFICATIONS) }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU - 1])
    fun `legacy notification prompt launches settings guidance and records that it was shown`() {
        mockBottomSheet()

        setPermissionsGranted(false)
        setCanPermissionBeRequested(true)
        showBottomSheetShouldSucceed()
        assertThat(prefs.notificationsBottomSheetShownBelowAPI33, equalTo(true))
        verify(exactly = 1) { PermissionsBottomSheet.launch(fragmentManager, OptionalPermissionSet.LEGACY_NOTIFICATIONS) }
    }

    @Test
    fun `openAppSettingsScreenForPermission opens the generic app settings screen for a null permission`() {
        fragment.openAppSettingsScreenForPermission(null)
        verifyOSSettingsWasOpened()
    }

    @Test
    fun `notification permission identifiers match Android and the legacy resource`() {
        assertThat(POST_NOTIFICATIONS, equalTo(Manifest.permission.POST_NOTIFICATIONS))
        assertThat(
            context.getString(R.string.legacy_post_notification_permission),
            equalTo(LEGACY_POST_NOTIFICATIONS),
        )
    }

    private fun mockBottomSheet() {
        bottomSheetMocked = true
        mockkObject(PermissionsBottomSheet)
        justRun { PermissionsBottomSheet.launch(any(), any()) }
    }

    private fun setPermissionsGranted(granted: Boolean) {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(granted)
    }

    private fun setCanPermissionBeRequested(canBeRequested: Boolean) {
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Permissions.notificationsPermission, canBeRequested)
    }

    private fun verifyPermissionWasRequested(permission: String = DUMMY_PERMISSION_STRING) {
        assertThat(permissionRequests, equalTo(listOf(permission)))
        // No settings screen should be opened if the permission could be requested directly.
        assertNull(shadowOf(fragmentActivity).nextStartedActivity)
    }

    private fun verifyOSSettingsWasOpened(openedActivityAction: String = Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
        assertThat(permissionRequests, equalTo(emptyList()))
        val intent = requireNotNull(shadowOf(fragmentActivity).nextStartedActivity)
        assertThat(intent.action, equalTo(openedActivityAction))
        assertNull(shadowOf(fragmentActivity).nextStartedActivity, "only one settings activity should be opened")
    }

    private fun showBottomSheetShouldSucceed() {
        Permissions.showNotificationsPermissionBottomSheetIfNeeded(fragmentActivity, fragmentManager) {
            prefs.notificationsPermissionRequested = true
        }
    }

    companion object {
        private const val DUMMY_PERMISSION_STRING = "DUMMY_PERMISSION_STRING"
    }
}
